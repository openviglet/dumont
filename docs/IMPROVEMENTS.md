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

## XLI Structured sources over paged JSON APIs

## XLII Web-crawler hardening (real-world sources)

## XLIII Connector run control

### XLIII.8 No grounded LLM extraction or summary (D131)

Met in trabalharei (TRA28, job description enrichment) against Dumont 2026.3.

trabalharei wants, for each job with a full description, the required skills (an open
list), and a two-line summary for its job page and abstract, both grounded in the text.
The only LLM surface a plugin can reach is `DumClassifierFactory` (D106): it builds a
`DumClassifier` that answers one label of a closed set (D107), cached per input. That
cannot express a list of values, an open vocabulary drawn from the text, or a generated
summary, so the plugin has no grounded LLM step for either.

Expected: a second factory method, or an `DumExtractor` beside `DumClassifier`, that
takes a task prompt and answers a typed result: a list of strings each of which must
occur in the input (checked after the model answers, so an invented skill is dropped),
or a short text with a length cap. Same contract as the classifier: empty when nothing
is grounded, a thrown error not cached, answers cached by input hash under the task's
prefix, and a per-day call cap the plugin sets. A `DumExtractionEnricher` writing a
multi-valued field (skills) and a text field (the summary into `abstract`) would make it
declarative.

