/*
 * D39 — one declaration, read three times.
 *
 * The sidebar, the bento rail and the ⌘K palette all answer the same question: which surfaces
 * exist, what are they called, where do they point. Before this task the answer lived twice —
 * `manifest.navItems` for the Module Federation wire, and the sidebar's own grouping over it —
 * and a rail and a palette would have made it four. Lists that say the same thing drift on the
 * first rename, and a rename is precisely the change nobody remembers to make four times.
 *
 * So what is asserted here is not that the nav renders. It is that there is only *one* list:
 * the wire shape is derived and unchanged, the sidebar is driven by the declaration, and the two
 * bento derivations cannot disagree with it or with each other.
 */
import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"

import App from "@/App"
import {
  DUMONT_NAV,
  dumontNavGroups,
  dumontNavItems,
  visibleNavSurfaces,
} from "@/app/nav.const"
import manifest from "@/manifest"
import { answerGet } from "./support/harness"

const STANDALONE = "local"

/**
 * Exactly what `manifest.navItems` carried before D39, typed out rather than derived.
 *
 * The point of writing it twice *here* is that this is the one place duplication is correct: a
 * host reads this over `./manifest`, so the wire shape is a contract, and a test that built its
 * expectation from the same declaration the code builds it from would pass through any change at
 * all. If this fails, the refactor stopped being invisible to Turing.
 */
const WIRE_BEFORE_D39 = [
  { titleKey: "integration.nav.sources", url: "/source", icon: "IconGitCommit", order: 10, provider: "AEM" },
  { titleKey: "integration.nav.dbSources", url: "/db-source", icon: "IconDatabase", order: 10, provider: "JDBC-DATABASE" },
  { titleKey: "integration.nav.wcSources", url: "/wc-source", icon: "IconGlobe", order: 10, provider: "WEB-CRAWLER" },
  { titleKey: "integration.nav.assetsSources", url: "/assets-source", icon: "IconFolder", order: 10, provider: "ASSETS" },
  { titleKey: "integration.nav.catalogSources", url: "/catalog-source", icon: "IconBuildingBank", order: 10, provider: "CATALOG-BRIDGE" },
  { titleKey: "integration.nav.edsSources", url: "/eds-source", icon: "IconCloudComputing", order: 10, provider: "EDS" },
  { titleKey: "integration.nav.sourceInference", url: "/source-inference", icon: "IconWand", order: 15 },
  { titleKey: "integration.nav.indexingRules", url: "/indexing-rule", icon: "IconTools", order: 20 },
  { titleKey: "integration.nav.indexingManager", url: "/indexing-manager", icon: "IconAdjustmentsSearch", order: 30 },
  { titleKey: "integration.nav.monitoring", url: "/monitoring", icon: "IconGraph", order: 40 },
  { titleKey: "integration.nav.indexingStats", url: "/indexing-stats", icon: "IconChartBar", order: 50 },
  { titleKey: "integration.nav.coverage", url: "/coverage", icon: "IconActivityHeartbeat", order: 55 },
  { titleKey: "integration.nav.doubleCheck", url: "/double-check", icon: "IconListCheck", order: 60 },
  { titleKey: "integration.nav.insights", url: "/insights", icon: "IconSparkles", order: 90 },
  { titleKey: "integration.nav.plugins", url: "/plugins", icon: "IconPuzzle", order: 100 },
  { titleKey: "integration.nav.systemInfo", url: "/system-info", icon: "IconInfoCircle", order: 110 },
]

describe("D39: the Module Federation wire is derived, not duplicated", () => {
  it("still hands the host exactly what it handed before", () => {
    expect(manifest.navItems).toEqual(WIRE_BEFORE_D39)
  })

  it("carries no `provider` key at all on a common surface", () => {
    // `provider: undefined` and an absent key serialise differently, and the host filters on the
    // key's presence. The spread that builds this is the kind of thing a refactor gets subtly
    // wrong without any type error.
    const insights = manifest.navItems.find((item) => item.url === "/insights")!
    expect("provider" in insights).toBe(false)
  })
})

