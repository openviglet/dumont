/*
 * D37 — the claim the parallel tree exists to make: both eras serve.
 *
 * A migration of 39 pages is reversible only if a surface can move without its console route
 * being touched. That is a property of the *route table*, not of either page, so these cases
 * mount the real `App` — its actual `<Routes>`, both roots, the real providers — at a path and
 * ask what came back. A test that rendered `BentoRoutes` directly would pass even if the tree
 * were never wired into the application, which is the one mistake that makes the whole task a
 * no-op.
 *
 * The pairing matters more than either half. `/bento/…/db-source` and `/admin/…/db-source` are
 * asserted against the same fixture in the same file, so a change that quietly turned the
 * addition into a replacement — a redirect, a moved route, a deleted console line — fails here
 * rather than being noticed by a user who bookmarked the old URL.
 */
import { screen, waitFor, within } from "@testing-library/react"
import { render } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"

import App from "@/App"
import { answerGet } from "./support/harness"

const STANDALONE = "local"
const CONSOLE_DB = `/admin/integration/instance/${STANDALONE}/db-source`
const BENTO_DB = `/bento/integration/instance/${STANDALONE}/db-source`
const BENTO_HOME = `/bento/integration/instance/${STANDALONE}`

/** The two sources both eras render, so the comparison is against one fixture. */
const SOURCES = [
  { id: "1", name: "Products", description: "Catalogue rows" },
  { id: "2", name: "Customers", description: "CRM rows" },
]

/**
 * Every connector plugin active, so a source list is reachable whichever one it belongs to. The
 * sidebar and the hubs filter on this, and a plugin left out here reads as a missing route.
 */
const CONNECTOR_STATUS = {
  status: "UP",
  providers: ["AEM", "JDBC-DATABASE", "WEB-CRAWLER", "ASSETS", "CATALOG-BRIDGE", "EDS"],
}
const USER = { username: "operator" }

/** Everything the shell and its providers ask for before any page paints. */
function stubConnector(sources: unknown = SOURCES) {
  answerGet({
    "/db/source": sources,
    "/connector/status": CONNECTOR_STATUS,
    "/connector/system-info": { appVersion: "2026.3.1" },
    "/connector/queue": { size: 0 },
    "/user": USER,
    "/ping": { status: "UP" },
  })
}

/** Mount the application itself at `path` — the real route table, not a reconstruction. */
function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe("D37: both trees serve", () => {
  it("serves the database sources under the bento tree", async () => {
    stubConnector()
    renderApp(BENTO_DB)

    expect(await screen.findByText("Products")).toBeDefined()
    expect(screen.getByText("Customers")).toBeDefined()
  })

  it("still serves the same surface under the console tree, unchanged", async () => {
    // The half that makes the migration reversible. If this ever fails, the bento route stopped
    // being an addition and became a replacement.
    stubConnector()
    renderApp(CONSOLE_DB)

    expect(await screen.findByText("Products")).toBeDefined()
    expect(screen.getByText("Customers")).toBeDefined()
  })

  it("mounts different chrome on each, rather than one shell with a flag", async () => {
    // The console root is a SidebarProvider wrapping a sidebar; the bento root is a fixed rail
    // with no provider at all. Asserting that each renders its own navigation landmark is what
    // proves the two roots are actually distinct — a single shell reused under both prefixes
    // would satisfy every other case in this file.
    stubConnector()
    const console = renderApp(CONSOLE_DB)
    await screen.findByText("Products")
    expect(console.container.querySelector(".bento-rail-gutter")).toBeNull()
    console.unmount()

    stubConnector()
    const bento = renderApp(BENTO_DB)
    await screen.findByText("Products")
    expect(bento.container.querySelector(".bento-rail-gutter")).not.toBeNull()
  })
})

