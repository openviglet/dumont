#!/usr/bin/env node
/*
 * D49 — assert the federated remotes actually shipped their type declarations.
 *
 * The build cannot be trusted to say so. For every release before this, `vite build` printed
 * `[ Module Federation DTS ] Error: Failed to generate type declaration #TYPE-001` and exited 0,
 * so a remote went out with no types across the federation boundary and nothing failed.
 *
 * `abortOnError: true` is set, and it is not enough: pointed at the wrong tsconfig the generator
 * reports no error at all, emits seven files into the wrong layout, writes none of the entry
 * declarations, and still exits 0. An exit code that is wrong in both directions is not a gate.
 *
 * So this checks the artefact. One declaration per exposed module, each re-exporting a compiled
 * type — which is the only thing a host actually consumes.
 */
import { existsSync, readFileSync, readdirSync } from "node:fs"
import { dirname, join, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const HERE = dirname(fileURLToPath(import.meta.url))
const APP = resolve(HERE, "..")

/**
 * Where the generator writes them, deliberately outside `build.outDir`.
 *
 * The Vite output directory is the connector's `public/`, emptied on every build *after* the
 * generator has written into it — so a successful generation still left nothing behind. That was
 * the second half of the defect, and the reason a passing build proved nothing.
 */
const TYPES_DIR = resolve(APP, "mf-types/@mf-types")

/**
 * The exposed modules, read from `vite.config.ts` rather than listed here.
 *
 * A hand-written list would pass the day someone exposes a fifth module and forgets this file,
 * which is exactly the silence the task is about.
 */
function exposedNames() {
  const config = readFileSync(join(APP, "vite.config.ts"), "utf8")
  const block = /exposes:\s*\{([\s\S]*?)\n {6}\}/.exec(config)
  if (!block) {
    console.error("check-mf-types: could not read `exposes` from vite.config.ts")
    process.exit(1)
  }
  return [...block[1].matchAll(/"\.\/([A-Za-z0-9_-]+)"\s*:/g)].map((match) => match[1])
}

const expected = exposedNames()
if (expected.length === 0) {
  console.error("check-mf-types: vite.config.ts exposes nothing — refusing to pass vacuously")
  process.exit(1)
}

if (!existsSync(TYPES_DIR)) {
  console.error(
    `check-mf-types: no @mf-types at ${TYPES_DIR}.\n` +
      "The remotes shipped without type declarations. Run `vite build` and check the DTS output.",
  )
  process.exit(1)
}

const missing = []
const empty = []
for (const name of expected) {
  const file = join(TYPES_DIR, `${name}.d.ts`)
  if (!existsSync(file)) {
    missing.push(`${name}.d.ts`)
    continue
  }
  // A declaration that names no compiled type is a stub, and a host learns nothing from it.
  if (!readFileSync(file, "utf8").includes("./compiled-types/")) empty.push(`${name}.d.ts`)
}

if (missing.length || empty.length) {
  console.error(`\ncheck-mf-types: the exposed remotes are missing their declarations\n`)
  if (missing.length) console.error(`  absent:  ${missing.join(", ")}`)
  if (empty.length) console.error(`  stub:    ${empty.join(", ")}`)
  console.error(
    "\nThe generator reports this badly: it can emit a wrong layout, or print #TYPE-001, and\n" +
      "exit 0 either way. Check `dts.generateTypes.tsConfigPath` in vite.config.ts — it must\n" +
      "point at a config that carries real compiler options, not the root solution file.\n",
  )
  process.exit(1)
}

/*
 * D52 — no declaration may import through this project's `@/` alias.
 *
 * TypeScript preserves the specifier the source wrote, so an emitted declaration keeps an alias
 * only this repository can resolve. `scripts/fix-mf-type-aliases.mjs` rewrites them; this is what
 * says it ran, and what fails if the emitted set grows a file it did not reach.
 */
const aliased = []
for (const entry of readdirSync(TYPES_DIR, { recursive: true })) {
  const name = String(entry)
  if (!name.endsWith(".d.ts")) continue
  const file = join(TYPES_DIR, name)
  if (/(["'])@\/[^"']+\1/.test(readFileSync(file, "utf8"))) aliased.push(name.replaceAll("\\", "/"))
}

if (aliased.length) {
  console.error(
    `\ncheck-mf-types: ${aliased.length} declaration(s) still import through the "@/" alias,\n` +
      "which no consumer can resolve. Run `node scripts/fix-mf-type-aliases.mjs`.\n",
  )
  for (const name of aliased.slice(0, 10)) console.error(`  ${name}`)
  if (aliased.length > 10) console.error(`  … and ${aliased.length - 10} more`)
  process.exit(1)
}

const compiled = join(TYPES_DIR, "compiled-types")
const compiledCount = existsSync(compiled) ? readdirSync(compiled, { recursive: true }).length : 0
console.log(
  `check-mf-types: ${expected.length} remote(s) declared — ${expected.join(", ")}` +
    ` (${compiledCount} compiled type entries).`,
)
