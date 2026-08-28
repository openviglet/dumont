/*
 * D53 â€” the behaviour that has to survive the refactor.
 *
 * The monitoring item's History tab is the last of the `set-state-in-effect` debt, and it is the
 * one place where the fix is not a local edit: the result has to be keyed by the query that
 * produced it, and that query interacts with a rule nothing else in the console has â€” when the tab
 * opens, the reader is sent to the **last** page, because entries are sorted oldest-first and the
 * interesting ones are the most recent.
 *
 * That rule lives in a `useRef` consulted inside a `.then`, re-entering the same effect with a new
 * page. It has no test. Restructuring it to satisfy a lint rule, with nothing watching, is how a
 * paging flow quietly starts landing on page one â€” so this goes in first and the refactor happens
 * behind it.
 *
 * The stub reads `params`, which the shared harness deliberately does not: the whole subject here
 * is which page was asked for.
 */
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import axios from "axios"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"

import IntegrationInstanceMonitoringItemPage from "@/pages/integration.instance.monitoring.item.page"

const STANDALONE = "local"
const ITEM_ID = "42"
const CONTENT_ID = "/content/wknd/en/home"

/** Every history request the page made, in order â€” the assertions are about these. */
const DEFAULT_PAGE_SIZE = 50

/** Every history request the page made, in order. */
let requestedPages: number[] = []

/**
 * Answer the three endpoints this page uses, with a history of `total` entries paged by `pageSize`.
 *
 * Entries are numbered so a row names the page it came from: the last page of 25 entries at 10 per
 * page holds `entry-100` upward, and nothing else does.
 */
function stubConnector({
  total = 120,
  loggingEnabled = true,
  loggingConfigured,
  loggingWriting = true,
}: {
  total?: number
  loggingEnabled?: boolean
  loggingConfigured?: boolean
  loggingWriting?: boolean | undefined
} = {}) {
  // D75 — the two halves the status endpoint reports separately. `configured` is what the
  // properties say; `enabled` is what a ping says. They default together because that is the
  // ordinary case; a store that is named and unreachable sets them apart.
  // D84 adds a third: `writing` is whether an appender is attached, which the first two cannot
  // see. It is `undefined` on a connector older than 2026.3.7, which is not the same as false.
  const configured = loggingConfigured ?? loggingEnabled
  requestedPages = []
  vi.spyOn(axios, "get").mockImplementation(async (url: string, config?: unknown) => {
    const params = (config as { params?: Record<string, unknown> } | undefined)?.params

    if (url.includes("/logging/status")) {
      return {
        data: { engine: "solr", configured, enabled: loggingEnabled, writing: loggingWriting },
        status: 200,
      } as never
    }

    if (url.includes("/logging/indexing")) {
      const page = Number(params?.page ?? 0)
      const pageSize = Number(params?.pageSize ?? DEFAULT_PAGE_SIZE)
      requestedPages.push(page)
      const start = page * pageSize
      const content = Array.from({ length: Math.max(0, Math.min(pageSize, total - start)) }, (_, i) => ({
        contentId: CONTENT_ID,
        status: "INDEXED",
        date: "2026-08-25T10:00:00Z",
        details: `entry-${start + i}`,
      }))
      return {
        data: { content, page, pageSize, totalElements: total, totalPages: Math.ceil(total / pageSize) },
        status: 200,
      } as never
    }

    if (url.includes(`/monitoring/indexing/item/${ITEM_ID}`)) {
      return {
        data: { id: ITEM_ID, objectId: CONTENT_ID, status: "INDEXED", source: "wknd" },
        status: 200,
      } as never
    }

    return { data: {}, status: 200 } as never
  })
}