describe("D37: the bento tree's own root", () => {
  it("lands Home on the sections, each opening a hub the tree serves", async () => {
    stubConnector()
    renderApp(BENTO_HOME)

    /*
     * Scoped to `main`, because the rail now lists the same sections in the same words — which is
     * the point of D45, and would otherwise make every one of these assertions ambiguous.
     */
    const page = within(await screen.findByRole("main"))

    // Home → hub → surface is the hierarchy the rail assumes, so Home lists sections rather than
    // leaves. A tile pointing at a hub route this tree does not answer is the failure.
    const sources = await page.findByRole("link", { name: /sources/i })
    expect(sources.getAttribute("href")).toBe(`${BENTO_HOME}/area/sources`)
  })

  it("lists the sections on the rail, which is what a hub is for", async () => {
    // D45's whole claim. The rail renders only sections carrying an `areaRoute`, so before the
    // hubs existed it showed Home alone and the shell's only navigation was a keyboard shortcut.
    stubConnector()
    renderApp(BENTO_HOME)

    const rail = within(await screen.findByRole("navigation", { name: /primary/i }))
    expect(rail.getByRole("link", { name: /sources/i }).getAttribute("href")).toBe(
      `${BENTO_HOME}/area/sources`,
    )
    expect(rail.getByRole("link", { name: /integration/i }).getAttribute("href")).toBe(
      `${BENTO_HOME}/area/integration`,
    )
  })

  it("reaches the migrated surface through its section hub", async () => {
    // The other half of the hierarchy, and the reason the hub exists: the rail lists hubs, so a
    // hub that could not reach its own surfaces would leave the rail pointing at a dead end.
    stubConnector()
    renderApp(`${BENTO_HOME}/area/sources`)

    const page = within(await screen.findByRole("main"))
    const db = await page.findByRole("link", { name: /database sources/i })
    expect(db.getAttribute("href")).toBe(BENTO_DB)
  })

  it("sends an unknown section back Home instead of showing an empty one", async () => {
    // A typed or stale URL. Rendering "nothing here" would claim the section exists and is empty,
    // which is a different and wrong statement.
    stubConnector()
    renderApp(`${BENTO_HOME}/area/not-a-section`)

    // Redirected to Home, so the page shows the section mosaic rather than an empty hub.
    const page = within(await screen.findByRole("main"))
    const sources = await page.findByRole("link", { name: /sources/i })
    expect(sources.getAttribute("href")).toBe(`${BENTO_HOME}/area/sources`)
    expect(page.queryByText(/nothing in this section/i)).toBeNull()
  })

  it("sends a not-yet-migrated detail page back to the console tree", async () => {
    // The link the migration is guaranteed to need, and the one most likely to be wrong: the DB
    // *detail* page has not moved, so a tile must name the console tree explicitly. Deriving the
    // prefix from the live URL — which is what `ROUTES.INTEGRATION_INSTANCE` does — would resolve
    // to `/bento/...` from in here and send every tile to a route nothing serves.
    stubConnector()
    renderApp(BENTO_DB)

    const tile = (await screen.findByText("Products")).closest("a")
    expect(tile?.getAttribute("href")).toBe(`${CONSOLE_DB}/1`)
  })
})

/**
 * The six source lists, with what each service answers and the two fields its cards show.
 *
 * Written out rather than derived from the declaration: this is the table that says the routes
 * were actually wired, and building it from the same source the code builds from would pass
 * against a tree that serves none of them.
 */
const SOURCE_LISTS = [
  { path: "source", endpoint: "/aem/source", title: "WKND author", second: "https://author.wknd" },
  { path: "db-source", endpoint: "/db/source", title: "Products", second: "Catalogue rows" },
  { path: "wc-source", endpoint: "/wc/source", title: "Marketing site", second: "https://www.example.com" },
  { path: "assets-source", endpoint: "/assets/source", title: "Brochures", second: "/mnt/brochures" },
  { path: "catalog-source", endpoint: "/catalog/source", title: "Parts catalogue", second: "Normalised rows" },
  { path: "eds-source", endpoint: "/eds/source", title: "aem.live site", second: "https://main--wknd--aem.hlx.live" },
] as const

describe("D46: all six source lists render bento, and every console route still serves", () => {
  for (const list of SOURCE_LISTS) {
    it(`serves ${list.path} on both trees`, async () => {
      /*
       * Both halves in one case, on one fixture. The migration is only reversible while the page
       * it replaced is still mounted, so asserting the bento route alone would let a "migration"
       * that quietly deleted the original pass.
       */
      const row = {
        id: "1",
        name: list.title,
        title: list.title,
        description: list.second,
        endpoint: list.second,
        url: list.second,
        sourceDir: list.second,
        baseUrl: list.second,
      }

      answerGet({ [list.endpoint]: [row], "/connector/status": CONNECTOR_STATUS, "/user": USER })
      const bento = renderApp(`${BENTO_HOME}/${list.path}`)
      expect(await within(await screen.findByRole("main")).findByText(list.title)).toBeDefined()
      // Reached through the bento shell, not by the console one happening to answer that path.
      expect(bento.container.querySelector(".bento-rail-gutter")).not.toBeNull()
      bento.unmount()

      answerGet({ [list.endpoint]: [row], "/connector/status": CONNECTOR_STATUS, "/user": USER })
      const console = renderApp(`/admin/integration/instance/${STANDALONE}/${list.path}`)
      expect(await screen.findByText(list.title)).toBeDefined()
      expect(console.container.querySelector(".bento-rail-gutter")).toBeNull()
    })
  }
})

describe("D37: the bento tree's empty branch", () => {
  it("shows the blank slate rather than an empty mosaic", async () => {
    stubConnector([])
    renderApp(BENTO_DB)

    await waitFor(() => {
      expect(screen.getByText(/don't seem to have any database sources/i)).toBeDefined()
    })
  })
})
