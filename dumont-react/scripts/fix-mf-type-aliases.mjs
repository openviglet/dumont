#!/usr/bin/env node
/*
 * D52 — rewrite `@/…` to a relative specifier in the emitted federated declarations.
 *
 * D49 got the four remotes to emit types. They still taught a consumer nothing: every one of
 * those declarations imports through this project's `@/` path alias, and TypeScript does not
 * rewrite an alias on emit — it preserves the specifier the source wrote. A host without the same
 * `paths` mapping resolves none of them.
 *
 * Fifty-seven of them, across fifty files, which is why this rewrites the output instead of the
 * imports. Changing the sources would mean fighting the `@/` convention in every file the remotes
 * happen to reach, and re-fighting it in every file added after — the alias is correct in this
 * codebase and wrong only once it has been emitted.
 *
 * The zip matters as much as the folder. It is what a host downloads, and it is written by the
 * generator before this runs, so it is rebuilt from the corrected tree rather than left to
 * disagree with it.
 */
import AdmZip from "adm-zip"
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs"
import { dirname, join, relative, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const HERE = dirname(fileURLToPath(import.meta.url))
const APP = resolve(HERE, "..")
const BUNDLE = resolve(APP, "mf-types")
const TYPES_DIR = join(BUNDLE, "@mf-types")
const ZIP = join(BUNDLE, "@mf-types.zip")

/** Where an aliased module lands in the emitted tree: `@/x/y` → `<types>/compiled-types/x/y`. */
const COMPILED = join(TYPES_DIR, "compiled-types")

/** `from "@/…"`, `import "@/…"` — single or double quoted, type-only or not. */
const ALIAS = /(["'])@\/([^"']+)\1/g

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (entry.endsWith(".d.ts")) out.push(full)
  }
  return out
}

/**
 * The relative specifier for `@/rest`, as written from `file`.
 *
 * Extensions are stripped: a few sources import `@/models/sn/sn-site.model.ts` explicitly, which
 * only resolves under `allowImportingTsExtensions` — an option the consumer has no reason to set.
 */
function relativeSpecifier(file, rest) {
  const target = join(COMPILED, rest.replace(/\.tsx?$/, ""))
  const spec = relative(dirname(file), target).replaceAll("\\", "/")
  return spec.startsWith(".") ? spec : `./${spec}`
}

if (!existsSync(TYPES_DIR)) {
  console.error(`fix-mf-type-aliases: no declarations at ${TYPES_DIR} — run the build first.`)
  process.exit(1)
}

let rewritten = 0
let touched = 0
for (const file of walk(TYPES_DIR)) {
  const before = readFileSync(file, "utf8")
  const after = before.replace(ALIAS, (_match, quote, rest) => {
    rewritten += 1
    return `${quote}${relativeSpecifier(file, rest)}${quote}`
  })
  if (after !== before) {
    writeFileSync(file, after, "utf8")
    touched += 1
  }
}

if (rewritten === 0) {
  console.log("fix-mf-type-aliases: no path aliases in the emitted declarations.")
} else {
  console.log(
    `fix-mf-type-aliases: rewrote ${rewritten} path alias(es) across ${touched} declaration(s).`,
  )
}

/*
 * Rebuild the zip from the corrected tree. Written unconditionally: a zip that is newer than the
 * folder but disagrees with it is the failure this task exists to remove, and skipping the work
 * when nothing was rewritten would leave a stale one behind after a partial run.
 */
if (existsSync(ZIP)) {
  const zip = new AdmZip()
  zip.addLocalFolder(TYPES_DIR)
  zip.writeZip(ZIP)
  console.log(`fix-mf-type-aliases: repacked ${relative(APP, ZIP).replaceAll("\\", "/")}.`)
}
