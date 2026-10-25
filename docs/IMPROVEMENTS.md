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

### XLIX.20 Facet groups, a mobile drawer and range facets (D164)

**What happens.** Facet groups, the kept filtered facet and the phone drawer landed
(`facets()` in `src/public/search-block.js` rebuilds a filtered facet from the last
answer that described it, until T1115 ships). Date and number fields can still only be
filtered by exact value: a visitor cannot ask for "last month" or "20 to 50".

**What it should do.**
- Range facets for the mapped DATE and INT/FLOAT fields (last 7 days, this year; 0-20,
  20-50), declared as Turing custom facets in the manifest the plugin posts. The shape
  is `customFacets: [{field, name, label, items: [{label, rangeStart, rangeEnd}]}]`, dates
  with `rangeStartDate: "NOW-7DAYS"`. Tried on 2026-10-25: Turing stores the facet but never
  answers with it (Turing T1122), so this waits on that fix.
- The Fields screen lets a mapped DATE or number field opt into ranges, with editable
  bounds for numbers and a fixed set for dates.
- The block lists a range facet like any other group; its filter query is the range Turing
  answers with, and the applied chip reads the range's label.
- A spec that filters by a range and removes it.

### XLIX.29 Natural-language filters offered as chips (D170)

**What happens.** A visitor types "seminars about php from last year", and search treats
all six words as keywords. Turing's public `POST /api/sn/{site}/nl-filters` turns that
text into a query (`php`) plus checked filter queries on the site's own fields. On the
local stack it worked, but resolved "last year" to 2022 (Turing T1117).

**What it should do.**
- In the search block, when a query reads as a sentence (several words, or a date or
  type word), call `nl-filters` once alongside the search and offer its reading as
  removable chips ("Type: post", "Published: 2025"), applied with one click. It is never
  applied silently.
- Show only filters Turing marks `grounded`, and only on fields the plugin mapped
  (Fields screen), so a chip always matches a facet the visitor can see.
- Once applied, the chips are the ordinary applied filters of D164 and live in the URL
  of D165.
- Hidden when D169's probe reports no LLM. A slow call never delays the results, which
  render first.
- Specs in the `ai` project: a type word and a date phrase become the two chips; a
  keyword-only query gets none.

Waits on T1117: shipped before it, a relative date would filter out what was asked for.

### XLIX.30 An Assist section in the editor panel (D171)

**What happens.** The editor panel's Assist section landed with "Already covered?" and
"Link ideas" (`GET viglet-dumont/v1/post/<id>/assist`,
`Viglet_Dumont_Post_State::assist()`: Turing's similar and duplicate searches for an
indexed post, a title search for a draft). Two actions are still missing. Drafting an
excerpt needs Turing's `POST /api/v2/summary`, which answers 403 to a Key-authenticated
server call (Turing T1124). Audience fit needs personas, which the local stack does not
have.

**What it should do.**
- "Draft an excerpt": a REST route that sends the saved content to `/api/v2/summary` with
  the stored key (never the browser) once T1124 ships; "Use" fills the excerpt field, and
  nothing is saved unasked (the D13 non-goal). Show the call's token count when Turing
  reports it.
- "Fits the audience?": when the SN site has personas (`/api/sn/{site}/persona/{id}/content-fit`),
  a score per persona with its reasons; hidden when there are none.
- Hide each action when D169's probe says its feature is off.
- Specs: draft and accept an excerpt; a persona score shows on a site with one.

### XLIX.34 WooCommerce products as a searchable catalog (D173)

**What happens.** With WooCommerce active, a product is indexed as a plain post. Price,
stock, SKU, attributes and product categories never reach Turing, so a shop's search
cannot filter by price or show what is in stock. Turing has a catalog copilot (`POST
/api/sn/{site}/copilot`) and an `add_to_cart` host action that no WordPress shop can
use.

**What it should do.**
- When WooCommerce is active, the mapping (Fields screen) offers product fields:
  price (float), sale price, stock status, SKU, rating, product categories and tags,
  and each global attribute. They are declared in the manifest and indexed on product
  save and on stock change.
