/*
 * D42 — the floor. Three surfaces, mounted for real, asserted to still render.
 *
 * Not coverage. Track I changes what 39 pages render, and until this file existed the only thing
 * that would have noticed a page going blank was a person opening it. What these three buy is the
 * narrow claim that a migration cannot silently delete a surface: the shell composes, a list page
 * shows its rows, and a form page shows the affordance that saves them.
 *
 * Each one renders the **page's own component** through a real router with a real i18n, and stubs
 * only the HTTP boundary. A test that stubbed `useTranslation`, or rendered the page's children
 * instead of the page, would pass whatever the page became — which is the failure mode that makes
 * a suite feel like coverage while asserting nothing.
 */
import { screen, waitFor } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import ConsoleRootPage from "@/app/console/console.root.page"
import DbSourceListPage from "@/pages/integration.instance.db.source.list.page"
import DbSourcePage from "@/pages/integration.instance.db.source.page"
import { answerGet, renderSurface } from "./support/harness"

const STANDALONE = "local"

/** What `/v2/connector/status` answers for a connector running the database plugin. */
const CONNECTOR_STATUS = { status: "UP", providers: ["JDBC-DATABASE"] }

describe("D42: the console shell", () => {
  it("mounts, and renders the footer with the connector's version", async () => {
    answerGet({
      "/connector/status": CONNECTOR_STATUS,
      "/connector/system-info": { appVersion: "2026.3.1" },
      "/connector/queue": { size: 0 },
    })

    renderSurface(<ConsoleRootPage />)

    // The shell is a SidebarProvider wrapping a sidebar, an outlet and the footer. Asserting on
    // the version proves the footer's own effect ran, which is the part of the shell that talks
    // to the connector — a shell that mounted but rendered nothing would pass a smoke test.
    expect(await screen.findByText(/2026\.3\.1/)).toBeDefined()

    // And the sidebar composed its navigation. Home is the one entry that does not depend on
    // which connector plugins are active, so it is what a shell owes every user.
    expect(screen.getByRole("link", { name: /home/i })).toBeDefined()
  })
})

describe("D42: a list surface", () => {
  const listRoute = { pattern: "/integration/:id/db-source", path: `/integration/${STANDALONE}/db-source` }

  it("shows a row per source the connector returns", async () => {
    answerGet({
      "/db/source": [
        { id: "1", name: "Products", description: "Catalogue rows" },
        { id: "2", name: "Customers", description: "CRM rows" },
      ],
      "/connector/status": CONNECTOR_STATUS,
    })

    renderSurface(<DbSourceListPage />, listRoute)

    expect(await screen.findByText("Products")).toBeDefined()
    expect(screen.getByText("Customers")).toBeDefined()
    expect(screen.getByText("Catalogue rows")).toBeDefined()
  })

  it("shows the blank slate, not an empty page, when there are none", async () => {
    // The branch a migration is most likely to drop, because it is the one nobody opens: an
    // empty list renders a different component tree entirely, and a page that lost it looks
    // exactly like a page that is still loading.
    answerGet({ "/db/source": [], "/connector/status": CONNECTOR_STATUS })

    renderSurface(<DbSourceListPage />, listRoute)

    await waitFor(() => {
      expect(screen.getByRole("link", { name: /database/i })).toBeDefined()
    })
  })
})

describe("D42: a form surface", () => {
  it("shows the affordance that saves it, with the source loaded", async () => {
    answerGet({
      "/db/source/7": { id: "7", name: "Products", description: "Catalogue rows" },
      "/connector/status": CONNECTOR_STATUS,
    })

    renderSurface(<DbSourcePage />, {
      pattern: "/integration/:id/db-source/:sourceId",
      path: `/integration/${STANDALONE}/db-source/7`,
    })

    // The save button is the whole point of a form page: a migration that rearranged the header
    // and lost it produces a page that looks right and cannot be submitted, which is the exact
    // regression a type-check cannot see.
    //
    // Two of them match since D88. `BentoFormHero` draws both halves of the hero → sticky-bar
    // morph and each half carries a Save, so this counts them rather than singling one out: which
    // half is on screen is the package's transition, and the claim here is about the page.
    expect(await screen.findAllByRole("button", { name: /save/i })).not.toHaveLength(0)

    // And the form is bound to what the connector returned, rather than rendering empty fields
    // beside a working button.
    await waitFor(() => {
      expect(screen.getByDisplayValue("Products")).toBeDefined()
    })
  })
})
