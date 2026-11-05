/*
 * D315 — the catalog source form shows and saves an OAI-PMH binding.
 *
 * The connector already harvests an OAI-PMH repository; until the form carried the fields, a
 * source could only be bound to one through the REST API. This pins that the form renders the
 * base URL, metadata prefix, set and full-text switch, sends them on save, and refuses a source
 * that names both a repository and a JSON API manifest before the API has to.
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
  turSNSites: ["acervo"],
}

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

describe("D315: the catalog source form binds an OAI-PMH repository", () => {
  it("renders the repository fields with oai_dc as the prefix hint", () => {
    renderForm(SOURCE)

    expect(screen.getByText("OAI-PMH repository")).toBeDefined()
    expect(screen.getByText("Base URL")).toBeDefined()
    expect(screen.getByPlaceholderText("oai_dc")).toBeDefined()
    expect(screen.getByText("Set")).toBeDefined()
    expect(screen.getByRole("switch", { name: "Full text from PDFs" })).toBeDefined()
  })

  it("saves the base URL, prefix, set and full text", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm(SOURCE)

    fireEvent.change(screen.getByPlaceholderText("https://repositorio.example.edu/oai/request"),
      { target: { value: "https://repo.example/oai/request" } })
    fireEvent.change(screen.getByPlaceholderText("oai_dc"), { target: { value: "oai_dc" } })
    fireEvent.change(screen.getByPlaceholderText("com_123456789_1"), { target: { value: "theses" } })
    fireEvent.click(screen.getByRole("switch", { name: "Full text from PDFs" }))
    submit(container)

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][1]).toMatchObject({
      oaiPmhBaseUrl: "https://repo.example/oai/request",
      oaiPmhMetadataPrefix: "oai_dc",
      oaiPmhSet: "theses",
      oaiPmhFullText: true,
      turSNSites: ["acervo"],
    })
  })

  it("refuses a repository and a JSON API manifest together", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm({ ...SOURCE, jsonApiManifest: "{\"name\": \"jobs\"}" })

    fireEvent.change(screen.getByPlaceholderText("https://repositorio.example.edu/oai/request"),
      { target: { value: "https://repo.example/oai/request" } })
    submit(container)

    expect(await screen.findByText(/not both/)).toBeDefined()
    expect(put).not.toHaveBeenCalled()
  })

  it("refuses a base URL that is not http(s)", async () => {
    const put = vi.spyOn(axios, "put").mockResolvedValue({ data: SOURCE, status: 200 } as never)
    const { container } = renderForm(SOURCE)

    fireEvent.change(screen.getByPlaceholderText("https://repositorio.example.edu/oai/request"),
      { target: { value: "ftp://repo.example/oai" } })
    submit(container)

    expect(await screen.findByText("The base URL must be an http(s) URL.")).toBeDefined()
    expect(put).not.toHaveBeenCalled()
  })
})
