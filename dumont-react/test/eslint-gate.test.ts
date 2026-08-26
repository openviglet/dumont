/*
 * D48 â€” the lint that had never run, and the shape that keeps it running.
 *
 * ESLint, typescript-eslint and the two React plugins were devDependencies here all along, and
 * `pnpm run lint` exited non-zero with "couldn't find an eslint.config.js" â€” a message that reads
 * like a broken command rather than an absent gate, which is why it survived.
 *
 * Turning it on found 105 problems in a codebase that had never been linted. Ten were fixed; two
 * classes were structural and are carried as **named files** in the config rather than as a
 * ceiling. That distinction is the whole design, and it is what this file holds: a ceiling lets
 * one change pay for another, while a named list means a new violation fails by name and clearing
 * a file deletes its line.
 *
 * So the assertions are about severity, not about counts. Linting the tree only proves the config
 * parses; what matters is that the same code is an *error* in a new file and a *warning* in one
 * that carries the debt.
 */
import { execFileSync } from "node:child_process"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const APP_ROOT = resolve(__dirname, "..")

/** ESLint's numeric severities. */
const WARNING = 1
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

describe("D48: inherited debt is named, not capped", () => {
  /** A `setState` run synchronously in an effect body â€” the rule that still carries a list. */
  const setsStateInEffect = [
    "import { useEffect, useState } from 'react'",
    "export function Planted() {",
    "  const [value, setValue] = useState(0)",
    "  useEffect(() => { setValue(1) }, [])",
    "  return value",
    "}",
  ].join("\n")

  it("fails a new file for something the debt list tolerates", () => {
    // The half that makes this a gate rather than a record: the rule is live everywhere it is not
    // explicitly excused, so the next one written here stops a build.
    expect(severityOf(lintAs("src/planted.tsx", setsStateInEffect), "react-hooks/set-state-in-effect"))
      .toBe(ERROR)
  })

  it("only warns in a file the config names, with its reason", () => {
    // `monitoring.item.page.tsx` is on the list. Clearing it means deleting its line from the config,
    // which is a diff somebody reviews â€” where lowering a ceiling is a number nobody reads.
    expect(
      severityOf(
        lintAs("src/pages/integration.instance.monitoring.item.page.tsx", setsStateInEffect),
        "react-hooks/set-state-in-effect",
      ),
    ).toBe(WARNING)
  })

  it("excuses a named file for that rule only, not for everything", () => {
    // The failure a file-level exemption would ship. `monitoring.item.page.tsx` is excused its effect;
    // it is not excused an unused variable.
    const messages = lintAs(
      "src/pages/integration.instance.monitoring.item.page.tsx",
      `const unused = 1\n${setsStateInEffect}\n`,
    )
    expect(severityOf(messages, "@typescript-eslint/no-unused-vars")).toBe(ERROR)
  })

  it("holds every file to a rule whose list has been emptied", () => {
    /*
     * D54 emptied the `no-explicit-any` list, so the rule is an error across the console â€” and the
     * list is *gone* rather than kept at zero. Asserted on a file that used to be excused, because
     * that is where a leftover exemption would still be sitting.
     */
    const usesAny = "export const value: any = 1\n"
    for (const file of ["src/planted.ts", "src/hooks/use-grid-adapter.ts"]) {
      expect(severityOf(lintAs(file, usesAny), "@typescript-eslint/no-explicit-any"), file)
        .toBe(ERROR)
    }
  })
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