Workaround in trabalharei: none. Benefits ship from a deterministic dictionary; skills
and the summary wait on this line (TRA28's remainder).

## XLIV Frontend build and lint gates

### XLIV.2 Unpatched xlsx through the design system (D134)

Found in the Dependabot alerts on `openviglet/dumont` (2026-10-11): four high alerts,
prototype pollution and a ReDoS, on `xlsx` 0.18.5. SheetJS stopped publishing to npm at
0.18.5; the fixed releases (0.20.3 today) are served only from
`https://cdn.sheetjs.com`.

Landed: `dumont-react` no longer declares `xlsx`, which no source imported. What still
pulls it is `@viglet/viglet-design-system`, whose `dependencies` carry `xlsx` ^0.18.5
for `exportToXlsx`.

What did not work: a `pnpm-workspace.yaml` override pointing `xlsx` at the CDN tarball.
pnpm 11 refuses it with `ERR_PNPM_EXOTIC_SUBDEP`, since `blockExoticSubdeps` forbids a
subdependency resolved by URL, and turning that policy off to silence an alert would be
the worse trade. The same rule means the design system cannot fix it by depending on the
tarball itself: every pnpm consumer would hit the refusal.

The fix, filed as VDS211 in the design system's roadmap: it takes `xlsx` as a
`peerDependency`, so each consumer declares the CDN tarball as its own direct
dependency, which pnpm allows. Then `dumont-react` adds `"xlsx":
"https://cdn.sheetjs.com/xlsx-0.20.3/xlsx-0.20.3.tgz"` and the alerts close.

Exposure meanwhile is low: `exportToXlsx` only writes a workbook (`aoa_to_sheet`,
`writeFile`), and both CVEs are in the parser. Nothing in the console reads a
spreadsheet.

## XLV Builds from released dependencies

A clean checkout of any Dumont branch must compile from the versions its pom names, as
released on Maven Central, with no sibling Turing checkout or locally installed
snapshot. Downstream images (dumont-ce, viglet.cloud's per-tenant Dumonts) build exactly
that way.

## XLVI API access and credential handling

Who can call the connector API, and what it hands back. The key filter and the security
chain both exempt a small set of probe routes; each exemption is a promise that the
route returns nothing a caller could use to act as Dumont.

## XLVII Adoption & authoring

## XLVIII Structured schemas declare custom facets and sorts (D144)

Turing's SN site manifest declares custom facets (Turing T1084) and custom sorts (Turing
T1104: named, multi-level sorts with a placement for documents missing the field, such
as "closing soonest": `expiresAt` ascending, undated jobs last).

Custom facets shipped:
`DumStructuredSchema.Builder.customFacet(TurSNManifestCustomFacet)` declares one over a
declared field, `DumIndexingSchema.customFacets()` carries it, and
`DumTuringIndexingPlugin.manifest` adds it to the one idempotent manifest call
(`provision`, D63). None declared sends none, so console-made facets survive.

What is left is the same path for sorts. The SDK source already has `.customSort(new
TurSNManifestCustomSort(name, description, items))` with
`TurSNManifestCustomSortItem.asc("expiresAt").missingLast()` and
`TurSNManifestResult.customSortsChanged()`, but turing-java-sdk 2026.3.6, the pinned
release, does not; Track Q builds only from released dependencies.

Once a release carries it:

- Bump `turing.version`.
- Add `customSort(...)` to `DumStructuredSchema.Builder` (unique by name, items over
  declared fields), carry it on `DumIndexingSchema`, and add it in
  `DumTuringIndexingPlugin.manifest` beside the facets.
- Log `customSortsChanged` with the facets; a refused manifest still never stops indexing.

Done when the jobs schema (trabalharei's `DumJobsSchema`, TRA125) declares "closing
soonest" and a run against a fresh site leaves the sort on it, and a second run reports
nothing changed.

## XLIX The WordPress connector plugin

### XLIX.16 Accessibility, viewports, visual regression and timing in the browser tests (D160)

**What happens.** The Playwright suite (D159) checks behaviour and console errors on one
desktop viewport. Accessibility is never measured. Phones and tablets are never opened.
Nothing notices a screen that changed its look, and nothing times the search.

**What it should do.**
- Run axe (`@axe-core/playwright`) on every screen the suite visits, failing on serious
  or critical violations: Setup, Fields, Health, Settings, the editor panel, the search
  block in every state, and the theme search page.
- Run the site specs at three viewports (phone 390, tablet 820, desktop 1280) and save
  the screens for each.
- Visual regression on the search block and the admin screens
  (`toHaveScreenshot`, with stored baselines and a small tolerance), refreshed by
  `npm run test:e2e -- --update-snapshots`.
- Budgets: the first results under 1.5 s and a suggestion under 500 ms on the local stack,
  measured from the browser.
- Keyboard-only paths: every admin action and the search block, with focus always
  visible.
- `npm run e2e:look` gains `--a11y`, printing the axe findings for any one page.

### XLIX.17 A theme, language and multisite matrix for the browser tests, in CI (D161)

**What happens.** Every browser check so far ran on Twenty Twenty-Five, one block theme,
in English, on a stack whose Turing image is built by hand. A classic theme, a pt_BR
site or a multisite network was never opened, and the suite runs only on a laptop.

**What it should do.**
- A theme matrix in the Playwright config: Twenty Twenty-Five (block), Twenty
  Twenty-One (classic) and one third-party block theme. The seed activates each in turn,
  and the site specs run against all three.
- A pt_BR run: `WPLANG=pt_BR` in the seed, checking that admin screens and the block show
  Portuguese and that no English string leaks.
- A multisite variant of the stack: two sites, one SN site, each search returning only its
  own key.
- A CI job that starts the stack (`docker compose`, `turing-ce` once T1113 publishes a
  current image) and runs `npm run test:e2e`, uploading the report and screens as
  artefacts.

### XLIX.18 Result cards and the loading, empty and error states (D162)

**What happens.** The Turing search block (`src/blocks/search`,
`src/public/search-block.js`) lists results as a bare numbered list: a title link and
the whole description, which on a long page runs for a screen. There is no post type,
date, image or URL, and the matched words are stripped rather than shown. While a search
runs nothing changes. A query with no match says only "No results found". An error says
"Search is unavailable" with no way forward.

**What it should do.**
- A result card: type badge (Post, Page, Event...), title, a two-or-three-line snippet
  with the matched terms highlighted safely (Turing's `<mark>` kept, every other tag
  dropped), the date in the site's format, a breadcrumb of the URL, and the featured
  image when there is one (`image` is already indexed).
- A loading state: skeleton cards and `aria-busy` while a query runs, with the previous
  results dimmed rather than removed.
- An empty state that offers the did-you-mean, the active filters to clear, and the
  site's most recent posts as a fallback.
- An error state with "Try again", and a link to WordPress search (`?s=`).
- Use the theme's tokens (`--wp--preset--*`, `wp-element-button`) so it looks native on
  any block theme. Check Twenty Twenty-Five and a classic theme with `npm run e2e:look`,
  desktop and `--mobile`.
- Extend `tests/e2e/site/search.spec.mjs` for each state.

### XLIX.19 An accessible combobox with instant results (D163)

**What happens.** The block's search box shows suggestions as a plain list of buttons.
Arrow keys do nothing, Enter searches what was typed and not the highlighted suggestion,
and a screen reader is not told how many suggestions there are or which one is active. A
search runs only on submit, and there is no clear button.

**What it should do.**
- A proper combobox (WAI-ARIA 1.2): `role="combobox"` on the input, `aria-expanded`,
  `aria-activedescendant` moving with Up and Down, Enter taking the active suggestion,
  Escape closing the list, and the typed prefix shown in bold in each suggestion.
- Instant results: after a short pause (about 300 ms), update the results while the
  visitor types, cancelling the request in flight. Keep submit for screen readers and
  slow networks.
- A clear (×) button inside the field, and focus returned to the input after a facet or
  page click.
- Recent searches (stored in the browser only, never sent anywhere) shown when the empty
  box is focused, each removable.
- A Playwright spec that drives the whole thing from the keyboard alone and checks the
  ARIA attributes.

### XLIX.20 Facet groups, a mobile drawer and range facets (D164)

**What happens.** Facets are checkboxes under a plain legend. Picking a value hides the
whole facet, because Turing's structured response stops describing it (T1115); the block
shows an applied-filter chip instead. Every value is listed however many there are. On a
phone the facets sit above the results and push them out of sight. Date and number
fields can only be filtered by exact value.

**What it should do.**
- Collapsible facet groups with a count badge, the first five values and "Show more".
- Keep a filtered facet visible with its value selected and the other values reachable.
  Once T1115 ships this comes from Turing; until then, keep the last unfiltered answer's
  values for that field.
- On narrow screens, a "Filters (n)" button opening a drawer, with Apply and Clear, the
  results visible behind it.
- Range facets for the mapped DATE and INT/FLOAT fields (last 7 days, this year; 0–20,
  20–50), declared as Turing custom facets through the manifest (Dumont D144 has the
  structured-schema half).
- Specs: a value toggled on and off, the drawer on `--mobile`, and a range facet.

### XLIX.21 Search state in the URL, and sort (D165)

**What happens.** The block keeps only `q` in the address bar. A filter, a page or a
sort is lost on reload, cannot be shared, and the Back button does not undo it. There is
no sort: results come by relevance only, and an events or news site cannot list the
newest first.

**What it should do.**
- Put the whole search state in the URL: `q`, `fq[]` (minus the site filter), `page` and
  `sort`. Use `pushState` on a deliberate change and `replaceState` while typing, restore
  the state on `popstate`, and start the block from the URL.
- A sort control: Relevance, plus Newest and Oldest on `publication_date`. It is
  configurable per block, using the SDK's `changeSort` and Turing's sort options
  (`fetchSortOptions`).
- A results-per-page block setting.
- The page title reflecting the query ("workshop – Search – Site"), and `noindex` on
  filtered result URLs.
- Specs: reload keeps filter, page and sort; Back undoes a facet; a shared URL opens the
  same results.

### XLIX.22 A grounded answer panel above the results (D166)

**What happens.** Turing can answer a question from the site's own content with
citations (RAG; the SDK's `createAnswerController` and `postAnswer`). The block never
asks it, so a visitor who types "how do I apply for the grant" gets ten links and no
answer.

**What it should do.**
- An opt-in "Answer" panel above the results (a block setting, off by default). For a
  query that reads as a question, it streams Turing's answer with numbered citations
  linking to the posts it used.
- Show it only when Turing returns citations. With none, show nothing rather than an
  ungrounded text (the project's no-fabrication non-goal).
- "Was this helpful?" buttons recording the SDK's answer signal, and a clear label that
  the answer is generated.
- Respect the SN site's LLM settings and cache. The plugin sends no key: the answer
  endpoint is public in the same way search is.
- A spec against the local Turing with an LLM configured, skipped when none is.

### XLIX.23 An instant-search overlay from the header (D167)

**What happens.** A visitor meets search in the header first: the theme's core Search
block, or a search icon. Those still submit to `?s=` and load a page. The Turing block
lives only on a page someone placed it on, so the fast, faceted search is one page away
from where people start.

**What it should do.**
- An "instant search" overlay: Ctrl/⌘+K or the header's search opens a dialog with the
  combobox, top results and suggestions as you type. Enter goes to the full search page
  with the same query. It is a focus-trapped `dialog` that returns focus where it came
  from.
- A block-variation or render filter so the core Search block (`core/search`) opens the
  overlay when Turing search is on, with no theme edit.
- A setting for which page is the full search page (default: the page holding the
  Turing search block, else `?s=`).
- Specs: open with the shortcut and with a click, navigate by keyboard, and Escape
  closes with focus restored.

### XLIX.24 Live preview, layouts and styles for the search block (D168)

**What happens.** In the block editor the Turing search block is a grey placeholder with
two settings (placeholder text, show facets). An editor cannot see what visitors will
get, cannot choose a layout, and cannot style it beyond the core supports.

**What it should do.**
- A live preview in the editor: the real search running against Turing with a sample
  query, through the same view module, so what the editor sees is what ships.
- Layout settings: facets left, right or above; results as a list or a grid of cards;
  show or hide image, date, type and snippet.
- Block style variations ("Minimal", "Cards") and `theme.json` hooks (`styles.blocks
  ["viglet-dumont/search"]`) so a theme restyles it without CSS overrides.
- Inner blocks, so a theme can rearrange the parts (box, filters, results, pages) or
  replace one with its own.
- Specs: insert the block in the editor, change a setting, and see the published page
  follow.
