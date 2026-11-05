/*
 * D314 — the catalog source form turns on the DSpace profile of an OAI-PMH source.
 *
 * The profile harvests dim and reads the DSpace REST API, so switching it on swaps the metadata
 * prefix for the REST API URL, adds the site key, and shows full text as on unless the source
 * turned it off. This pins that the form saves those fields, refuses the profile without a REST
 * URL before the API has to, and leaves a generic source's full text off.
 */
import { fireEvent, screen, waitFor } from "@testing-library/react"
import axios from "axios"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { IntegrationCatalogSourceForm } from "@/components/integration/integration.catalog.source.form"
import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model"

import { renderSurface } from "./support/harness"

const SOURCE: TurIntegrationCatalogSource = {
  id: "7",
  name: "Repository",
  description: "",
  catalogUrl: "",
  authHeaderName: "",
  authHeaderValue: "",
  locale: "pt_BR",
  oaiPmhBaseUrl: "https://repo.example/server/oai/request",
  turSNSites: ["acervo"],
}

const REST_PLACEHOLDER = "https://repositorio.example.edu/server/api"

function renderForm(value: TurIntegrationCatalogSource) {
  return renderSurface(
    <IntegrationCatalogSourceForm value={value} isNew={false} integrationId="local" sourceId="7" tab="connection" />,
    { pattern: "/integration/:id/catalog-source/:sourceId/:tab", path: "/integration/local/catalog-source/7/connection" },
  )
}

function submit(container: HTMLElement) {
  const form = container.querySelector("form")
  if (!form) throw new Error("the catalog source form did not render")
  fireEvent.submit(form)
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe("D314: the catalog source form reads a repository as DSpace", () => {
  it("swaps the metadata prefix for the REST URL and the site key", () => {
    renderForm(SOURCE)
    expect(screen.getByPlaceholderText("oai_dc")).toBeDefined()
    expect(screen.queryByPlaceholderText(REST_PLACEHOLDER)).toBeNull()
    expect(screen.getByRole("switch", { name: "Full text from PDFs" }).getAttribute("aria-checked")).toBe("false")

    fireEvent.click(screen.getByRole("switch", { name: "DSpace profile" }))

    expect(screen.queryByPlaceholderText("oai_dc")).toBeNull()
    expect(screen.getByPlaceholderText(REST_PLACEHOLDER)).toBeDefined()
    expect(screen.getByText("Site key")).toBeDefined()
    expect(screen.getByRole("switch", { name: "Full text" }).getAttribute("aria-checked"))
      .toBe("true")
  })

  it("saves the profile, the REST URL and the site key", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm(SOURCE)

    fireEvent.click(screen.getByRole("switch", { name: "DSpace profile" }))
    fireEvent.change(screen.getByPlaceholderText(REST_PLACEHOLDER),
      { target: { value: "https://repo.example/server-x/api" } })
    fireEvent.change(screen.getByPlaceholderText("repositorio.example.edu"), { target: { value: "repo" } })
    submit(container)

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][1]).toMatchObject({
      oaiPmhProfile: "dspace",
      dspaceRestUrl: "https://repo.example/server-x/api",
      siteKey: "repo",
    })
  })

  it("turns full text off only when asked", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm({ ...SOURCE, oaiPmhProfile: "dspace", dspaceRestUrl: "https://repo.example/server/api" })

    fireEvent.click(screen.getByRole("switch", { name: "Full text" }))
    submit(container)

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][1]).toMatchObject({ oaiPmhProfile: "dspace", oaiPmhFullText: false })
  })

  it("refuses the profile without a REST URL", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm(SOURCE)

    fireEvent.click(screen.getByRole("switch", { name: "DSpace profile" }))
    submit(container)

    expect(await screen.findByText("The DSpace profile needs the REST API URL.")).toBeDefined()
    expect(put).not.toHaveBeenCalled()
  })
})
