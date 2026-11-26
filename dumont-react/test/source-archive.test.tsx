/*
 * D362 — moving source configuration between instances from the console.
 *
 * D362: every source list (AEM, DB, web crawler) offers "Export all (ZIP)" and "Import ZIP",
 * against the plugin's own `/export` and `/import`. What a list can get wrong is the URL — each
 * plugin owns a different one — and forgetting to reload after an import, so both are pinned.
 */
import { fireEvent, screen, waitFor } from "@testing-library/react"
import { toast } from "@viglet/viglet-design-system"
import axios from "axios"
import { beforeEach, describe, expect, it, vi } from "vitest"

import IntegrationInstanceDbSourceListPage from "@/pages/integration.instance.db.source.list.page"
import IntegrationInstanceWcSourceListPage from "@/pages/integration.instance.wc.source.list.page"
import { filenameFrom } from "@/services/integration/source-archive.service"

import { renderSurface } from "./support/harness"

beforeEach(() => {
  vi.restoreAllMocks()
})

async function openActions() {
  const [trigger] = await screen.findAllByRole("button", { name: /actions/i })
  fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false, pointerType: "mouse" })
}

describe("D362: export all and import from a source list", () => {
  it("imports a zip into an empty DB list through the DB plugin, then reloads the list", async () => {
    let listed: unknown[] = []
    vi.spyOn(axios, "get").mockImplementation(async (url: string) => {
      if (url.endsWith("/db/source")) return { data: listed, status: 200 } as never
      throw new Error(`no stub for GET ${url}`)
    })
    const post = vi.spyOn(axios, "post").mockImplementation(async () => {
      listed = [{ id: "db-1", name: "products", description: "" }]
      return { data: {}, status: 200 } as never
    })
    const success = vi.spyOn(toast, "success")

    renderSurface(<IntegrationInstanceDbSourceListPage />, {
      pattern: "/integration/:id/db-source",
      path: "/integration/local/db-source",
    })
    fireEvent.click(await screen.findByRole("button", { name: /Import ZIP/ }))
    const zip = new File(["PK"], "db-sources.zip", { type: "application/zip" })
    fireEvent.change(screen.getByLabelText("Import ZIP"), { target: { files: [zip] } })

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toBe("/v2/integration/local/db/source/import")
    const form = post.mock.calls[0][1] as FormData
    expect(form.get("file")).toBe(zip)
    await waitFor(() => expect(success).toHaveBeenCalledWith("Configuration imported successfully."))
    expect(await screen.findByText("products")).toBeDefined()
  })

  it("exports every web-crawler source from the WC plugin's export", async () => {
    const get = vi.spyOn(axios, "get").mockImplementation(async (url: string) => {
      if (url.endsWith("/wc/source")) {
        return { data: [{ id: "wc-1", title: "Docs", url: "https://docs.example" }], status: 200 } as never
      }
      if (url.endsWith("/wc/source/export")) {
        return {
          data: new Blob(["PK"]),
          status: 200,
          headers: { "content-disposition": "attachment;filename=WC_2026-11-26.zip" },
        } as never
      }
      throw new Error(`no stub for GET ${url}`)
    })
    const createObjectURL = vi.fn(() => "blob:x")
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})

    renderSurface(<IntegrationInstanceWcSourceListPage />, {
      pattern: "/integration/:id/wc-source",
      path: "/integration/local/wc-source",
    })
    await screen.findByText("Docs")
    await openActions()
    fireEvent.click(await screen.findByRole("menuitem", { name: /Export all \(ZIP\)/ }))

    await waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    const exportCall = get.mock.calls.find(([url]) => url.endsWith("/export"))
    expect(exportCall?.[0]).toBe("/v2/integration/local/wc/source/export")
    expect(exportCall?.[1]).toMatchObject({ responseType: "blob" })
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe("WC_2026-11-26.zip")
  })

  it("reads the server's file name from Content-Disposition", () => {
    expect(filenameFrom("attachment;filename=Aem_2026-11-26_101500.zip")).toBe("Aem_2026-11-26_101500.zip")
    expect(filenameFrom('attachment; filename="db sources.zip"')).toBe("db sources.zip")
    expect(filenameFrom(undefined)).toBeNull()
  })
})
