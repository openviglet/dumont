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
import { screen, waitFor } from "@testing-library/react"
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

/** Everything the shell and its providers ask for before any page paints. */
function stubConnector(sources: unknown = SOURCES) {
  answerGet({
    "/db/source": sources,
    "/connector/status": { status: "UP", providers: ["JDBC-DATABASE"] },
    "/connector/system-info": { appVersion: "2026.3.1" },
    "/connector/queue": { size: 0 },
    "/user": { username: "operator" },
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
  it("lands Home on a mosaic of what has actually migrated", async () => {
    stubConnector()
    renderApp(BENTO_HOME)

    // The tree serves exactly what it holds. A home listing a surface with no bento route would
    // be advertising a page the router does not answer.
    const migrated = await screen.findByRole("link", { name: /database/i })
    expect(migrated.getAttribute("href")).toBe(BENTO_DB)
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

describe("D37: the bento tree's empty branch", () => {
  it("shows the blank slate rather than an empty mosaic", async () => {
    stubConnector([])
    renderApp(BENTO_DB)

    await waitFor(() => {
      expect(screen.getByText(/don't seem to have any database sources/i)).toBeDefined()
    })
  })
})
