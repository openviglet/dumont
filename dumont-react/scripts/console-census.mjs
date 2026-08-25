/*
 * D35 — the instrument Track I is measured with.
 *
 * The track opened with a sentence and no number: "the console adopts the shared bento layer",
 * across 39 pages and 88 components, with nothing saying which of them the shared layer could
 * carry. A migration scoped that way has no size and no end, and the tasks below D35 are mostly
 * deletions — a deletion with no baseline is indistinguishable from a regression.
 *
 * So this reads the source and prints two censuses:
 *
 *   PAGES       every `*.page.tsx`, and whether it renders console chrome or bento chrome.
 *   COMPONENTS  every `.tsx` under `src/components`, as one of three things.
 *
 * ## Why the component count cannot be a grep for imports
 *
 * Three quite different files live side by side under `src/components`:
 *
 *   shim  `ui/card.tsx` is one line — `export { Card, … } from "@viglet/viglet-design-system"`.
 *         Already adopted; the file exists so `@/components/ui/card` keeps resolving.
 *   fork  `ui/table.tsx` re-declares eight Table primitives the package already exports. This is
 *         the debt: two copies of one component, and the one here drifts.
 *   own   `dumont-logo.tsx`, the wizard steps, the source forms — Dumont's, and never moving.
 *
 * A grep for `@viglet/viglet-design-system` separates the first from the other two and stops. What
 * decides fork from own is whether the **name this file declares** is a name the package already
 * exports, which is a question only the package can answer — so the design system's own
 * `dist/exports.json` is the authority here, read from this console's `node_modules`.
 *
 * The first run is the argument for reading the package rather than trusting a reader: §XI.1 gave
 * `app-footer.tsx` as an example of something Dumont owns outright, and the package exports an
 * `AppFooter` whose markup is the same footer with the product name as a prop. Seven such files
 * were named `own` by a person and are forks by the package's own manifest.
 *
 * ## Usage
 *
 *   node scripts/console-census.mjs            # the numbers, the pages, and what is forked
 *   node scripts/console-census.mjs --all      # every component, `own` ones included
 *   node scripts/console-census.mjs --json     # the same, as data
 *
 * Exits non-zero when it cannot measure — no `exports.json`, or a walk that found no pages. A
 * census that quietly reports zero reads exactly like a finished migration, which is the one
 * answer it must never give by accident.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

const HERE = dirname(fileURLToPath(import.meta.url))
export const ROOT = join(HERE, "..")
export const SRC = join(ROOT, "src")
export const DS_ROOT = join(ROOT, "node_modules", "@viglet", "viglet-design-system")
export const EXPORTS_JSON = join(DS_ROOT, "dist", "exports.json")

/**
 * Codepoint order, stated rather than defaulted, and deliberately not `localeCompare`: this
 * listing is compared between runs and between machines, so a comparator that consults the host
 * locale would order the same census two ways.
 */
const byCodepoint = (a, b) => (a < b ? -1 : a > b ? 1 : 0)

/**
 * Exports that say nothing about a page's era. The bento subpath is what states which era a page
 * belongs to — except for the adapters, which exist precisely to be imported *from a console
 * page* and render console chrome by default. A page that takes one of these has adopted nothing
 * yet. Kept by name rather than by loosening the pattern: a page importing an adapter **and**
 * `BentoHero` is migrated, and this still says so.
 */
export const ERA_NEUTRAL = [
  "AdaptiveSectionCard", "AdaptivePageHeader", "AdaptivePanel", "AdaptiveEmptyState",
  "SectionCardChromeProvider", "useSectionChrome",
]

