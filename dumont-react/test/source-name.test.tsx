/*
 * D361 — a source name is the key of its source in the connector URLs (`index/{name}`,
 * `reindex/{name}`, `audit/{source}`, monitoring), so whitespace is refused before the source is
 * saved, with a message that says why.
 */
import { fireEvent, screen } from "@testing-library/react"
import axios from "axios"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { isValidSourceName } from "@/lib/source-name"
import IntegrationInstanceDbSourcePage from "@/pages/integration.instance.db.source.page"

import { answerGet, renderSurface } from "./support/harness"

beforeEach(() => {
  vi.restoreAllMocks()
})

describe("D361: a source name cannot contain whitespace", () => {
  it("accepts names without whitespace and refuses any whitespace", () => {
    expect(isValidSourceName("wknd-site_en")).toBe(true)
    expect(isValidSourceName("")).toBe(true) // emptiness is the required rule's message
    expect(isValidSourceName("wknd site")).toBe(false)
    expect(isValidSourceName("wknd\tsite")).toBe(false)
    expect(isValidSourceName(" wknd")).toBe(false)
  })

  it("refuses to save a DB source whose name has a space, saying why", async () => {
    answerGet({ "/db/source/db-3": { id: "db-3", name: "products", turSNSites: [] } })
    const put = vi.spyOn(axios, "put")

    renderSurface(<IntegrationInstanceDbSourcePage />, {
      pattern: "/integration/:id/db-source/:sourceId/:tab",
      path: "/integration/local/db-source/db-3/general",
    })
    const name = await screen.findByDisplayValue("products")
    fireEvent.change(name, { target: { value: "my products" } })
    fireEvent.submit(name.closest("form")!)

    expect(await screen.findByText(/The name cannot contain spaces/)).toBeDefined()
    expect(put).not.toHaveBeenCalled()
  })
})