/** Mount the item page on one of its two tabs. */
function renderItem(tab: "detail" | "history") {
  const path = `/integration/${STANDALONE}/monitoring/wknd/${ITEM_ID}${tab === "history" ? "/history" : ""}`
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/integration/:id/monitoring/:source/:itemId/:tab?"
          element={<IntegrationInstanceMonitoringItemPage />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe("D53: the History tab opens on the most recent entries", () => {
  it("jumps to the last page when there is more than one", async () => {
    /*
     * The rule that makes this flow worth a test. Entries come back oldest-first, so page 0 is the
     * least interesting thing the reader could be shown; the page asks for it, learns how many
     * pages exist, and immediately asks again for the last one.
     */
    stubConnector({ total: 120 })
    renderItem("history")

    await waitFor(() => expect(requestedPages.length).toBeGreaterThan(1))

    expect(requestedPages[0]).toBe(0)
    expect(requestedPages.at(-1)).toBe(2) // 25 entries, 10 per page â†’ pages 0..2
  })

  it("shows the last page's rows, not the first page's", async () => {
    // The observable half. Asserting only on the request would pass against a page that fetched
    // correctly and rendered whatever arrived first.
    stubConnector({ total: 120 })
    renderItem("history")

    expect(await screen.findByText("entry-100")).toBeDefined()
    expect(screen.queryByText("entry-0")).toBeNull()
  })

  it("does not jump when everything fits on one page", async () => {
    // The jump exists to skip pages, and a single page has none to skip. Re-requesting the same
    // page would be a wasted round trip on every open.
    stubConnector({ total: 4 })
    renderItem("history")

    await waitFor(() => expect(requestedPages.length).toBeGreaterThan(0))
    await screen.findByText("entry-0")

    expect(requestedPages).toEqual([0])
  })
})

describe("D53: the History tab asks only when it is open and available", () => {
  it("fetches nothing while the Detail tab is showing", async () => {
    stubConnector({ total: 120 })
    renderItem("detail")

    // The item itself loads; the history does not, because nothing is displaying it.
    await waitFor(() => expect(axios.get).toHaveBeenCalled())
    expect(requestedPages).toEqual([])
  })

  it("fetches nothing when the connector reports logging disabled", async () => {
    // Without the logging engine there is no history to page through, and the tab says so rather
    // than showing an empty table.
    stubConnector({ total: 120, loggingEnabled: false })
    renderItem("history")

    await waitFor(() => expect(axios.get).toHaveBeenCalled())
    expect(requestedPages).toEqual([])
  })

  it("says the store did not answer rather than showing an empty history", async () => {
    /*
     * D75 — the state that had nowhere to go. A configured store that does not answer used to
     * report itself enabled, so the tab opened onto an empty table and the reader concluded the
     * connector had indexed nothing. The tab now follows `configured` and its body follows
     * `enabled`, so "switched on" and "answering" are two different sentences on screen.
     */
    stubConnector({ total: 120, loggingConfigured: true, loggingEnabled: false })
    renderItem("history")

    expect(await screen.findByText(/did not answer/i)).toBeDefined()
    // And it does not ask for pages it knows it cannot get.
    expect(requestedPages).toEqual([])
  })

  it("says nothing is writing rather than showing a history that will stay empty", async () => {
    /*
     * D84 — the third state, and the one the first two could not express. The store answers, so
     * `enabled` is true and the old code rendered the table: an empty history indistinguishable
     * from a connector that indexed nothing. The cause is a different setting from an outage
     * (`logging.config`, not the URI), so it gets its own sentence.
     */
    stubConnector({ total: 0, loggingEnabled: true, loggingWriting: false })
    renderItem("history")

    expect(await screen.findByText(/nothing is writing to it/i)).toBeDefined()
  })

  it("still renders the history for a connector too old to report writing", async () => {
    // `writing` absent is not `writing: false`. A pre-2026.3.7 connector says nothing about the
    // writer, and refusing to show its history over a field it never sends would be a regression.
    stubConnector({ total: 120, loggingEnabled: true, loggingWriting: undefined })
    renderItem("history")

    expect(await screen.findByText("entry-100")).toBeDefined()
    expect(screen.queryByText(/nothing is writing to it/i)).toBeNull()
  })
})

describe("D53: paging through history", () => {
  it("requests the page the reader asks for", async () => {
    stubConnector({ total: 120 })
    renderItem("history")

    // Land on the last page first.
    await screen.findByText("entry-100")

    // The pager is two icon buttons labelled `<` and `>`; the first is "previous".
    const previous = screen.getByRole("button", { name: "<" })
    fireEvent.click(previous)

    await waitFor(() => expect(requestedPages.at(-1)).toBe(1))
    expect(await screen.findByText("entry-50")).toBeDefined()
  })

  it("keeps showing one page's rows at a time", async () => {
    // A refactor that accumulated results instead of replacing them would still pass every
    // assertion above.
    stubConnector({ total: 120 })
    renderItem("history")

    await screen.findByText("entry-100")
    const table = screen.getByRole("table")
    expect(within(table).queryByText("entry-0")).toBeNull()
    expect(within(table).queryByText("entry-50")).toBeNull()
  })
})
