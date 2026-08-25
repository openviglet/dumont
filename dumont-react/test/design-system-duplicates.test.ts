/*
 * D41 — the gate is only a gate if a new duplicate actually fails it.
 *
 * Nineteen names were declared in this console and exported by the design system under the same
 * names, and nothing anywhere failed because of it. They were found by pointing the package's own
 * export-manifest lint at this repository — which is to say, by someone looking. Turing is the
 * proof of what that costs: an entire design language grew inside the product while a design
 * system sat next to it exporting the same chrome, because a local copy was always cheaper than
 * finding the shared one.
 *
 * `viglet-ds-check-duplicates` runs in CI (`.github/workflows/design-system.yml`). What this file
 * asserts is the part CI cannot: that the gate is wired to *this* repository's source, that the
 * eleven deliberate collisions are annotated rather than ignored wholesale, and — the case that
 * matters — that a component newly re-declared here is caught.
 *
 * The planted duplicate is the whole test. A suite that only ran the lint over a clean tree would
 * pass just as happily against a lint that scanned nothing at all, which is exactly how the
 * `--manifest` argument bug (VDS78) survived its own test suite.
 */
import { execFileSync } from "node:child_process"
import { mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, resolve } from "node:path"
import { afterAll, describe, expect, it } from "vitest"

/** Repository root for the frontend workspace — the lint resolves the package from here. */
const APP_ROOT = resolve(__dirname, "..")

interface Finding {
  file: string
  line: number
  name: string
  replacement: string
}

/**
 * Run the lint over `roots` and return its findings *and its exit code*.
 *
 * The non-zero exit is the whole contract — it is what fails a build — so it is captured rather
 * than avoided with `--warn`. `execFileSync` throws on it and carries the stdout along, which is
 * where the JSON is.
 */
function check(...roots: string[]): { scanned: number; findings: Finding[]; exitCode: number } {
  const args = [
    resolve(APP_ROOT, "node_modules/@viglet/viglet-design-system/scripts/check-duplicates.mjs"),
    ...roots,
    "--json",
  ]
  try {
    const out = execFileSync(process.execPath, args, { cwd: APP_ROOT, encoding: "utf8" })
    return { ...JSON.parse(out), exitCode: 0 }
  } catch (error) {
    const failure = error as { status?: number; stdout?: string }
    if (typeof failure.stdout !== "string" || failure.stdout.length === 0) throw error
    return { ...JSON.parse(failure.stdout), exitCode: failure.status ?? 1 }
  }
}

const planted: string[] = []

afterAll(() => {
  for (const dir of planted) rmSync(dir, { recursive: true, force: true })
})

describe("D41: a component re-declared here fails the gate", () => {
  it("catches a newly declared copy of a design system export", () => {
    const dir = mkdtempSync(join(tmpdir(), "dumont-ds-dup-"))
    planted.push(dir)
    // `GradientButton` is a real export of the package and is *not* declared anywhere in this
    // console — it is one of the shims. Planting it is the exact mistake the gate exists for.
    writeFileSync(
      join(dir, "planted.tsx"),
      "export function GradientButton() {\n  return null\n}\n",
      "utf8",
    )

    const { findings, exitCode } = check(dir)
    const hit = findings.find((f) => f.name === "GradientButton")

    expect(hit, "a fresh duplicate went unreported").toBeDefined()
    // Reporting it and exiting 0 would be a gate that never fails a build.
    expect(exitCode).toBe(1)
    // Reporting it is not the useful half: the finding has to end in the line the author would
    // have written instead, or they have to go looking for it a second time.
    expect(hit!.replacement).toContain("@viglet/viglet-design-system")
    expect(hit!.replacement).toContain("GradientButton")
  })

  it("honours an annotation, and only for the name it names", () => {
    const dir = mkdtempSync(join(tmpdir(), "dumont-ds-allow-"))
    planted.push(dir)
    writeFileSync(
      join(dir, "planted.tsx"),
      [
        "// viglet-ds-allow-duplicate GradientButton -- deliberate, for this test",
        "export function GradientButton() {",
        "  return null",
        "}",
        "export function Toaster() {",
        "  return null",
        "}",
      ].join("\n"),
      "utf8",
    )

    const { findings } = check(dir)

    // The exemption belongs to a name, not to the file. A file-level skip would have let the
    // second declaration through, which is the case the gate exists to catch.
    expect(findings.map((f) => f.name)).toEqual(["Toaster"])
  })
})

describe("D41: this console's own source is clean", () => {
  it("declares no design system export without a reason", () => {
    const { scanned, findings, exitCode } = check("src")

    // If this fails, the message is the fix — each finding carries the import that replaces it.
    expect(findings, findings.map((f) => `${f.file}:${f.line} ${f.name}`).join("\n")).toEqual([])
    expect(exitCode).toBe(0)

    // And it actually scanned something. A misrouted root reports zero findings and exits 0,
    // which reads identically to a clean tree.
    expect(scanned).toBeGreaterThan(100)
  })
})