const BENTO_SUBPATH = "@viglet/viglet-design-system/bento"
const BENTO_CLAUSE = /import\s*(type\s+)?\{([^}]*)\}\s*from\s*["']@viglet\/viglet-design-system\/bento["']/g

/**
 * A page is bento-era when it imports the bento subpath as something other than an adapter. A
 * clause-less `import "…/bento…"` counts: it can only be there for a side effect the console era
 * does not have.
 *
 * A **type-only** import does not count. `import type { BentoNavGroup }` is erased at compile time
 * and renders nothing, so it says what shape a module speaks in, not which era it belongs to —
 * `nav.const.ts` takes the nav schema that way and is read by the console sidebar, which is as
 * console-era as a file gets.
 */
export function isBento(source) {
  /*
   * D47 — declaring the chrome is the strongest statement of era there is.
   *
   * `SectionCardChromeProvider` is era-neutral *as a component*, because it can declare either
   * era, and that is why it sits in the list above. The value is what commits: a page rendering
   * `chrome="bento"` puts everything beneath it in bento chrome, which is exactly how the six
   * source detail pages migrate without their 500-line forms being forked.
   */
  if (/chrome=["']bento["']/.test(source)) return true
  if (!source.includes(BENTO_SUBPATH)) return false
  const clauses = [...source.matchAll(BENTO_CLAUSE)]
  if (!clauses.length) return true
  return clauses.some(([, typeOnly, names]) => !typeOnly && names
    .split(",")
    .map((name) => name.trim().replace(/^type\s+/, "").split(/\s+as\s+/)[0].trim())
    .filter(Boolean)
    .some((name) => !ERA_NEUTRAL.includes(name)))
}

/** Local import specifiers — the alias this codebase uses, and the relative spellings. */
const LOCAL_IMPORT = /from\s*["'](@\/[^"']+|\.\.?\/[^"']+)["']/g
const RESOLVE_EXTENSIONS = [".tsx", ".ts", "/index.tsx", "/index.ts"]

/** Resolve one local specifier to a file under `src`, or null when it is not a module here. */
function resolveLocal(fromFile, specifier) {
  const base = specifier.startsWith("@/")
    ? join(SRC, specifier.slice(2))
    : join(dirname(fromFile), specifier)
  for (const ext of RESOLVE_EXTENSIONS) {
    const candidate = `${base}${ext}`
    if (existsSync(candidate)) return candidate
  }
  return existsSync(base) ? base : null
}

/**
 * Is this page bento-era, counting the chrome it renders **through a local module**?
 *
 * `isBento` asks whether a file imports the bento subpath itself, which is the right question of
 * one file and the wrong one of a page. A page that composes a local bento component renders bento
 * chrome as surely as one importing `BentoHero` directly, and the six source lists do exactly
 * that: they share one wrapper rather than repeating its shape six times. Reading only the page's
 * own imports counted every one of them as console-era, so the instrument said the migration had
 * gone backwards while it was going forwards.
 *
 * Bounded by `seen`, so a cycle terminates, and it only ever follows imports that resolve to a
 * file under `src` — a page's era is decided by this project's code, never by a dependency's.
 */
function isBentoThroughLocals(file, source, seen = new Set()) {
  if (seen.has(file)) return false
  seen.add(file)
  if (isBento(source)) return true
  for (const [, specifier] of source.matchAll(LOCAL_IMPORT)) {
    const target = resolveLocal(file, specifier)
    if (!target || seen.has(target)) continue
    const targetSource = readFileSync(target, "utf8")
    // An adapter renders whichever era it is placed in, so importing one states nothing.
    if (isAdapter(targetSource)) continue
    if (isBentoThroughLocals(target, targetSource, seen)) return true
  }
  return false
}

/**
 * Is this module an adapter — one that renders both eras and commits to neither?
 *
 * `useSectionChrome` is the whole test, and it is a property rather than a name: a module asking
 * which chrome it is in has already said it works in both. Dumont's `AdaptiveFormHeader` is one,
 * and it is why the six source *forms* are shared instead of forked — they are 500 lines of field
 * logic whose only era-specific part is the header.
 *
 * Without this, following imports made every page reaching such a form read as bento, including
 * the console originals that still render `StickyPageHeader`. The package's own `ERA_NEUTRAL`
 * list says the same thing about its adapters; this says it about a product's.
 */
function isAdapter(source) {
  return source.includes("useSectionChrome")
}

/**
 * Every source a page renders through — its own, plus the local modules it reaches.
 *
 * D51 — the chrome probes used to read the page file alone, and a page renders most of its chrome
 * through components. `StickySaveBar` was the one that made it visible: the census reported the
 * probe matching nothing and warned that it had gone stale, while the indexing manager page was
 * rendering one the whole time, two files away. The warning was true about pages and false about
 * the console, which is the worst kind — a reader learns to scroll past it, and the next probe
 * that goes stale for the real reason reads identically.
 *
 * Same walk the era check uses, and the same bound: local modules only, `seen` to terminate.
 */
function localClosure(file, source, seen = new Set()) {
  if (seen.has(file)) return []
  seen.add(file)
  const sources = [source]
  for (const [, specifier] of source.matchAll(LOCAL_IMPORT)) {
    const target = resolveLocal(file, specifier)
    if (!target || seen.has(target)) continue
    sources.push(...localClosure(target, readFileSync(target, "utf8"), seen))
  }
  return sources
}

/**
 * The console-era chrome this track is retiring, each probe naming what a bento page replaces.
 * Both spellings of every path: `@/components/…` and the relative `../…` this codebase also uses,
 * because a probe that reads only the alias goes blind to a third of its own subject.
 *
 * Adding a probe is how newly-noticed console chrome enters the census. Removing one is only ever
 * correct when every page carrying it was migrated — a probe that matches nothing is a layer this
 * reader has gone blind to while its number went down, so `--json` reports the count per probe and
 * the suite asserts none of them is zero.
 */
export const CONSOLE_CHROME = [
  ["PageHeader", /["'](?:@\/components|\.{1,2})\/page-header["']/],
  ["SubPageHeader", /["'](?:@\/components|\.{1,2})\/sub\.page\.header["']/],
  ["Card", /["'](?:@\/components|\.{1,2})\/(?:components\/)?ui\/card["']/],
  ["Sidebar", /["'](?:@\/components|\.{1,2})\/(?:(?:components\/)?ui\/sidebar|app-sidebar)["']/],
  ["StickySaveBar", /["'](?:@\/components|\.{1,2})\/(?:components\/)?ui\/sticky-save-bar["']/],
]

/**
 * Strip comments, so a commented-out import cannot contribute to a census of what renders. String
 * literals are left alone on purpose: the import specifier *is* a string, and it is the thing
 * being read.
 */
function code(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/(^|[^:])\/\/[^\n]*/g, "$1 ")
}

function walk(dir, accept, out = []) {
  if (!existsSync(dir)) return out
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) walk(path, accept, out)
    else if (accept(entry.name)) out.push(path)
  }
  return out
}

const key = (file) => relative(SRC, file).replaceAll("\\", "/")

/**
 * The design system's own list of what it exports, by name, across every entry point.
 *
 * Two readings, and the report says which one it got. `dist/exports.json` is the package's own
 * manifest and the better answer — it is emitted by the package's `scripts/emit-exports.mjs` and
 * names the specifier each export belongs to. But the **published** package does not ship it:
 * `2026.3.2` from the registry has no such file, and only a local `copy-ds.cmd` build does. A
 * census that needed it would work on one machine and refuse on CI, which for an instrument whose
 * whole job is to be re-run is the same as not existing.
 *
 * So the fallback reads the `.d.ts` each entry point in the package's `exports` map declares and
 * takes the names out of its `export { … }` clause. That is the same set for every purpose this
 * census has; what it loses is nothing the classifier consults.
 */
export function designSystemExports(path = EXPORTS_JSON, dsRoot = DS_ROOT) {
  const reading = existsSync(path) ? fromManifest(path) : fromDeclarations(dsRoot)

  /*
   * A floor, not a zero check, and the first run of the fallback is why. `dist/index.d.ts` in the
   * published package is eight lines of `export * from './components'`, so a reader that took
   * only the names it spells found exactly **one** export — and one is not zero, so a `size === 0`
   * guard passed it and the census reported the console had no forks at all. The package's root
   * barrel alone carries 221 names; anything near single digits means the reader broke, not that
   * the debt was paid.
   */
  if (reading.names.size < PLAUSIBLE_EXPORTS) {
    throw new Error(
      `read only ${reading.names.size} exports from ${dsRoot} (via ${reading.source})\n`
      + `  The package's root barrel alone carries far more than ${PLAUSIBLE_EXPORTS}, so this is\n`
      + "  a reader that stopped working, not a package that shrank. Every component here would\n"
      + "  classify as Dumont's own and the census would report a finished migration.\n"
      + "  Re-run `pnpm install` at the repo root, or `copy-ds.cmd` for an unpublished build.",
    )
  }
  return reading
}

/** Below this, the reading is broken rather than small. See the guard above for the incident. */
const PLAUSIBLE_EXPORTS = 50

/** The package's own emitted manifest: names and the specifier each belongs to. */
function fromManifest(path) {
  const manifest = JSON.parse(readFileSync(path, "utf8"))
  const names = new Map()
  for (const entry of Object.values(manifest.entries ?? {})) {
    for (const name of entry.values ?? []) {
      if (!names.has(name)) names.set(name, entry.specifier)
    }
  }
  return { version: manifest.version ?? "unknown", names, source: "exports.json" }
}

/** Every entry point's `.d.ts`, as the published package ships it. */
function fromDeclarations(dsRoot) {
  const manifestPath = join(dsRoot, "package.json")
  if (!existsSync(manifestPath)) {
    throw new Error(
      `no design system at ${dsRoot}\n`
      + "  The census classifies a component by asking whether the package already exports its\n"
      + "  name, so without the package it cannot tell a fork from something Dumont owns — and a\n"
      + "  census that reports no forks reads exactly like a finished migration.\n"
      + "  Run `pnpm install` at the repo root, or `copy-ds.cmd` for an unpublished build.",
    )
  }
  const pkg = JSON.parse(readFileSync(manifestPath, "utf8"))
  const names = new Map()
  for (const [subpath, entry] of Object.entries(pkg.exports ?? {})) {
    const declaration = declarationFor(dsRoot, entry)
    if (!declaration) continue
    const specifier = subpath === "."
      ? "@viglet/viglet-design-system"
      : `@viglet/viglet-design-system${subpath.slice(1)}`
    for (const name of declaredNames(declaration)) {
      if (!names.has(name)) names.set(name, specifier)
    }
  }
  return { version: pkg.version ?? "unknown", names, source: "type declarations" }
}

function declarationFor(dsRoot, entry) {
  const types = typeof entry === "object" && entry !== null ? entry.types : null
  if (!types) return null
  const declaration = join(dsRoot, types)
  return existsSync(declaration) ? declaration : null
}

/**
 * Every name one entry point's declarations expose, following `export * from` as far as it goes.
 *
 * The published package does not bundle its types: `dist/index.d.ts` is eight lines, six of them
 * `export * from './components'` and friends. A reader that took only the names spelled in that
 * file found **one** export and reported the console had zero forks — a finished migration, by
 * arithmetic on an empty set. So the star re-exports are followed, and `seen` bounds the walk on
 * a graph that is allowed to have cycles.
 */
function declaredNames(declaration, seen = new Set()) {
  const resolved = existsSync(declaration) ? declaration : null
  if (!resolved || seen.has(resolved)) return []
  seen.add(resolved)

  const source = readFileSync(resolved, "utf8")
  const out = []

  for (const [, clause] of source.matchAll(DTS_EXPORT)) {
    for (const raw of clause.split(",")) {
      const name = raw.trim().replace(/^type\s+/, "").split(/\s+as\s+/).pop()?.trim()
      if (name) out.push(name)
    }
  }
  for (const [, name] of source.matchAll(DTS_DECLARED)) out.push(name)
  for (const [, specifier] of source.matchAll(DTS_STAR)) {
    const next = resolveDeclaration(dirname(resolved), specifier)
    if (next) out.push(...declaredNames(next, seen))
  }
  return out
}

/** `./components` is a directory in the published layout and a file in the bundled one. */
function resolveDeclaration(from, specifier) {
  if (!specifier.startsWith(".")) return null
  const base = join(from, specifier.replace(/\.js$/, ""))
  for (const candidate of [`${base}.d.ts`, join(base, "index.d.ts")]) {
    if (existsSync(candidate)) return candidate
  }
  return null
}

/** `export { A, B as C };` — the whole surface in one clause, bundled or forwarded. */
const DTS_EXPORT = /export\s*(?:type\s+)?\{([^}]*)\}/g
/** `export declare function X`, `export declare const X`, `export type X =` — named in place. */
const DTS_DECLARED = /export\s+(?:declare\s+)?(?:abstract\s+)?(?:function|const|let|var|class|type|interface|enum)\s+([A-Za-z_$][\w$]*)/g
/** `export * from './components'` — the published layout's whole content, one level down. */
const DTS_STAR = /export\s+\*\s+from\s*["']([^"']+)["']/g

// `export { A, B as C } from "…"` — a shim, and the specifier it forwards to.
const REEXPORT = /export\s*(?:type\s+)?\{([^}]*)\}\s*from\s*["']([^"']+)["']/g
// `export function X`, `export const X`, `export class X`, `export type X`, `export interface X`.
const DECLARED = /export\s+(?:default\s+)?(?:async\s+)?(?:function|const|let|var|class|type|interface|enum)\s+([A-Za-z_$][\w$]*)/g
// `export { X }` with no `from` — a local declaration exported at the bottom of the file.
const LOCAL_EXPORT = /export\s*(?:type\s+)?\{([^}]*)\}\s*(?!from)[;\n]/g

const clause = (names) => names
  .split(",")
  .map((n) => n.trim().replace(/^type\s+/, ""))
  .filter(Boolean)
  .map((n) => {
    const [from, to] = n.split(/\s+as\s+/).map((s) => s.trim())
    return { source: from, exported: to ?? from }
  })

/**
 * One component file, as one of `shim`, `fork` or `own`.
 *
 * `fork` wins over `own` when a file declares both — `ui/table.tsx` declaring eight primitives the
 * package exports is a fork even if it also declares a ninth that is Dumont's, because the eight
 * are what drift.
 */
export function classify(source, ds) {
  const body = code(source)
  const forwarded = []
  for (const [, names, specifier] of body.matchAll(REEXPORT)) {
    if (!specifier.startsWith("@viglet/viglet-design-system")) continue
    for (const { exported } of clause(names)) forwarded.push(exported)
  }
  const declared = new Set()
  for (const [, name] of body.matchAll(DECLARED)) declared.add(name)
  for (const [, names] of body.matchAll(LOCAL_EXPORT)) {
    for (const { exported } of clause(names)) declared.add(exported)
  }
  // A name this file both forwards and re-declares is forwarded, not declared: `export { X } from`
  // matches LOCAL_EXPORT's tail as well when the two sit on one line.
  for (const name of forwarded) declared.delete(name)

  const collides = [...declared].filter((name) => ds.names.has(name)).sort(byCodepoint)
  if (declared.size === 0 && forwarded.length > 0) {
    return { kind: "shim", names: forwarded.sort(byCodepoint), collides: [] }
  }
  if (collides.length > 0) {
    return { kind: "fork", names: [...declared].sort(byCodepoint), collides }
  }
  return { kind: "own", names: [...declared].sort(byCodepoint), collides: [] }
}

export function census() {
  const ds = designSystemExports()

  const pages = {}
  for (const file of walk(SRC, (n) => n.endsWith(".page.tsx")).sort(byCodepoint)) {
    const source = readFileSync(file, "utf8")
    if (isBentoThroughLocals(file, source)) {
      pages[key(file)] = { era: "bento", chrome: [] }
      continue
    }
    // The chrome a page renders, counting what its own components render (D51).
    const rendered = localClosure(file, source).map(code).join("\n")
    pages[key(file)] = {
      era: "console",
      chrome: CONSOLE_CHROME.filter(([, probe]) => probe.test(rendered)).map(([id]) => id),
    }
  }

  const components = {}
  const componentRoot = join(SRC, "components")
  for (const file of walk(componentRoot, (n) => n.endsWith(".tsx")).sort(byCodepoint)) {
    components[key(file)] = classify(readFileSync(file, "utf8"), ds)
  }

  const chrome = {}
  for (const [id] of CONSOLE_CHROME) chrome[id] = 0
  for (const page of Object.values(pages)) {
    for (const id of page.chrome) chrome[id] += 1
  }

  const kinds = { shim: 0, fork: 0, own: 0 }
  for (const c of Object.values(components)) kinds[c.kind] += 1

  return {
    designSystem: ds.version,
    read: ds.source,
    exports: ds.names.size,
    pages,
    components,
    totals: {
      pages: Object.keys(pages).length,
      bento: Object.values(pages).filter((p) => p.era === "bento").length,
      console: Object.values(pages).filter((p) => p.era === "console").length,
      noChrome: Object.values(pages).filter((p) => p.era === "console" && !p.chrome.length).length,
      chrome,
      components: Object.keys(components).length,
      ...kinds,
      forked: Object.values(components).reduce((n, c) => n + c.collides.length, 0),
    },
  }
}

function report(result, all = false) {
  const t = result.totals
  const lines = []
  lines.push(
    `Dumont console census — design system ${result.designSystem}, `
    + `${result.exports} exports read from ${result.read}`,
  )
  lines.push("")
  lines.push(`PAGES  ${t.pages} — ${t.bento} bento, ${t.console} console`)
  for (const [name, page] of Object.entries(result.pages)) {
    const marks = page.era === "bento" ? "bento" : (page.chrome.join(" ") || "—")
    lines.push(`  ${page.era.padEnd(7)}  ${marks.padEnd(34)}  ${name}`)
  }
  lines.push(`  chrome still rendered: ${
    Object.entries(t.chrome).sort(byCodepoint).map(([k, v]) => `${k} ${v}`).join(", ")
  }; no chrome ${t.noChrome}`)
  lines.push("")
  lines.push(`COMPONENTS  ${t.components} — ${t.shim} shim, ${t.fork} fork, ${t.own} own`)
  for (const [name, c] of Object.entries(result.components)) {
    if (c.kind === "own" && !all) continue
    let detail
    if (c.kind === "fork") detail = c.collides.join(" ")
    else if (c.kind === "shim") detail = `${c.names.length} re-exported`
    else detail = `${c.names.length} declared`
    lines.push(`  ${c.kind.padEnd(7)}  ${detail.padEnd(34)}  ${name}`)
  }
  lines.push(`  ${t.forked} name(s) declared here that the package already exports`)
  lines.push("")
  lines.push(
    `Left to move: ${t.console} of ${t.pages} pages on console chrome, `
    + `${t.fork} of ${t.components} components forked from the package.`,
  )
  return lines.join("\n")
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let result
  try {
    result = census()
  } catch (error) {
    console.error(`console-census: ${error.message}`)
    process.exit(2)
  }

  // A walk that found nothing reports a finished migration. It is the one wrong answer this
  // script must not give quietly, so it is an exit code rather than a zero in the report.
  if (result.totals.pages === 0 || result.totals.components === 0) {
    console.error(
      `console-census: found ${result.totals.pages} pages and ${result.totals.components} `
      + `components under ${SRC}. The tree moved and this reader is measuring an empty `
      + "directory, which would read as a finished migration.",
    )
    process.exit(2)
  }

  // A probe that matches nothing is ambiguous in the expensive direction: either the last page
  // carrying that chrome was migrated, or its import path was renamed and this reader has gone
  // blind to a whole layer while its number went down. Said out loud, on stderr, every run.
  for (const [id, hits] of Object.entries(result.totals.chrome)) {
    if (hits === 0) {
      console.error(
        `console-census: the ${id} probe matches no page. Either every page carrying it was `
        + "migrated — then delete the probe in the same diff — or its import path moved and this "
        + "census is no longer counting it.",
      )
    }
  }

  if (process.argv.includes("--json")) console.log(JSON.stringify(result, null, 2))
  else console.log(report(result, process.argv.includes("--all")))
}
