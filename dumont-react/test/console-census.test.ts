/*
 * D42 â€” the test that holds D35's instrument.
 *
 * The census is a script anyone can run, which makes it a number anyone can also stop running.
 * This is what re-runs it: the seven forks are named here one by one, so retiring one is a diff
 * somebody reviews, and adding one fails by name rather than moving a total.
 *
 * ## Why a named list and not a ceiling
 *
 * A ceiling â€” "no more than seven forked components" â€” lets one migration pay for the next: shim
 * `page.tsx`, and the budget quietly funds leaving `grid.list.tsx` forked for ever. The map below
 * names every one, so the diff that removes a fork also removes its line, and the diff that adds
 * a component re-declaring a package export has to write it in.
 *
 * ## What is not asserted
 *
 * Not the page-by-page chrome map. Thirty-nine entries would be a wall nobody reads, and the
 * chrome counts below carry the same signal in a form a person can check at a glance. What the
 * page half asserts is the falling number the track is actually about â€” how many surfaces are on
 * bento â€” plus the guards that keep the reader honest when that number is zero.
 */
import { readdirSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

import { census, classify, designSystemExports, isBento } from "../scripts/console-census.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

/**
 * Every component here that declares a name the design system already exports. **This is the
 * debt.** A component shimmed onto the package is deleted from this map; a new one that
 * re-declares a package export has to be written into it.
 */
const FORKED: Record<string, string[]> = {
  "components/app-footer.tsx": ["AppFooter"],
  "components/dialog.delete.tsx": ["DialogDelete"],
  "components/grid.list.tsx": ["GridList"],
  "components/language-select.tsx": ["LanguageSelect"],
  "components/nav-main.tsx": ["NavMain"],
  "components/page-header.tsx": ["PageHeader"],
  "components/page.tsx": ["Page"],
}

/**
 * Surfaces that render bento chrome. Empty until D37 built the route tree, and it grew by exactly
 * one line per migrated surface after that. The count, not the names, is what the track is
 * measured by — but the names are what make each step reviewable.
 *
 * The `app/bento` three are the shell rather than migrated pages: a parallel tree needs a root,
 * somewhere for Home to land, and a hub per section before any surface can move into it.
 *
 * The `pages/bento` twenty-seven are the surfaces that moved onto it: the six source lists (D46),
 * the six detail pages behind them (D47), the inference wizard that completed the Sources section
 * (D57), the Integration section's seven read-only surfaces plus the monitored-item page behind
 * the grid (D58), its two form surfaces and the indexing manager (D59), and the AEM model and
 * specification editors (D60) — the one pair this list would never have grown by counting
 * surfaces, found by following a link the shell could construct and the tree could not answer.
 *
 * The rest arrived in one step, and none of them is a surface that moved. D88 retired the four
 * console-era components this console still took from the package, and with them the console
 * branch of `AdaptivePageHeader` and `AdaptiveFormHeader`. A page behind either of those renders
 * the bento hero on whichever tree it is reached from, so the console originals read bento as
 * well — which is the same sentence the era rule has always spoken, now that the answer changed.
 *
 * Four pages are left on console chrome, and they are the surrounding app rather than surfaces:
 * the console shell, its home, login and setup.
 */
const ON_BENTO: string[] = [
  "app/bento/bento.area.page.tsx",
  "app/bento/bento.home.page.tsx",
  "app/bento/bento.root.page.tsx",
  "app/console/admin-settings/admin.group.page.tsx",
  "app/console/admin-settings/admin.groups.list.page.tsx",
  "app/console/admin-settings/admin.role.page.tsx",
  "app/console/admin-settings/admin.roles.list.page.tsx",
  "app/console/admin-settings/admin.user.page.tsx",
  "app/console/admin-settings/admin.users.list.page.tsx",
  "app/console/user/preferences/user.preferences.page.tsx",
  "app/console/user/profile/user.profile.page.tsx",
  "app/console/user/user.account.page.tsx",
  "pages/bento/integration.instance.assets.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.assets.source.list.bento.page.tsx",
  "pages/bento/integration.instance.catalog.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.catalog.source.list.bento.page.tsx",
  "pages/bento/integration.instance.coverage.bento.page.tsx",
  "pages/bento/integration.instance.db.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.db.source.list.bento.page.tsx",
  "pages/bento/integration.instance.double.check.bento.page.tsx",
  "pages/bento/integration.instance.eds.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.eds.source.list.bento.page.tsx",
  "pages/bento/integration.instance.indexing.manager.bento.page.tsx",
  "pages/bento/integration.instance.indexing.rules.bento.page.tsx",
  "pages/bento/integration.instance.indexing.rules.list.bento.page.tsx",
  "pages/bento/integration.instance.indexing.stats.bento.page.tsx",
  "pages/bento/integration.instance.insights.bento.page.tsx",
  "pages/bento/integration.instance.monitoring.bento.page.tsx",
  "pages/bento/integration.instance.monitoring.item.bento.page.tsx",
  "pages/bento/integration.instance.orphans.bento.page.tsx",
  "pages/bento/integration.instance.plugins.bento.page.tsx",
  "pages/bento/integration.instance.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.source.inference.bento.page.tsx",
  "pages/bento/integration.instance.source.list.bento.page.tsx",
  "pages/bento/integration.instance.source.model.bento.page.tsx",
  "pages/bento/integration.instance.source.spec.bento.page.tsx",
  "pages/bento/integration.instance.system-info.bento.page.tsx",
  "pages/bento/integration.instance.wc.source.detail.bento.page.tsx",
  "pages/bento/integration.instance.wc.source.list.bento.page.tsx",
  "pages/integration.instance.assets.source.list.page.tsx",
  "pages/integration.instance.assets.source.page.tsx",
  "pages/integration.instance.catalog.source.list.page.tsx",
  "pages/integration.instance.catalog.source.page.tsx",
  "pages/integration.instance.coverage.page.tsx",
  "pages/integration.instance.db.source.list.page.tsx",
  "pages/integration.instance.db.source.page.tsx",
  "pages/integration.instance.double.check.page.tsx",
  "pages/integration.instance.eds.source.list.page.tsx",
  "pages/integration.instance.eds.source.page.tsx",
  "pages/integration.instance.indexing.manager.page.tsx",
  "pages/integration.instance.indexing.rules.list.page.tsx",
  "pages/integration.instance.indexing.rules.page.tsx",
  "pages/integration.instance.indexing.stats.page.tsx",
  "pages/integration.instance.insights.page.tsx",
  "pages/integration.instance.monitoring.item.page.tsx",
  "pages/integration.instance.monitoring.page.tsx",
  "pages/integration.instance.orphans.page.tsx",
  "pages/integration.instance.plugins.page.tsx",
  "pages/integration.instance.source.inference.page.tsx",
  "pages/integration.instance.source.list.page.tsx",
  "pages/integration.instance.source.model.page.tsx",
  "pages/integration.instance.source.page.tsx",
  "pages/integration.instance.source.spec.page.tsx",
  "pages/integration.instance.system-info.page.tsx",
  "pages/integration.instance.wc.source.list.page.tsx",
  "pages/integration.instance.wc.source.page.tsx",
]

describe("D35: the console census", () => {
  it("names every forked component, and nothing else", () => {
    const { components } = census()
    const forked = Object.fromEntries(
      Object.entries(components)
        .filter(([, c]) => c.kind === "fork")
        .map(([name, c]) => [name, c.collides]),
    )

    expect(forked, [
      "The fork census moved.",
      "A file in `+ received` declares a name the package exports â€” either shim it, or if the",
      "  variation is genuine, wrap the shared component instead of re-declaring its name.",
      "A file in `- expected` was shimmed: delete its line here in the same diff.",
    ].join("\n")).toEqual(FORKED)
  })

  it("says how many surfaces are on bento, by name", () => {
    const { pages } = census()
    const bento = Object.entries(pages)
      .filter(([, page]) => page.era === "bento")
      .map(([name]) => name)
      .sort()

    expect(bento, "A page imports the bento subpath and is not declared migrated â€” add it to"
      + " ON_BENTO. That import is what states a page's era.").toEqual([...ON_BENTO].sort())
  })

  it("is not measuring an empty tree", () => {
    // The one wrong answer this reader must never give quietly. A walk that found nothing
    // reports zero forks and zero console pages, which is indistinguishable from a finished
    // migration â€” and the census is the only thing that would have said otherwise.
    const { totals } = census()
    const onDisk = readdirSync(join(SRC, "pages")).filter((n) => n.endsWith(".page.tsx")).length

    expect(totals.pages).toBeGreaterThan(30)
    expect(totals.components).toBeGreaterThan(60)
    expect(onDisk).toBeGreaterThan(20)
    expect(totals.pages).toBe(totals.bento + totals.console)
    expect(totals.components).toBe(totals.shim + totals.fork + totals.own)
  })

  it("reads a design system that actually exports things", () => {
    // The failure the fallback reader had on its first run: `dist/index.d.ts` in the published
    // package is eight lines of `export * from`, so a reader that did not follow them found one
    // name â€” and one export means every component classifies as Dumont's own.
    const ds = designSystemExports()
    expect(ds.names.size).toBeGreaterThan(200)
    expect(ds.names.has("Table")).toBe(true)
    expect(ds.names.has("Card")).toBe(true)
  })
})

describe("D35: the classifier classifies", () => {
  const ds = designSystemExports()

  it("calls a one-line re-export a shim", () => {
    const shim = classify(
      'export { Card, CardContent } from "@viglet/viglet-design-system";', ds)
    expect(shim.kind).toBe("shim")
    expect(shim.names).toEqual(["Card", "CardContent"])
  })

  it("calls a local declaration of a package name a fork", () => {
    const fork = classify("export function Card() { return null }", ds)
    expect(fork.kind).toBe("fork")
    expect(fork.collides).toEqual(["Card"])
  })

  it("calls a name the package does not export Dumont's own", () => {
    const own = classify("export function DumontLogo() { return null }", ds)
    expect(own.kind).toBe("own")
    expect(own.collides).toEqual([])
  })

  it("does not count a commented-out declaration", () => {
    // A census of what renders must not be moved by what is commented out, or a fork can be
    // hidden from it by two slashes.
    expect(classify("// export function Card() {}", ds).kind).toBe("own")
  })
})

describe("D35: era is decided by the bento subpath", () => {
  const importing = (names: string) =>
    `import { ${names} } from "@viglet/viglet-design-system/bento"`

  it("marks a page that composes bento chrome migrated", () => {
    expect(isBento(importing("BentoHero"))).toBe(true)
  })

  it("leaves a page that only takes an era-neutral adapter on console", () => {
    // The adapter is the one export written to be used *from* a console page â€” it renders
    // console chrome by default. A page that takes it has adopted nothing yet, and counting it
    // as migrated would shrink the debt without moving a surface.
    expect(isBento(importing("AdaptiveSectionCard"))).toBe(false)
    expect(isBento(importing("AdaptiveSectionCard, BentoHero"))).toBe(true)
  })

  it("leaves a page with no bento import at all on console", () => {
    expect(isBento('import { Card } from "@/components/ui/card"')).toBe(false)
  })

  it("does not read a type-only import as adopting the layer", () => {
    /*
     * D46 â€” a type import is erased at compile time and renders nothing, so it says which shape a
     * module speaks in, not which era it belongs to. `nav.const.ts` takes the package's nav schema
     * that way and is read by the *console* sidebar; counting it as bento made every page that
     * reaches it â€” the console shell included â€” read as migrated.
     */
    expect(isBento('import type { BentoNavGroup } from "@viglet/viglet-design-system/bento"')).toBe(
      false,
    )
    // A value import alongside an inline type still counts: the page renders that component.
    expect(isBento(importing("BentoHero, type BentoTone"))).toBe(true)
  })

  it("does not read a comment that quotes the layer as adopting it", () => {
    /*
     * D89 — the mirror of the classifier's "does not count a commented-out declaration", which it
     * had and this reader did not. `BentoRoutes.tsx` spells `chrome="bento"` in the paragraph
     * explaining how a detail page migrates, and read bento-era off that sentence; only the page
     * walk's `*.page.tsx` filter kept the router out of the count.
     *
     * D88 is why the pair is worth holding together: the same hole in `isAdapter` read the two
     * header wrappers' own doc comments — which say they *used* to call `useSectionChrome` — as
     * the call, and left every page behind them on console after their branch had gone.
     */
    expect(isBento('// the console page under a `chrome="bento"` declaration')).toBe(false)
    expect(isBento(`/* migrating: <SectionCardChromeProvider chrome="bento"> */`)).toBe(false)
    expect(isBento(`// ${importing("BentoHero")}`)).toBe(false)
    // And the declaration itself still commits, which is the claim the stripping must not weaken.
    expect(isBento('<SectionCardChromeProvider chrome="bento">')).toBe(true)
  })
})

describe("D46: era follows a page's local imports", () => {
  it("counts the six source lists, which reach the layer through a shared wrapper", () => {
    /*
     * The six lists render the same shape and share one wrapper rather than repeating it six
     * times, so none of them imports the bento subpath directly. Judging a page by its own import
     * line alone counted every one as console-era and made the instrument report the migration
     * going backwards while it was going forwards.
     *
     * A page's era is the chrome it renders, and it renders whatever its own modules render.
     */
    const { pages } = census()
    const lists = Object.entries(pages).filter(([path]) => path.startsWith("pages/bento/"))

    // Counted against `ON_BENTO` rather than a literal, so a migration that adds a page here
    // updates one list instead of two — and a walk that found nothing still fails, which is the
    // only thing the number was ever guarding.
    expect(lists).toHaveLength(ON_BENTO.filter((path) => path.startsWith("pages/bento/")).length)
    for (const [path, page] of lists) {
      expect(page.era, `${path} should be bento-era through its wrapper`).toBe("bento")
    }
  })

  it("counts the chrome a page renders through its components, not just its own imports", () => {
    /*
     * D51 — the census warned every run that its `StickySaveBar` probe matched no page, and
     * offered two explanations: the last page carrying it migrated, or the probe went stale.
     * Neither was true. The indexing manager page rendered one, two files away, through
     * `integration.indexing.manager.form` — and the chrome scan only ever read the page file.
     *
     * The warning was accurate about *pages* and false about the *console*, which is the worst
     * kind: a reader learns to scroll past it, and the next probe that dies for the real reason
     * reads exactly the same.
     *
     * The subject is the login page since D88, because the indexing manager is bento-era now and
     * a bento page has no chrome list to check. Login renders `Card` two files away, through
     * `components/login/login-form`, and imports nothing console-era itself — so a reader of the
     * page file alone would report it rendering no console chrome at all.
     */
    const { pages, totals } = census()
    expect(pages["app/login/login.page.tsx"].chrome).toContain("Card")
    // And no probe is dead, which is what the script's own stderr guard checks on every run.
    for (const [id, hits] of Object.entries(totals.chrome)) {
      expect(hits, `the ${id} probe matches nothing — stale, or the console really has none`)
        .toBeGreaterThan(0)
    }
  })

  it("keeps the console shell on console, though it reaches a module that names bento types", () => {
    // The regression the type-only rule prevents: the console root imports the sidebar, which
    // imports the nav declaration, which imports the nav *schema* as types.
    const { pages } = census()
    expect(pages["app/console/console.root.page.tsx"].era).toBe("console")
  })
})
