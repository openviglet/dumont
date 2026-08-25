/*
 * D42 — the test that holds D35's instrument.
 *
 * The census is a script anyone can run, which makes it a number anyone can also stop running.
 * This is what re-runs it: the seven forks are named here one by one, so retiring one is a diff
 * somebody reviews, and adding one fails by name rather than moving a total.
 *
 * ## Why a named list and not a ceiling
 *
 * A ceiling — "no more than seven forked components" — lets one migration pay for the next: shim
 * `page.tsx`, and the budget quietly funds leaving `grid.list.tsx` forked for ever. The map below
 * names every one, so the diff that removes a fork also removes its line, and the diff that adds
 * a component re-declaring a package export has to write it in.
 *
 * ## What is not asserted
 *
 * Not the page-by-page chrome map. Thirty-nine entries would be a wall nobody reads, and the
 * chrome counts below carry the same signal in a form a person can check at a glance. What the
 * page half asserts is the falling number the track is actually about — how many surfaces are on
 * bento — plus the guards that keep the reader honest when that number is zero.
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
 * Surfaces that render bento chrome. **Expected to stay empty until D37 builds the route tree**,
 * and to grow by exactly one line per migrated surface after that. The count, not the names, is
 * what the track is measured by — but the names are what make each step reviewable.
 */
const ON_BENTO: string[] = []

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
      "A file in `+ received` declares a name the package exports — either shim it, or if the",
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

    expect(bento, "A page imports the bento subpath and is not declared migrated — add it to"
      + " ON_BENTO. That import is what states a page's era.").toEqual([...ON_BENTO].sort())
  })

  it("is not measuring an empty tree", () => {
    // The one wrong answer this reader must never give quietly. A walk that found nothing
    // reports zero forks and zero console pages, which is indistinguishable from a finished
    // migration — and the census is the only thing that would have said otherwise.
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
    // name — and one export means every component classifies as Dumont's own.
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
    // The adapter is the one export written to be used *from* a console page — it renders
    // console chrome by default. A page that takes it has adopted nothing yet, and counting it
    // as migrated would shrink the debt without moving a surface.
    expect(isBento(importing("AdaptiveSectionCard"))).toBe(false)
    expect(isBento(importing("AdaptiveSectionCard, BentoHero"))).toBe(true)
  })

  it("leaves a page with no bento import at all on console", () => {
    expect(isBento('import { Card } from "@/components/ui/card"')).toBe(false)
  })
})
