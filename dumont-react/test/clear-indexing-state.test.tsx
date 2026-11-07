/*
 * Clear indexing state, offered on every source page beside its run actions.
 *
 * The action sends `DELETE /connector/ledger/{source}`, which forgets what the connector already
 * sent without starting a run. What this pins is the one thing a page can get wrong: the
 * identifier. Each plugin owns its sources by a different field — a structured source (AEM, DB,
 * Assets, Catalog) by its name, the Web Crawler by its id — so a page passing the field the reader
 * sees instead of the one the plugin matches gets a 404 for a source that exists.
 */
import { fireEvent, screen, waitFor } from "@testing-library/react"
import { toast } from "@viglet/viglet-design-system"
import axios from "axios"
import { beforeEach, describe, expect, it, vi } from "vitest"

import IntegrationInstanceDbSourcePage from "@/pages/integration.instance.db.source.page"
import IntegrationInstanceWcSourcePage from "@/pages/integration.instance.wc.source.page"

import { answerGet, renderSurface } from "./support/harness"

const WC_SOURCE = {
  id: "wc-7",
  title: "Docs site",
  description: "",
  locale: "en_US",
  localeClass: "",
  url: "https://docs.example",
  username: "",
  password: "",
  turSNSites: [],
  startingPoints: [],
  allowUrls: [],
  notAllowUrls: [],
  notAllowExtensions: [],
  attributeMappings: [],
}

const DB_SOURCE = { id: "db-3", name: "products", turSNSites: [] }

/** Opens the header's `⋮`, picks the action, types the source's name and confirms. */
async function clearState(confirmName: string) {
  const [trigger] = await screen.findAllByRole("button", { name: "More actions" })
  fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false, pointerType: "mouse" })
  const item = await screen.findByRole("menuitem", { name: /Clear indexing state/ })
  await waitFor(() => expect(item.getAttribute("data-disabled")).toBeNull())
  fireEvent.click(item)

  expect(await screen.findByText(/The next run sends every document again\. Nothing is removed from the index/))
    .toBeDefined()
  fireEvent.change(screen.getByPlaceholderText(confirmName), { target: { value: confirmName } })
  fireEvent.click(screen.getByRole("button", { name: "Clear indexing state" }))
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe("Clear indexing state", () => {
  it("clears a Web Crawler source by its id, not its title", async () => {
    answerGet({ "/wc/source/wc-7": WC_SOURCE })
    const del = vi.spyOn(axios, "delete").mockResolvedValue({ data: { status: "cleared" }, status: 200 } as never)
    const success = vi.spyOn(toast, "success")

    renderSurface(<IntegrationInstanceWcSourcePage />, {
      pattern: "/integration/:id/wc-source/:sourceId/:tab",
      path: "/integration/local/wc-source/wc-7/general",
    })
    await screen.findAllByText("Docs site")
    await clearState("Docs site")

    await waitFor(() => expect(del).toHaveBeenCalledTimes(1))
    expect(del.mock.calls[0][0]).toBe("/v2/integration/local/connector/ledger/wc-7")
    await waitFor(() => expect(success).toHaveBeenCalledWith(
      "Indexing state of Docs site cleared. The next run sends every document again.",
    ))
  })

  it("clears a database source by its name, and warns while a run holds it", async () => {
    answerGet({ "/db/source/db-3": DB_SOURCE })
    const del = vi.spyOn(axios, "delete").mockRejectedValue(
      new axios.AxiosError("conflict", "ERR_BAD_REQUEST", undefined, undefined,
        { status: 409, data: {}, statusText: "Conflict", headers: {}, config: {} } as never),
    )
    const warning = vi.spyOn(toast, "warning")

    renderSurface(<IntegrationInstanceDbSourcePage />, {
      pattern: "/integration/:id/db-source/:sourceId/:tab",
      path: "/integration/local/db-source/db-3/general",
    })
    await screen.findAllByText("products")
    await clearState("products")

    await waitFor(() => expect(del).toHaveBeenCalledTimes(1))
    expect(del.mock.calls[0][0]).toBe("/v2/integration/local/connector/ledger/products")
    await waitFor(() => expect(warning).toHaveBeenCalledWith(
      "A run is in progress for products. Wait for it to finish, then clear the state.",
    ))
  })
})
