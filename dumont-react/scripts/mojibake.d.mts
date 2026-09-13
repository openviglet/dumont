/*
 * D90 — types for the encoding check, so the suite that holds it is type-checked too.
 *
 * Same arrangement as `console-census.d.mts`, and for the same reason: `mojibake.mjs` stays plain
 * JavaScript because it is a script anyone runs with `node`, and without these the test importing
 * it infers `any` for every field.
 */

/** One double-encoded run: where it starts, what the file holds, and what was written. */
export interface MojibakeRun {
  index: number
  found: string
  written: string
}

/** One file carrying double-encoded text, path relative to the package root. */
export interface MojibakeFile {
  file: string
  runs: MojibakeRun[]
}

export declare const ROOT: string
export declare const ROOTS: string[]

export declare function repairRun(run: string): string | null
export declare function findMojibake(text: string): MojibakeRun[]
export declare function repair(text: string): string
export declare function sources(root?: string, roots?: string[]): string[]
export declare function scan(root?: string, roots?: string[]): MojibakeFile[]
