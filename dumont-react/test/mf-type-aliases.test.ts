/*
 * D52 — the emitted declarations must be readable by someone who is not this repository.
 *
 * D49 got the four remotes to emit types. They still taught a consumer nothing: TypeScript
 * preserves the specifier the source wrote, so every declaration reached the host importing
 * through `@/`, a path alias only this project maps. Fifty-six of them, across forty-eight files.
 *
 * The rewrite runs in `compile:build`. What is asserted here is the property, not the run: no
 * alias survives in the shipped tree, and none survives in the **zip**, which is the artefact a
 * host actually downloads and was written by the generator before the rewrite touched anything.
 *
 * Skipped when the bundle is absent, so `pnpm test` on a fresh clone does not fail for the want
 * of a build. `check-mf-types.mjs` is what fails a build that produced nothing at all.
 */
import AdmZip from "adm-zip"
import { existsSync, readFileSync, readdirSync } from "node:fs"
import { join, resolve } from "node:path"
import { describe, expect, it } from "vitest"

const APP = resolve(__dirname, "..")
const TYPES_DIR = resolve(APP, "mf-types/@mf-types")
const ZIP = resolve(APP, "mf-types/@mf-types.zip")

/** `from "@/…"` / `import "@/…"` — the alias no consumer maps. */
const ALIAS = /(["'])@\/[^"']+\1/

const built = existsSync(TYPES_DIR)

describe.skipIf(!built)("D52: the shipped declarations resolve outside this repository", () => {
  it("leaves no path alias in any declaration", () => {
    const offenders = readdirSync(TYPES_DIR, { recursive: true })
      .map(String)
      .filter((name) => name.endsWith(".d.ts"))
      .filter((name) => ALIAS.test(readFileSync(join(TYPES_DIR, name), "utf8")))

    expect(offenders, offenders.join("\n")).toEqual([])
  })

  it("rewrites to a specifier that points inside the bundle", () => {
    // A rewrite that produced a path escaping the bundle would satisfy the rule above and still
    // resolve to nothing. `manifest` is the one entry that re-exports across the tree.
    const manifest = readFileSync(join(TYPES_DIR, "compiled-types/manifest.d.ts"), "utf8")
    expect(manifest).toContain('from "./app/nav.const"')
  })

  it("carries no extension a consumer cannot resolve", () => {
    /*
     * A few sources import `@/models/sn/sn-site.model.ts` with the extension spelled out, which
     * only resolves under `allowImportingTsExtensions` — an option a consumer has no reason to
     * set. The rewrite strips it.
     */
    const service = readFileSync(
      join(TYPES_DIR, "compiled-types/services/sn/sn.service.d.ts"),
      "utf8",
    )
    expect(service).not.toMatch(/from\s+["'][^"']+\.ts["']/)
  })
})

describe.skipIf(!existsSync(ZIP))("D52: the zip a host downloads agrees with the tree", () => {
  it("contains no path alias either", () => {
    /*
     * The half that is easy to miss. The generator writes the zip before the rewrite runs, so a
     * fix applied only to the folder leaves the artefact a consumer actually fetches unchanged —
     * green build, corrected files on disk, and the same broken types over the wire.
     */
    const offenders = new AdmZip(ZIP)
      .getEntries()
      .filter((entry) => !entry.isDirectory && entry.entryName.endsWith(".d.ts"))
      .filter((entry) => ALIAS.test(entry.getData().toString("utf8")))
      .map((entry) => entry.entryName)

    expect(offenders, offenders.join("\n")).toEqual([])
  })

  it("is not empty", () => {
    // A repack that wrote nothing would pass the assertion above for the wrong reason.
    const declarations = new AdmZip(ZIP)
      .getEntries()
      .filter((entry) => entry.entryName.endsWith(".d.ts"))
    expect(declarations.length).toBeGreaterThan(100)
  })
})
