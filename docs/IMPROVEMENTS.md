# Dumont — Improvements & design rationale

> **What this file is:** the *design rationale* (the what & why) for **unshipped**
> Dumont work — the spec behind each backlog item. It is one of the roadmap
> documents; keep them in sync:
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — `D##` backlog | task |
> | [STRATEGY.md](STRATEGY.md) | Positioning, moat, the bets, non-goals | — |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active `D##` tasks | — |
>
> **Founding rule:** once a task ships, its detail is removed from here — `git log`
> is the authoritative history. Do not re-accrete shipped implementation reports.

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Cross-references** use paths from the repo root; linked code may have moved —
verify against `git ls-files` before acting. This track is cross-repo: the demands
originate from the **cursarei → Viglet** migration and pair with Turing **Block R**
(`turing/2026.1/docs/IMPROVEMENTS.md` §XIX). See [DOCS_INDEX.md](DOCS_INDEX.md).

## Table of contents

- [I. The second bet — adoption & trust on top of ingestion](#i-the-second-bet--adoption--trust-on-top-of-ingestion)
- [IX. Cross-cutting decisions & restrictions](#ix-cross-cutting-decisions--restrictions)

---

## I. The second bet — adoption & trust on top of ingestion

The roadmap's first bet (Tracks A–C) is **ingestion fidelity**: a structured-source
SPI, declarative strategies, schema-as-code grounding, crawler hardening and the
cursarei sidecar→native path. That bet answers *"can Dumont extract this source
correctly?"*.

It does **not** answer the two questions that actually gate value once the framework
exists:

1. **Adoption** — *"how cheaply can someone stand up a new source?"* A declarative
   manifest is only cheap if producing it is cheap. Today that still means a developer
   reading HTML and writing selectors. → **Track D** (D13 AI-assisted inference, D14
   dry-run preview).
2. **Trust** — *"can I rely on what came out, and prove where it came from?"* A
   connector that silently drops fields when a catalog changes layout, with no
   provenance on what it published, is not safe to feed a RAG answer. → **Track E**
   (D15 drift monitoring, D16 provenance).

Track F (semantics — embeddings, entity resolution, reusable taxonomy) is the
**differentiator** that turns the result from "a pipe" into the *Data Exchange*
platform the product name claims: it doesn't just move documents, it makes them
semantically richer and de-duplicated on the way through.

The ordering is deliberate. Tracks A–C are prerequisites; D13/D14 (AI-assisted inference +
dry-run preview) opened the adoption layer, D15 (field-coverage & drift monitoring) made
partial degradation visible and the sidecar→native parity gate (D11) measurable rather than
manual, and D16 (field-level provenance) made every published field traceable — all shipped,
completing the trust track. Track F (semantics) follows the RAG maturity on the Turing side.

## IX. Cross-cutting decisions & restrictions

These constrain every task above:

- **Grounding is non-negotiable, everywhere.** Inference (D13), enrichment, embeddings
  and entity-merge (Track F) may omit, normalize, classify, derive or merge — they may
  **never** fabricate a sourced attribute or fill an absent field with a placeholder.
- **Reuse Turing's LLM surface; do not re-wire models in Dumont.** D13 and the D04/D17
  LLM paths all go through `RestClient` → `TurSummaryAPI` / `ChatModel`, inheriting
  Turing's cache and Global Settings (`llmCacheEnabled`, `llmCacheTtlMs`,
  `llmCacheRegenerate`).
- **The web crawler stays usable unchanged.** Every Track A/D/E/F capability sits *above*
  or *beside* `DumWCPluginProcess` under the same `DumConnectorPlugin` SPI — not a rewrite
  of it.
- **No auto-publish from a model.** Anything an LLM proposes is a draft a human confirms.
- **Additive state, not migrations of meaning.** Conditional-fetch validators (D12),
  coverage history (D15) and provenance (D16) are new state alongside existing checksum
  state — they do not change what a "document" is.

## XI The console adopts the shared bento layer

### XI.1 Count the console before moving it (D35)

Dumont's console is 39 pages built from 88 components, 33 of them under `components/ui`.
Nobody can say how much of that the shared layer could carry, which means this track has
no size, no order and no way to know it is finished. Shio met the same problem and its
answer was an instrument first: a script that reads the source and reports what is still
console-era, so a migration is a falling number rather than a feeling.

Count it here the same way. For each page, whether it renders console chrome or bento
chrome; for each component, whether it is a local declaration, a one-line re-export of a
design-system export, or genuinely this product's. The third category is the interesting
one and the reason the count cannot be a grep for imports: `AppFooter` and the user
context are Dumont's own and will never move, while `ui/table.tsx` is a fork of eight
primitives the package already exports.

The output is a number and a list, printed by a script anyone can run and cheap enough
to run on every commit. Without it, every later line in this track is judged by whoever
last looked, and "the console adopts the shared bento layer" stays a sentence in a
roadmap instead of a claim something checks. Land it first, because the tasks below are
deletions and a deletion with no baseline is indistinguishable from a regression.

### XI.2 Render the layer once before trusting it (D36)

Dumont installs the design system and takes 95 imports from it — 81 from the root entry,
13 from `./router`, one stylesheet. Not one comes from `./bento`. The subpath is there,
published since 2026.3.3, carrying the frosted surface, the airy hero, the mosaic and
the save-bar morph, and this console has never rendered any of it.

That is what makes three products that share a design system look unrelated. Turing has
118 bento pages; Shio is migrating 43. Dumont renders 39 console-era pages and will keep
looking like the previous era for as long as nothing here imports the new one.

The first import is not a migration and should not be treated as one. It is the question
every task below assumes an answer to: does the package render correctly inside this
application's providers, its Tailwind build, its theme? Turing found out that the answer
has a shape — its `BentoListPage` takes a layout as a prop rather than fetching one, and
a naive adoption silently dropped an affordance on 16 surfaces. Wire the subpath and its
stylesheet, render a handful of representative surfaces against it, and compare them
against what the console renders today.

Nothing below is safe until that comparison has been made once, because every later line
is justified by the claim that the package does the same thing.

### XI.3 A parallel shell, so a page can move back (D37)

A migration of 39 pages is only safe if it can be done one page at a time and undone the
same way. That needs somewhere for a migrated surface to live that is not the console
route tree, because the two eras mount different chrome: the console root provides a
sidebar shell that the bento components neither expect nor tolerate, and a page cannot
be half of each.

Shio reached the same conclusion and its SH888 is this task under another name — a
parallel bento route tree beside the console one, so a surface moves by being added to
the new tree rather than by being rewritten in place. The old route keeps working until
the new one is trusted, which is what makes each of the 39 reversible.

Build the tree, mount the bento shell at its root, and put one already-migrated surface
under it. Two things this is not: it is not a rewrite of the console router, which keeps
every route it has, and it is not a redirect — both trees serve, and which one a person
reaches is a decision this task does not make.

The cost of skipping it is the cost Turing paid by not having it: the bento layer grew
inside the product, page by page, until 118 pages depended on components nothing shared,
and unwinding that took a block of its own.

### XI.4 The one fork among thirty-three (D38)

Thirty-three files sit under `components/ui` and thirty-two of them are honest. One is
not: `table.tsx` declares `Table`, `TableHeader`, `TableBody`, `TableFooter`,
`TableHead`, `TableRow`, `TableCell` and `TableCaption` locally, and the package exports
all eight under the same names.

This is the worst shape a duplicate takes. A table is not a leaf a single page renders —
it is what every list surface in this console is built out of, so a divergence between
this copy and the package's is a divergence every list inherits at once. And because
both are shadcn primitives that started from the same generator, they look identical
today and will drift silently: a padding fix, a `data-slot` rename, an overflow change
lands in one and not the other, and nobody meets the difference until two products are
compared side by side.

Shio has zero forks among its 24 ui files and Turing shimmed 39 of 59. Dumont is one
file away from the same state, which is why this is worth its own line rather than being
folded into the lint below: the lint reports it, and reporting is not fixing.

Replace the eight declarations with a one-line re-export, the pattern the other 32 files
already use. If a variation is genuinely needed — and check before assuming it is — wrap
the shared component rather than re-declaring its name.

### XI.5 One declaration, read three times (D39)

The bento nav rail and the command palette are both driven by a declaration of what
surfaces exist: their labels, their routes, their icons, which of them a given user may
see. The package supplies the schema and takes that declaration as data, deliberately —
routes and entity names are product data and the non-goals keep them out of the package.

Dumont has never written that declaration down. Its navigation lives inside the console
sidebar component as markup, which means a rail and a palette added later would each
need their own copy, and the three would drift the moment a surface is renamed. Turing
met the same problem from the other side: its `bento-nav.config` mixes behaviour with 29
surfaces and has to become data over the package schema before the rail can move, which
is a task of its own there.

Dumont has the easier version of it, because nothing has been written twice yet. Extract
the surfaces the console sidebar renders into one declaration shaped to the package's
nav schema, and have the sidebar read it. That is a refactor with no visible change,
which is the point: it can land before any bento surface exists, and it is what makes
the rail and the palette a configuration rather than a second list.

Write it once, read it three times — sidebar today, rail and palette when the shell
arrives.

### XI.6 The chrome speaks, and not yet in this console's languages (D40)

The bento chrome the package owns speaks: the save bar says "Unsaved changes" and "Title
required", the palette names its sections, the rail labels its groups, the empty states
carry their own copy. Every one of those strings goes through `t()` with a
`defaultValue`, which is what VDS21 shipped — so a consumer with no translations renders
English rather than a raw key, and a consumer with translations gets its own languages.

Dumont has an `i18n` directory and a console that already speaks more than English. The
moment a bento rail or palette renders here, its chrome will be the one part of the page
that is not translated. English beside Portuguese in the same shell reads as a bug, and
it is the kind that ships because nobody notices their own default language.

Turing found the flip side of this in T989: two of its suites asserted the raw i18n key
and started failing the moment the package's component rendered its `defaultValue`
instead. The behaviour was correct; only the copy had changed. That is worth knowing
here because it says the strings really do arrive from the package and really can be
overridden.

Add the bento chrome namespace to this console's translations, in the languages it
already ships. Do it with the shell rather than after it, so no migrated surface is ever
the untranslated one.

### XI.7 A lint, since discipline is not an instrument (D41)

Nineteen names are declared in this console and exported by the design system under the
same names, across eleven files, and nothing anywhere fails because of it. They were
found by pointing the package's export-manifest lint at this repository, which is the
first time anyone counted.

Some of them are legitimate. `AppFooter` renders Dumont's own identity, and the user
context is bound to this product's auth service and model — those share a name and
nothing else, and the lint has an annotation for exactly that case, where the reason is
the point. `ui/table.tsx` is not legitimate and has its own line above. The rest are
console-era chrome that leaves when the console does.

What matters is not this list but the next one. Turing is the proof: an entire design
language grew inside that product while a design system sat next to it exporting console
chrome, because nothing ever failed when a component was re-declared instead of
imported. Dumont is about to put a large shared surface within reach, and the cheapest
path to one missing prop is a local copy.

Adopt the lint the package ships — `viglet-ds-check-duplicates`, which reads
`exports.json` from the installed package and ends each finding in the import that
replaces the copy — and run it in this repository's CI. Annotate what is deliberate with
its reason, so what fails is a new duplicate.

### XI.8 A floor, not coverage (D42)

There are no test files in `dumont-react` and no `test` script in its `package.json`.
Not a thin suite — none. Every other line in this track is a change to what 39 pages
render, and the only thing that would notice a regression is a person opening the page.

That is affordable while nothing moves and unaffordable the moment something does.
Turing had suites and they earned their keep twice inside one afternoon: the list-page
suite caught a dropped Customize affordance that type-checked cleanly and would have
reached 16 surfaces silently, and the status-marker suite caught a copy change that
turned out to be correct. Both were invisible to the compiler. Neither would have been
noticed here.

The ask is not coverage. It is a floor: a runner wired up, and a handful of tests that
render the surfaces this track touches and assert they still render — the shell mounts,
a list page shows its rows, a form page shows its save affordance. Enough that a
migrated page failing to render is a red run rather than a support ticket.

Land it early. A suite written after a migration documents whatever the migration
produced, including its mistakes; a suite written before it is the only thing that can
tell the two apart.

### XI.9 A copy that lands where nobody reads (D43)

`copy-ds.cmd` builds the design system and copies its `dist` into this console's
`node_modules`, so a package change can be tried here before it is published. It sets

    DS_DEST=d:\Git\viglet\dumont\dumont-react\node_modules\@viglet\viglet-design-system

and that path does not exist. The repository is laid out by version — `dumont\2026.3\
dumont-react` — so the copy lands in a directory nobody reads, `xcopy` creates it
happily, and the script reports success. The failure mode is the expensive one: it looks
like the package change had no effect.

Both sibling repositories carry the same defect and both have a line for it, SH895 in
Shio and T998 in Turing, which says something about how the three copies of this script
came to exist rather than one. Fixing the path here is a one-line change; the reason it
is worth a task is that every other line in this track is a package change tried against
this console first, and each one of them silently gets the stale `dist` until this is
right.

Fix the destination, and make the script fail rather than succeed when the target
directory is absent — a copy into a path that had to be created is the signal, and
`xcopy /i` is what suppresses it today.
