/*
 * D90 — the text this console reasons in, read back as it was written.
 *
 * Twenty-eight files here held UTF-8 that had been decoded once as Windows-1252 and encoded
 * again, so an em-dash arrived as three characters and an accented letter as two. Most of it sat
 * in comments, which is what let it survive — nothing renders them, and a reader scrolls past a
 * mangled dash. A tool cannot: an exact-text edit fails on the line it is trying to match, and
 * this codebase keeps its reasoning in comments.
 *
 * The rest of it was on screen. Four source forms masked a password field with eight mangled
 * bullets, and the coverage page put a mangled arrow between two percentages. Whoever wrote the
 * line the census scoped this by had checked the locale files, found them clean, and concluded
 * the damage was cosmetic. It was in the JSX.
 *
 * ## What counts as mojibake, and why not a list of sequences
 *
 * A list of the four or five spellings that happened to be here would pass the day it was written
 * and go blind to the sixth — which is what that reading did, missing the bullet, the arrow and
 * the command key. The property is checked instead: take a run of non-ASCII characters, map each
 * one back to the Windows-1252 byte that would have produced it, and decode those bytes as UTF-8
 * in strict mode. Text that was double-encoded round-trips and yields the character that was
 * meant; text that is merely non-ASCII does not decode at all.
 *
 *   U+00E2 U+20AC U+201D  →  E2 80 94  →  decodes  →  U+2014, the em-dash that was written
 *   U+2014                →  97        →  refused  →  a lone continuation byte is not UTF-8
 *   U+00E9                →  E9        →  refused  →  a two-byte lead with nothing after it
 *
 * Those are spelled as code points rather than shown, because this file is scanned along with
 * every other: an illustration written as itself would be rewritten by the first `--fix`, and the
 * example that explains the defect would become an example of nothing.
 *
 * So a clean file cannot be "repaired" into a different one: the decode has to succeed *and*
 * change something before anything is rewritten.
 *
 * ## Usage
 *
 *   node scripts/mojibake.mjs            # every file that carries it, and how many runs
 *   node scripts/mojibake.mjs --fix      # rewrite them
 *   node scripts/mojibake.mjs --json
 *
 * Exits non-zero when a file still carries it, so this reads as a gate from a shell. The suite
 * calls `findMojibake` directly — `test/source-encoding.test.ts`.
 */
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

const HERE = dirname(fileURLToPath(import.meta.url))
export const ROOT = join(HERE, "..")

/** Where this console keeps text a person wrote. `node_modules` and build output are nobody's. */
export const ROOTS = ["src", "test", "scripts", "index.html", "eslint.config.js", "vite.config.ts"]

const TEXT = /\.(tsx?|jsx?|mts|mjs|cts|cjs|css|html|json|md)$/
const SKIP = new Set(["node_modules", "dist", "build", "coverage", "mf-types"])

/**
 * Windows-1252's own byte range: 0x80–0x9F, where it differs from Latin-1 by naming printable
 * characters rather than controls. Every other byte maps to the code point of the same value.
 *
 * This is the half that makes the check work at all. The commonest spelling of the defect here
 * ends in U+20AC and U+201D, the euro sign and a closing quote, which are bytes 0x80 and 0x94
 * only under 1252 — read as Latin-1 both are control characters, the round-trip never closes, and
 * every mangled em-dash in this tree goes undetected.
 */
const CP1252_HIGH = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ"

const TO_BYTE = new Map()
for (let byte = 0; byte < 0x100; byte += 1) {
  const char = byte >= 0x80 && byte <= 0x9f ? CP1252_HIGH[byte - 0x80] : String.fromCodePoint(byte)
  if (!TO_BYTE.has(char)) TO_BYTE.set(char, byte)
}

const STRICT = new TextDecoder("utf-8", { fatal: true })

/**
 * A maximal run of non-ASCII characters — the unit a double-encoding damaged and repairs as.
 *
 * Spelled as the Unicode property rather than as a negated `\x00-\x7F` range, which says the same
 * thing and puts two control characters in the pattern for `no-control-regex` to refuse.
 */
const RUN = /\P{ASCII}+/gu

/**
 * The text this run was written as, or null when it was not double-encoded.
 *
 * Null covers both refusals: a character Windows-1252 cannot spell, and bytes that are not valid
 * UTF-8. A run that decodes to itself is also null — there is nothing to rewrite, and saying so
 * is what keeps `--fix` idempotent.
 */
export function repairRun(run) {
  const bytes = new Uint8Array(run.length)
  for (let i = 0; i < run.length; i += 1) {
    const byte = TO_BYTE.get(run[i])
    if (byte === undefined) return null
    bytes[i] = byte
  }
  let decoded
  try {
    decoded = STRICT.decode(bytes)
  } catch {
    return null
  }
  return decoded === run ? null : decoded
}

/** Every double-encoded run in one file's text, with what each was written as. */
export function findMojibake(text) {
  const found = []
  for (const match of text.matchAll(RUN)) {
    const repaired = repairRun(match[0])
    if (repaired !== null) found.push({ index: match.index, found: match[0], written: repaired })
  }
  return found
}

/** The same text with every double-encoded run put back. Unchanged where there is none. */
export function repair(text) {
  return text.replace(RUN, (run) => repairRun(run) ?? run)
}

function walk(path, out = []) {
  if (!existsSync(path)) return out
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    const full = join(path, entry.name)
    if (entry.isDirectory()) {
      if (!SKIP.has(entry.name) && !entry.name.startsWith(".")) walk(full, out)
    } else if (TEXT.test(entry.name)) {
      out.push(full)
    }
  }
  return out
}

/** Every text file under `ROOTS`, whether a directory or a file in its own right. */
export function sources(root = ROOT, roots = ROOTS) {
  return roots.flatMap((entry) => {
    const path = join(root, entry)
    if (!existsSync(path)) return []
    return TEXT.test(entry) ? [path] : walk(path)
  })
}

/** One row per file carrying double-encoded text, in codepoint order of its path. */
export function scan(root = ROOT, roots = ROOTS) {
  return sources(root, roots)
    .map((file) => ({ file: relative(root, file).replaceAll("\\", "/"), runs: findMojibake(readFileSync(file, "utf8")) }))
    .filter((row) => row.runs.length > 0)
    .sort((a, b) => (a.file < b.file ? -1 : 1))
}

function main() {
  const rows = scan()
  const fix = process.argv.includes("--fix")

  if (fix) {
    for (const row of rows) {
      const path = join(ROOT, row.file)
      writeFileSync(path, repair(readFileSync(path, "utf8")), "utf8")
    }
  }

  if (process.argv.includes("--json")) {
    console.log(JSON.stringify({ fixed: fix, files: rows.length, rows }, null, 2))
  } else if (rows.length === 0) {
    console.log("mojibake: no file carries double-encoded text")
  } else {
    for (const row of rows) {
      const sample = [...new Set(row.runs.map((r) => `${r.found} → ${r.written}`))].slice(0, 3)
      console.log(`  ${String(row.runs.length).padStart(4)}  ${row.file}  (${sample.join(", ")})`)
    }
    console.log(
      fix
        ? `\nmojibake: rewrote ${rows.length} file(s)`
        : `\nmojibake: ${rows.length} file(s) carry double-encoded text — \`--fix\` rewrites them`,
    )
  }

  process.exit(!fix && rows.length > 0 ? 1 : 0)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main()