- Range facets on price (D164) and a stock facet, with result cards showing price, image
  and an "Add to cart" button (D162's card slots).
- The catalog copilot as a block, when D169 reports it available: "a waterproof jacket
  under 200". It answers with products, and its `add_to_cart` host action calls
  WooCommerce's Store API, so the cart updates with no reload.
- A WooCommerce seed for the browser tests (products with prices, stock and
  attributes).
- Specs: a price range filters products; out-of-stock is a facet value; the copilot
  adds a seeded product to the cart (the live-LLM `ai` project, D174).

### XLIX.26 A live-LLM browser suite for the AI features (D174)

**What happens.** The live-LLM harness landed (`playwright.ai.config.mjs`, `npm run
test:e2e:ai`, `tests/e2e/ai/`): a seed of known facts in English and Portuguese and a
prompt-injection post, deleted again by the teardown; chat specs for grounding, pt_BR,
injection, no invented figure and a p95 time budget; answer-panel specs; answers saved
to `tests/e2e/artifacts/ai/answers.json`. On the local stack every spec skips. The SN
site has no GenAI config, so `/answer` refuses, and chat, though enabled, never
retrieves the posts: asked when the seeded grant closes, it says the information is not
available (Turing T1116). Turing's cost ledger does not record the site's LLM calls
(Turing T1127).

**What it should do.**
- Once T1116 lets the plugin turn the site's GenAI on (D186), the chat and answer specs
  run and pass on the stack started with `OPENAI_API_KEY`.
- Once T1127 records the site's calls, each run reads `/api/v2/llm/cost/summary` before
  and after, reports tokens and dollars beside the p50/p95 times, and fails above a cost
  budget set in the config.
- Natural-language filters (D170), the shop copilot (D173) and the site assistant (D175)
  join the suite as each lands.

### XLIX.27 A site assistant block on Turing's chat (D175)

**What happens.** Turing runs a RAG chat over the site's own content: `GET
/api/sn/{site}/chat` answered from the seeded WordPress posts on the local stack. The
SDK has `createChatController` (SSE conversation, session cookie, citations, intents,
slots and host actions). A WordPress visitor has no way to reach it.

**What it should do.**
- A "Site assistant" block, also placeable site-wide by a setting (a launcher button in
  a corner), opening a chat panel built on `createChatController` against the public
  `/chat/conversation`. It streams tokens and lists the posts it used as cards.
- Conversation memory per visitor through the SDK's `TUR_SESSION` cookie, with a
  "New conversation" button that clears it.
- Host actions limited to what is safe on a content site: `navigate` to a cited post,
  with `allowNavigate` restricted to the site's own origin.
- Suggested first questions, set in the block and defaulting to the top search terms
  once the insights screen reads them.
- A clear "generated" label and "Was this helpful?" signals. Hidden when D169's probe
  says chat is off.
- Accessible: a focus-trapped dialog, `aria-live` for the streamed answer, Escape to
  close.
- Specs in the `ai` project (D174): a question about a seeded post gets that post as a
  citation, a follow-up keeps context, and navigation stays on the site.

### XLIX.32 A search insights screen in wp-admin (D178)

**What happens.** A site owner cannot see what visitors search for, what they fail to
find, or what they click. Turing keeps the metrics (top terms by period, live accesses,
and click-through from D176), but only its own console and its MCP tools read them.

**What it should do.** A "Search insights" admin screen in the plugin:
- Top terms today, this week, this month and all time, with counts and CTR.
- Zero-result queries, each with "Add a synonym" (D172) and "Write about this" (a new
  draft titled with the query): the content gaps a site owner can act on.
- A trend sparkline per term, and the live count.
- An AI summary of the period ("visitors look for X, find nothing for Y, click Z most")
  through Turing's summary endpoint, which is cached and regenerable as Dumont's own
  insights page is. Hidden when D169 says off.
- A dashboard widget with the week's top five and zero-result five.
- Read through the plugin's REST routes with the stored key, capability
  `manage_options`.
- Specs: seeded searches and clicks appear in the tables; an empty site shows an empty
  state, not zeros.

Zero-result queries and content gaps are MCP tools only (`top_failed_searches`,
`content_gaps`), so their two tables wait on Turing T1119. CTR waits on Turing T1131:
D176's clicks are stored, but nothing reads them back. Top terms are REST today, and the
rest can ship first.

### XLIX.35 A showcase site, admin tab and recorded tour (D179)

**What happens.** Each Turing feature the plugin brings to WordPress lands as its own
block or screen. Nobody can see them together: the Docker stack starts with a test seed,
the docs have one screenshot per screen, and a prospect cannot try any of it.

**What it should do.**
- `run-docker.cmd demo`: the stack with a realistic content seed (an institution's
  news, events, courses and FAQ, in English and pt_BR, plus a WooCommerce catalog when
  D173 is in), and a "Showcase" page holding every block. The page has a short label per
  block naming the Turing feature behind it.
- An admin "Showcase" tab listing each feature: on or off (from D169), the block that
  shows it, and a link to the page section.
- A guided tour, `npm run e2e:tour`, that drives the showcase and records a video and
  screenshots per feature. It is the source for the docs and the site's visuals.
- vigletdocs: a "Turing on WordPress" page with the feature matrix and those
  screenshots, and the site copy claims only what the tour shows working
  (`dumont-site` rule).
- Specs: the tour runs green on the stack with a key, and the keyless parts run without
  one.

Last in the track's AI series: it gathers what the others built.

### XLIX.36 Event messages translated when read, not when written (D180)

**What happens.** `Viglet_Dumont_Events::record()` stores the message already
translated, in the language of the request that wrote it: "Fields provisioned", "Bulk:
16 sent, 0 failed". The Health page prints the stored string, so a pt_BR reader sees
English for every event written while the site, the writer or WP-CLI ran in English, and
two admins with different languages see one log in one language. The pt-br browser spec
(D161) found it and leaves the events table out of its leak check until this ships.

**What it should do.**
- Store a message key and its arguments (`provisioned`, `bulk` with `sent` and
  `failed`, ...) instead of the sentence; Turing's own error text stays as it came.
- Translate when Health renders, in the reader's language: one `__()` per key on the
  server, or the keys handed to the React screen with its own strings.
- Events already stored as sentences keep showing as they are.
- Drop the events exclusion from `tests/e2e/i18n/pt-br.spec.mjs`, and add a PHPUnit case
  that records under `en_US` and reads under `pt_BR`.

### XLIX.37 Text buttons that hold their look under a classic theme (D181)

**What happens.** Under Twenty Twenty-One (the `site-classic` project, D161), "Clear all
filters" renders as white text on a dark box: the theme's own `button` rules win over
`.viglet-dumont-search__link`, whose reset sets `background: none` and nothing for the
theme's hover, focus and padding states. The same rule set styles "Did you mean", and
the search box's × clear button (D163) shows the same dark box inside the field. The
baseline `tests/e2e/snapshots/win32/site-classic/search-block-facet.png` holds the wrong
look.

**What it should do.**
- Reset the text buttons for every state a classic theme styles (`:hover`, `:focus`,
  `:active`): background, colour, border, padding and box shadow, scoped under
  `.viglet-dumont-search` so the specificity beats a theme's bare `button` rule.
- Keep a visible focus ring of the block's own (the D160 keyboard checks assert it).
- Refresh the `site-classic` baselines with `npm run test:e2e -- --update-snapshots` and
  check the new PNGs show a link-styled control.

### XLIX.38 The browser suite in CI (D182)

**What happens.** The browser suite (D159, D160, D161) runs only where someone starts
`run-docker.cmd` by hand. CI (`validate.yml`) builds and unit-tests the plugin but never
opens a screen, so a regression in the block, an admin screen, a theme or the pt_BR
strings lands unseen until the next local run.

**What it should do.**
- A workflow (on push and PR touching `wordpress/`) that starts the stack with
  `docker compose` and `run-docker.cmd`'s steps written for a Linux runner, runs
  `npm run test:e2e` (and `E2E_MULTISITE=1` on a schedule, since it doubles the setup),
  and uploads `tests/e2e/artifacts/` as an artefact.
- It needs a `turing-ce` image current enough for the plugin's calls, which T1113
  publishes; the published `latest` lags the local build `run-docker.cmd` prefers.
- The baselines are per platform (`tests/e2e/snapshots/{platform}/`): the first run
  writes the `linux` set, which is reviewed by eye and committed before the job can fail
  on a visual change.
- Speed budgets on a shared runner are noisier than on a laptop; keep them, and read a
  budget failure against the timing annotations before raising a bound.

### XLIX.39 Drop the instant-search wildcard once Turing matches prefixes (D183)

**What happens.** Turing matches whole words only, so an instant search for "work" finds
nothing (Turing T1120). D163's search block works around it: `run( query, prefix )` in
`wordpress/src/public/search-block.js` appends `*` to the last word of an instant
search. That is a guess at Turing's query syntax: a quoted phrase or a field query typed
into the box gets a stray wildcard, and a wildcard term loses stemming and highlighting
weight. It also lands in Turing's search log, so synonym mining proposes `work* = work`
(Turing T1125; D172 filters those out).

**What it should do.** Once Turing ships T1120's as-you-type parameter (and the SDK's
`searchQuery` passes it), the block sends that flag instead and drops the `*`. The
keyboard spec `results follow the typing without a submit, a half-typed word included`
already asserts the behaviour, so it stays as the check that nothing regressed.

### XLIX.40 Plural forms for the search block's counts (D184)

**What happens.** The block's summary is one label, `__( '%d results' )`, handed to the
browser in `render.php` and filled in `state.summary` (`src/public/search-block.js`). A
search that finds one document reads "1 results", in English and in pt_BR ("1
resultados"). The same holds for "%d suggestions" (the combobox's live announcement) and
"Show %d more" never reaches one, but "Filters (%d)" does not need a plural.

**What it should do.**
- Hand the browser the plural forms rather than one string: either both of
  `_n( '%d result', '%d results', ... )` for the two forms English and Portuguese need, or,
  better, the script module's own translations through `wp_set_script_translations` and
  `_n()` from `@wordpress/i18n`, which carries every language's plural rule.
- The same for the suggestion count.
- A spec that filters to a single result and reads "1 result" (Seats 20 under
  `q=workshop` gives one).

### XLIX.41 The search block's parts as inner blocks (D185)

**What happens.** The Turing search block (D162 to D168) is one block: its box, filters,
results and pages are fixed in `src/blocks/search/render.php`, in that order. Settings
choose where the filters sit and which parts of a result show, but a theme cannot put
the pages above the results, move the box into a header row, or swap the results list
for its own.

**What it should do.**
- Inner blocks for the parts: `viglet-dumont/search-box`, `-filters`, `-results`,
  `-pages`, each a child block whose render is the current markup for that part, with
  `parent: ["viglet-dumont/search"]`. The parent keeps `data-wp-interactive`, the store
  and the state; the children only read it, so the Interactivity context is unchanged.
- The parent's default template is today's order, so an existing block renders as it does
  now; a block saved before this (no inner blocks) renders the default template.
- The editor preview (D168's server render) keeps working: the children render on the
  server like the parent.
- Specs: a page whose parts are reordered (pages above results) renders them in that
  order, and the existing site specs pass unchanged on the default template.

### XLIX.42 Generative AI declared in the manifest the plugin posts (D186)

**What happens.** Health's AI card and `wp dumont ai status` (D169) show that answers
are off on a site whose chat works ("AI is not enabled for site"), and tell the admin to
turn generative AI on in Turing's console. The plugin provisions its fields through the
manifest (`Viglet_Dumont_Mapping::manifest()`), but the manifest has no way to turn a
site's generative AI on (Turing T1116), so a fresh install needs that console step.

**What it should do.**
- Once T1116 ships, declare the site's generative AI in the manifest the plugin posts
  (behind a setting, off by default: it spends the owner's LLM budget), so a provisioned
  site answers with no console visit.
- Drop the console instruction from the answer row's `fix` in `viglet_dumont_ai_status()`
  when the manifest did it, and keep the reason Turing gives otherwise.
- A spec on the stack started with `OPENAI_API_KEY`: provision, then the answer row reads
  On.

### XLIX.43 Boosts on the Search tuning screen (D187)

**What happens.** The Search tuning screen (D172) has synonyms, promoted results,
suggestions and a Try it preview, but no boosts. The server half is written:
`Viglet_Dumont_Tuning::set_boosts()` and `boosts()` keep the plugin's ranking
expressions (named "WordPress: …"): newer first is Turing's recency form
(`publication_date` = `asc`), sticky posts first matches the `sticky` field every
document now carries, and a post type "down" is `type IS NOT` (Turing boosts, it never
sinks). `POST viglet-dumont/v1/tuning/boosts` calls it, and `TuningTest` covers the
payloads. Live, Turing answers 500: saving a ranking expression over the API key fails
in its auditor (Turing T1126).

**What it should do.**
- Once T1126 ships, a Boosts card on the screen: "Newer first", "Sticky posts first", and
  per post type Up, Normal or Down, saved together, read back from `boosts()`.
- The Try it preview shows the change, as it does for synonyms.
- Specs: newer first puts Workshop on testing (10 January) ahead of Workshop on search
  (1 January) for `workshop`; sticky moves a seeded sticky post up; switching both off
  restores relevance order.

### XLIX.44 Search abandonment and GA4's recommended events (D188)

**What happens.** The search block and the instant search report searches and result
clicks (D176). They go to Google Analytics as the SDK's `turing_search` and
`turing_search_result_click`. GA4's own site-search reports read `search` and
`select_content`, so they stay empty until the site owner writes custom definitions. No
event says "searched, saw results, opened none and left", which is the abandonment the
design asked for. The SDK has no canonical search abandonment event, and its GA sink
sends only the `turing_*` names (Turing T1130).

**What it should do.**
- Once T1130 lands, turn on the sink's recommended mode, so `search { search_term }` and
  `select_content` reach GA4 beside the canonical events.
- Arm the SDK's abandonment helper after each search the visitor settled on, in both the
  block and the overlay. Disarm it on a result click, a new search or a facet change. On
  tab hide or page leave it emits `turing_search_abandoned { query, results }` with no
  personal data, gated by `trackingAllowed()` like the rest.
- Specs in `tests/e2e/site/analytics.spec.mjs`:
  - leaving after a search sends one abandonment;
  - a result click sends none;
  - without statistics consent, nothing is sent;
  - the stubbed gtag receives `search` with `search_term`.
