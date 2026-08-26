/*
 * D48 â€” the lint that had never run, and the shape that keeps it running.
 *
 * ESLint, typescript-eslint and the two React plugins were devDependencies here all along, and
 * `pnpm run lint` exited non-zero with "couldn't find an eslint.config.js" â€” a message that reads
 * like a broken command rather than an absent gate, which is why it survived.
 *
 * Turning it on found 105 problems in a codebase that had never been linted. Ten were fixed at
 * once; two classes were structural and were carried as **named files** rather than a ceiling —
 * a ceiling lets one change pay for another, while a named list means a new violation fails by
 * name and clearing a file is a diff somebody reviews.
 *
 * D54 and D53 then emptied both lists, so nothing is excused today and both rules are errors
 * across the console. That is what this file now holds: the severity, in the files that used to
 * be exempt. Linting the tree only proves the config parses — an exemption trimmed to nothing
 * looks exactly like one that was deleted until something checks which it was.
 */
import { execFileSync } from "node:child_process"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const APP_ROOT = resolve(__dirname, "..")

/** ESLint's numeric severity for an error. Nothing is a warning here any more (D53, D54). */
const ERROR = 2

interface LintMessage {
  ruleId: string | null
  severity: number
}

/**
 * Lint `source` **as if** it were the file at `filename`, without writing anything.
 *
 * `--stdin-filename` is what makes the config's `files` patterns apply, which is the only way to
 * ask "would this be an error *here*" â€” the question the named lists exist to answer differently
 * per file.
 */
function lintAs(filename: string, source: string): LintMessage[] {
  // ESLint's own entry point rather than `npx`: a `.cmd` shim cannot be spawned without a shell
  // on Windows, and going through one would put quoting between this test and its input.
  const args = [
    resolve(APP_ROOT, "node_modules/eslint/bin/eslint.js"),
    "--stdin",
    "--stdin-filename",
    filename,
    "-f",
    "json",
  ]
  const options = {
    cwd: APP_ROOT,
    input: source,
    encoding: "utf8" as const,
    // stderr ignored: ESLint's own progress noise is not what this asserts on.
    stdio: ["pipe", "pipe", "ignore"] as ("pipe" | "ignore")[],
  }

  // A non-zero exit *is* the gate firing, and the JSON still comes back on stdout â€” so the throw
  // is caught rather than avoided with `--max-warnings`, which would hide the distinction this
  // file exists to assert.
  let out: string
  try {
    out = execFileSync(process.execPath, args, options)
  } catch (error) {
    const failure = error as { stdout?: string }
    if (typeof failure.stdout !== "string" || failure.stdout.length === 0) throw error
    out = failure.stdout
  }
  return JSON.parse(out).flatMap((result: { messages: LintMessage[] }) => result.messages)
}

const severityOf = (messages: LintMessage[], ruleId: string) =>
  messages.find((message) => message.ruleId === ruleId)?.severity

describe("D48: the config is wired to this project", () => {
  it("resolves, and applies the TypeScript and React rules", () => {
    // Before D48 this threw: there was no config anywhere in the repository.
    const messages = lintAs("src/planted.tsx", "export const value: any = 1\n")
    expect(messages.length).toBeGreaterThan(0)
  })
})

describe("D48/D53: the inherited debt was named, then paid off", () => {
  /** A `setState` run synchronously in an effect body. */
  const setsStateInEffect = [
    "import { useEffect, useState } from 'react'",
    "export function Planted() {",
    "  const [value, setValue] = useState(0)",
    "  useEffect(() => { setValue(1) }, [])",
    "  return value",
    "}",
  ].join("\n")

  it("fails any file for a dependency array that does not list what it reads", () => {
    /*
     * D56 — promoted from the warning it ships as. A warning is what let twenty-one of these
     * accumulate, and completing them uncovered a list page whose service was built inline every
     * render: an honest array there would have refetched forever.
     */
    const lyingArray = [
      "import { useEffect, useState } from 'react'",
      "export function Planted({ url }: { url: string }) {",
      "  const [v, setV] = useState('')",
      "  useEffect(() => { void (async () => setV(url))() }, [])",
      "  return v",
      "}",
    ].join("\n")
    expect(severityOf(lintAs("src/planted.tsx", lyingArray), "react-hooks/exhaustive-deps"))
      .toBe(ERROR)
  })

  it("fails any file for a synchronous setState in an effect", () => {
    // The half that makes this a gate rather than a record: nothing is excused any more, so the
    // next one written anywhere stops a build.
    expect(severityOf(lintAs("src/planted.tsx", setsStateInEffect), "react-hooks/set-state-in-effect"))
      .toBe(ERROR)
  })

  it("holds every file to both rules, now that neither list survives", () => {
    /*
     * D54 emptied the `no-explicit-any` list and D53 emptied `set-state-in-effect` - twenty-five
     * violations across twenty-three files. Both rules are errors across the console now, and both
     * lists were deleted rather than kept at zero.
     *
     * Asserted on the files that used to be excused, because that is where a leftover exemption
     * would still be sitting: a list trimmed to nothing looks identical to one that is gone until
     * something checks.
     */
    const wasExcused: [string, string, string][] = [
      ["src/hooks/use-grid-adapter.ts", "export const value: any = 1\n", "@typescript-eslint/no-explicit-any"],
      ["src/components/dicebear-avatar-picker.tsx", setsStateInEffect, "react-hooks/set-state-in-effect"],
      ["src/pages/integration.instance.monitoring.item.page.tsx", setsStateInEffect, "react-hooks/set-state-in-effect"],
      ["src/components/rag-status-badge.tsx", setsStateInEffect, "react-hooks/set-state-in-effect"],
    ]

    for (const [file, source, rule] of wasExcused) {
      expect(severityOf(lintAs(file, source), rule), `${rule} in ${file}`).toBe(ERROR)
    }
    // Four ESLint processes, one per file. Slow enough to trip the default timeout when the rest
    // of the suite is running beside it, which is a busy machine rather than a broken gate.
  }, 60_000)
})

describe("D48: the rules a type-check cannot see", () => {
  it("catches a hook called conditionally", () => {
    /*
     * The reason the plugins were worth installing. `tsc` is perfectly happy with this, and it
     * breaks at runtime the first time the branch changes between renders.
     */
    const messages = lintAs(
      "src/planted.tsx",
      [
        "import { useState } from 'react'",
        "export function Broken({ on }: { on: boolean }) {",
        "  if (on) { const [v] = useState(0); return v }",
        "  return null",
        "}",
      ].join("\n"),
    )
    expect(messages.some((message) => message.ruleId?.startsWith("react-hooks/"))).toBe(true)
  })

  it("allows an intentionally unused argument, spelled with an underscore", () => {
    // A signature being honoured is not a mistake, and a gate that cannot tell the difference is
    // one people work around.
    const messages = lintAs(
      "src/planted.ts",
      "export const handler = (_event: string, value: number) => value\n",
    )
    expect(severityOf(messages, "@typescript-eslint/no-unused-vars")).toBeUndefined()
  })
})