describe("D39: every reader gets the same answer", () => {
  const visible = visibleNavSurfaces(["JDBC-DATABASE", "AEM"])

  it("hides a surface whose connector is not active, in the shared filter", () => {
    const ids = visible.map((surface) => surface.id)
    expect(ids).toContain("db-source")
    expect(ids).toContain("source")
    // The web crawler is not among the active providers, so no reader may list it.
    expect(ids).not.toContain("wc-source")
    // A surface with no provider is always visible.
    expect(ids).toContain("insights")
  })

  it("gives the rail and the palette the same surfaces, in the same order", () => {
    // The claim the whole task rests on: `⌘K` and the rail cannot disagree, because neither of
    // them holds a list. Comparing the *sets* would pass if one silently dropped a section.
    const fromPalette = dumontNavItems(STANDALONE, visible).map((item) => item.id)
    const fromRail = dumontNavGroups(STANDALONE, visible).flatMap((group) =>
      group.items.map((item) => item.id),
    )
    expect([...fromRail].sort()).toEqual([...fromPalette].sort())
    expect(fromPalette).toEqual(visible.map((surface) => surface.id))
  })

  it("drops a section with nothing visible in it rather than rendering an empty band", () => {
    const onlyIntegration = DUMONT_NAV.filter((surface) => surface.section === "integration")
    const sections = dumontNavGroups(STANDALONE, onlyIntegration).map((group) => group.section.id)
    expect(sections).not.toContain("sources")
    expect(sections).toContain("integration")
  })

  it("keeps the sources band alive on a connector with no source plugins at all", () => {
    // Not every surface in the band belongs to a provider: AI source inference is how a reader
    // *creates* a source, so it is visible on a connector that has none. A filter that emptied
    // the band with the providers would take the one entry that matters most there.
    const onlyCommon = visibleNavSurfaces([])
    expect(onlyCommon.map((surface) => surface.id)).toContain("source-inference")
    expect(dumontNavGroups(STANDALONE, onlyCommon).map((group) => group.section.id)).toContain(
      "sources",
    )
  })
})

describe("D39: where a surface points depends on whether it has moved", () => {
  it("sends a migrated surface into the bento tree", () => {
    const db = dumontNavItems(STANDALONE).find((item) => item.id === "db-source")!
    expect(db.bentoRoute).toBe(`/bento/integration/instance/${STANDALONE}/db-source`)
  })

  it("leaves a surface that has not moved with no bento route at all", () => {
    // Not an empty string and not the console route copied across: the palette decides whether to
    // navigate inside the shell or link out by whether this is set, so a placeholder value would
    // send a reader to a route the bento tree does not serve.
    //
    // Monitoring, not a source list — all six of those moved in D46, and picking a migrated
    // surface here would make the assertion pass for the wrong reason.
    const monitoring = dumontNavItems(STANDALONE).find((item) => item.id === "monitoring")!
    expect(monitoring.bentoRoute).toBeUndefined()
  })

  it("gives every migrated surface a bento route the tree actually serves", () => {
    // The other direction, and the one that rots: `migrated: true` with no route in
    // `BentoRoutes.tsx` sends the palette and the hubs at a page nothing answers.
    const migrated = dumontNavItems(STANDALONE).filter((item) => item.bentoRoute)
    expect(migrated.map((item) => item.id).sort()).toEqual([
      "assets-source",
      "catalog-source",
      "db-source",
      "eds-source",
      "source",
      "source-inference",
      "wc-source",
    ])
  })

  it("always names the console tree explicitly in the fallback", () => {
    // The palette renders *inside* the bento shell, and `ROUTES.INTEGRATION_INSTANCE` derives its
    // prefix from the live URL — so a fallback built through it would resolve to `/bento/...`
    // from exactly the place these links are used, and lead nowhere.
    for (const item of dumontNavItems(STANDALONE)) {
      expect(item.fallbackRoute.startsWith(`/admin/integration/instance/${STANDALONE}/`)).toBe(true)
    }
  })

  it("declares one entry per surface, with no duplicate ids", () => {
    const ids = DUMONT_NAV.map((surface) => surface.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

describe("D39: the console sidebar is driven by the declaration", () => {
  it("renders the visible surfaces, grouped, and omits the inactive connectors", async () => {
    answerGet({
      "/connector/status": { status: "UP", providers: ["JDBC-DATABASE"] },
      "/connector/system-info": { appVersion: "2026.3.1" },
      "/connector/queue": { queue: 0 },
      "/db/source": [],
      "/user": { username: "operator" },
      "/ping": { status: "UP" },
    })

    render(
      <MemoryRouter initialEntries={[`/admin/integration/instance/${STANDALONE}/db-source`]}>
        <App />
      </MemoryRouter>,
    )

    /*
     * Asserted on links rather than on text. "Database Sources" is also the DB list page's own
     * hero, so a text match would pass with no sidebar rendered at all — the href is the part
     * only the sidebar produces, and it is built from the declaration's `path`.
     */
    const base = `/admin/integration/instance/${STANDALONE}`

    // A surface from the declaration whose connector is active.
    const db = await screen.findByRole("link", { name: "Database Sources" })
    expect(db.getAttribute("href")).toBe(`${base}/db-source`)

    // A common surface, shown whatever the connector runs.
    expect(screen.getByRole("link", { name: "System Information" }).getAttribute("href")).toBe(
      `${base}/system-info`,
    )

    // And one whose connector is not active is absent — the sidebar reads the same filter the
    // rail and the palette do, so this is the assertion that ties all three together.
    expect(screen.queryByRole("link", { name: "Web Crawler Sources" })).toBeNull()
  })
})
