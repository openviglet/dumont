/*
 * D42 — types for the census script, so the suite that holds it is type-checked too.
 *
 * `console-census.mjs` stays plain JavaScript on purpose: it is a script anyone can run with
 * `node scripts/console-census.mjs`, with no build step between the source and the number. That
 * costs it declarations, and without these the test importing it infers `any` for every field —
 * which is how a test asserting on `c.collides` keeps compiling after the field is renamed.
 */
export type ComponentKind = "shim" | "fork" | "own"

export interface ComponentClassification {
  /** `shim` re-exports the package, `fork` re-declares a name it exports, `own` is Dumont's. */
  kind: ComponentKind
  /** What the file exports: forwarded names for a shim, declared names otherwise. */
  names: string[]
  /** The declared names the package already exports. Empty unless `kind` is `fork`. */
  collides: string[]
}

export interface PageClassification {
  era: "bento" | "console"
  /** Which console-era chrome the page still composes. Empty for a bento page. */
  chrome: string[]
}

export interface CensusTotals {
  pages: number
  bento: number
  console: number
  noChrome: number
  chrome: Record<string, number>
  components: number
  shim: number
  fork: number
  own: number
  /** How many declared names across all files collide with a package export. */
  forked: number
}

export interface Census {
  designSystem: string
  /** Which reading answered: the package's manifest, or its type declarations. */
  read: "exports.json" | "type declarations"
  exports: number
  pages: Record<string, PageClassification>
  components: Record<string, ComponentClassification>
  totals: CensusTotals
}

export interface DesignSystemExports {
  version: string
  /** Export name to the specifier it is exported from. */
  names: Map<string, string>
  read?: string
  source: "exports.json" | "type declarations"
}

export declare const ROOT: string
export declare const SRC: string
export declare const DS_ROOT: string
export declare const EXPORTS_JSON: string
export declare const ERA_NEUTRAL: string[]
export declare const CONSOLE_CHROME: Array<[string, RegExp]>

export declare function isBento(source: string): boolean
export declare function classify(source: string, ds: DesignSystemExports): ComponentClassification
export declare function designSystemExports(path?: string, dsRoot?: string): DesignSystemExports
export declare function census(): Census
