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

### XLIX.30 An Assist section in the editor panel (D171)

**What happens.** The editor panel's Assist section has "Already covered?" and "Link
ideas" (`GET viglet-dumont/v1/post/<id>/assist`) and "Draft an excerpt" (`POST
viglet-dumont/v1/post/<id>/excerpt`, `Viglet_Dumont_Post_State::draft_excerpt()`, which
calls Turing's `/api/v2/summary` with the stored key). Audience fit is the one action
left: the local stack has no persona, so nothing tests it yet.

**What it should do.**
- "Fits the audience?": when the SN site's agent has audience personas, a score per
  persona with its reasons, from Turing's anonymous
  `/api/sn/{site}/persona/{personaId}/content-fit` (T634), called from the plugin's server
  like the excerpt. Hidden when the site has none.
- List the site's audience personas through Turing T1143, which is filed because no read
  answers it today: the plugin's key opens `/api/persona` and `/api/ai-agent`, but
  resolving the site's effective agent there would copy Turing's logic into the plugin.
- The spec provisions one audience persona on the local Turing (`/api/persona`) in
  global-setup, so a site with personas exists to test against.
- Specs: a persona score shows on a site with one; the action is hidden on a site with
  none.

### XLIX.34 WooCommerce products as a searchable catalog (D173)

**What happens.** With WooCommerce active, products are a mapped type
(`Viglet_Dumont_WooCommerce`): `woo:price`, `regular_price`, `sale_price`,
`stock_status` (sent as the shop's words, "In stock"), `sku` and `average_rating`, with
price, a Stock facet, product categories and tags and each global attribute mapped by
default, and a stock change from an order queueing the product again. The `woo`
Playwright project activates WooCommerce, seeds three jackets (`fixtures/woo-seed.php`)
and filters them by a price range, stock and color. Two things are still missing.

**What it should do.**
- Result cards for a product show its price (and the regular price struck through when
  on sale), its image and an "Add to cart" button that posts to WooCommerce's Store API
  (`/wp-json/wc/store/v1/cart/add-item`, with its nonce) and says when it worked. D162's
  card has the slots; a product result needs its id and price in the indexed document.
- The catalog copilot as a block, when D169 reports it available: "a waterproof jacket
  under 200". It answers with products, and its `add_to_cart` host action calls the same
  Store API, so the cart updates with no reload.
- Specs: a card adds a seeded product to the cart (`woo` project); the copilot adds one
  (the live-LLM `ai` project, D174).

### XLIX.26 A live-LLM browser suite for the AI features (D174)

**What happens.** The live-LLM harness landed (`playwright.ai.config.mjs`, `npm run
test:e2e:ai`, `tests/e2e/ai/`): a seed of known facts in English and Portuguese and a
prompt-injection post, deleted again by the teardown; chat specs for grounding, pt_BR,
injection, no invented figure and a p95 time budget; answer-panel specs; answers saved
to `tests/e2e/artifacts/ai/answers.json`. On the local stack every spec still skips: the
SN site has no GenAI binding. Turing has shipped both fixes the suite waited on: a
manifest `genAi` block now creates the site's GenAI binding (T1116), and every SN call,
chat included, records its site in the cost ledger, which `cost/summary?site=` narrows
to (T1127).

**What it should do.**
- Once the plugin declares the site's GenAI in its manifest (D186), the chat and answer
  specs run and pass on the stack started with `OPENAI_API_KEY`.
- Each run reads `/api/v2/llm/cost/summary?site=<SN site>` before and after, reports
  tokens and dollars beside the p50/p95 times, and fails above a cost budget set in the
  config.
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
and the clicks D176 reports), but the plugin reads none of them.

**What it should do.** A "Search insights" admin screen in the plugin:
- Top terms today, this week, this month and all time, with counts and CTR.
- Zero-result queries, each with "Add a synonym" (D172) and "Write about this" (a new
  draft titled with the query): the content gaps a site owner can act on.
- A trend sparkline per term, and the live count.
- An AI summary of the period through Turing's summary endpoint, cached and regenerable
  as Dumont's own insights page is. Hidden when D169 says off.
- A dashboard widget with the week's top five and zero-result five.
- Read through the plugin's REST routes with the stored key, capability
  `manage_options`.
- Specs: seeded searches and clicks appear in the tables; an empty site shows an empty
  state, not zeros.

Everything it reads is now REST in Turing. Failed searches and content gaps take a
from/to/period/rows window (T1119). `GET /api/sn/{siteId}/metrics/clicks` reports
searches, clicks, CTR and mean clicked position per term, plus the terms found but never
opened (T1131). The SDK reads that as `fetchClickThrough`. When metrics are off,
`/click` answers 204 with `X-Turing-Metrics: disabled`, which the screen should explain.

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
- Turing T1113 shipped: every release pushes `turing-ce` at its version and moves
  `latest`, a branch dispatch publishes `<branch>-snapshot`, and `/api/discovery` names
  the build's version and commit. The workflow pins a version (or the snapshot of the
  branch it pairs with) and logs `/api/discovery`, so a failure names the Turing it ran
  against.
- The baselines are per platform (`tests/e2e/snapshots/{platform}/`): the first run
  writes the `linux` set, which is reviewed by eye and committed before the job can fail
  on a visual change.
- Speed budgets on a shared runner are noisier than on a laptop; keep them, and read a
  budget failure against the timing annotations before raising a bound.

### XLIX.39 Drop the instant-search wildcard once Turing matches prefixes (D183)

**What happens.** D163's search block works around whole-word matching: `run( query,
prefix )` in `wordpress/src/public/search-block.js` (and `update()` in
`src/public/overlay.js`) appends `*` to the last word of an instant search. That is a
guess at Turing's query syntax: a quoted phrase or a field query typed into the box gets
a stray wildcard, and a wildcard term loses stemming and highlighting weight. Turing
T1120 shipped the fix. A POST search with `mode: "instant"` runs its last plain term as
a prefix on every engine, echoes the typed `q`, and stays out of the search log, so
synonym mining no longer sees `work*` (T1125). The SDK's `searchQuery` sends it.

**What it should do.** The block and the overlay send `mode: "instant"` for as-you-type
searches instead of the `*`. The keyboard spec `results follow the typing without a
submit, a half-typed word included` already asserts the behaviour, so it stays as the
check that nothing regressed. The same mode makes the SDK abort a superseded search
(T1121) and leave instant searches out of its analytics (T1129). Drop the wildcard
filter D172's suggestions apply, once the stack's log holds no `*` terms.

### XLIX.42 Generative AI declared in the manifest the plugin posts (D186)

**What happens.** Health's AI card and `wp dumont ai status` (D169) show that answers
are off on a site whose chat works ("AI is not enabled for site"), and tell the admin to
turn generative AI on in Turing's console. The plugin provisions its fields through the
manifest (`Viglet_Dumont_Mapping::manifest()`). Turing T1116 shipped: a manifest `genAi`
block creates or updates the site's GenAI binding (an agent id or the Default AI Agent,
plus the knowledge-base mode), reported as `genAiChanged` and `genAiToChange`. Declaring
the block is what turns GenAI on.

**What it should do.**
- Declare the site's GenAI in the manifest the plugin posts, behind a setting that is off
  by default, since it spends the owner's LLM budget. A provisioned site then answers
  with no console visit. Show the plan's `genAiToChange` on the Fields screen before it
  is applied.
- Drop the console instruction from the answer row's `fix` in `viglet_dumont_ai_status()`
  when the manifest did it, and keep the reason Turing gives otherwise.
- A spec on the stack started with `OPENAI_API_KEY`: provision, then the answer row reads
  On.

### XLIX.43 Boosts on the Search tuning screen (D187)

**What happens.** The Search tuning screen (D172) has synonyms, promoted results,
suggestions and a Try it preview, but no boosts. The server half is written:
`Viglet_Dumont_Tuning::set_boosts()` and `boosts()` keep the plugin's ranking
expressions (named "WordPress: …"). Newer first is Turing's recency form
(`publication_date` = `asc`). Sticky posts first matches the `sticky` field every
document now carries. A post type "down" is `type IS NOT`, since Turing boosts and never
sinks. `POST viglet-dumont/v1/tuning/boosts` calls it, and `TuningTest` covers the
payloads. Saving a ranking expression over the API key used to answer 500 in Turing's
auditor. Turing T1126 shipped: an API-key request now audits as the key's owner.

**What it should do.**
- Check `set_boosts()` live against the stack, then add a Boosts card to the screen:
  "Newer first", "Sticky posts first", and Up, Normal or Down per post type, saved
  together and read back from `boosts()`.
- The Try it preview shows the change, as it does for synonyms.
- Specs: newer first puts Workshop on testing (10 January) ahead of Workshop on search
  (1 January) for `workshop`; sticky moves a seeded sticky post up; switching both off
  restores relevance order.

### XLIX.44 Search abandonment and GA4's recommended events (D188)

**What happens.** The search block and the instant search report searches and result
clicks (D176) through workarounds in `src/public/search.js`. Clicks go out through a
hand `fetchRaw` with `keepalive`. Search events are re-emitted by `trackSearch()`,
because the controller's analytics option wrote a cookie before consent. GA gets only
the `turing_*` names, and no event says "searched and left". Turing has shipped all of
it. The SDK's clicks are `keepalive` with no CSRF prime (T1128). The controller takes a
`consent` function and `sessionCookie: false`, and skips instant-mode searches (T1129).
`googleAnalyticsSink({ recommended: true })` also sends `search` and `select_content`,
and the controller's opt-in abandonment emits `turing_search_abandoned` (T1130).

**What it should do.**
- Hand the analytics bus to the search controllers, with `consent: trackingAllowed`,
  `sessionCookie: false` and abandonment on. Then drop `trackSearch()` and the hand click
  call for the SDK's `trackResultClick`.
- Turn on the sink's recommended mode.
- Specs in `tests/e2e/site/analytics.spec.mjs`, kept as they are, plus:
  - leaving after a search sends one abandonment;
  - a result click sends none;
  - the stubbed gtag receives `search` with `search_term`.

### XLIX.46 Partitioned API keys in place of client-side site filters (D190)

**What happens.** Several WordPress sites share one SN site, told apart by `site_key`
(D148). The plugin keeps them apart itself:
- every visitor search sends `fq=site_key:<key>`;
- `Viglet_Dumont_Indexer::delete_all` pages through a filtered search and deletes one id
  at a time, and D149's orphan sweep lists ids the same way;
- `Viglet_Dumont_Related::neighbours()` asks for 50 similar documents and drops the other
  sites' ones.

The stored API key is not bound to the site key, so it can read and delete every other
site's documents. Turing has shipped what replaces this. An API key can be bound to a
partition: its searches see only that partition, its imports are stamped and refused
outside it, and endpoints that do not enforce partitions refuse it (T1133). An import
DELETE can name shared fields (T1112). `/search/similar` takes `fq[]` (T1132).

**What it should do.**
- Provisioning creates or asks for one partitioned key per site key, and stores it in
  place of the shared one. Health reports an endpoint the key is refused at.
- Delete-all becomes one field delete. The sweep and Related drop their client-side
  filtering, and Related asks for exactly the count it shows.
- The visitor-side `fq=site_key:` stays only where a call is keyless.
- Specs: on a multisite stack (`E2E_MULTISITE=1`), site A's key never reads, lists or
  deletes a site B document.

### XLIX.47 The AI-off answer spec depends on the stack's AI state (D191)

**What happens.** `site/answer.spec.mjs` "a site whose AI is off (this stack's real
Turing)" assumes the Sample site answers with "AI is not enabled for site". The stack's
`docker-compose.yml` passes `OPENAI_API_KEY` through, and a Turing boot that finds no
LLM instance creates one with a default agent, so on any machine whose shell carries the
key (the same one `run-commit.cmd` needs) the panel shows and the default suite fails.
The state persists in the `turing` volume: unsetting the key later does not turn it off.

**What it should do.**
- The default suite pins the state it asserts instead of inheriting it: either the spec
  routes the answer request to Turing's own "not enabled" event, as the other answer specs
  route theirs, or global-setup switches the site's generative AI off (and the live-LLM
  suite, `playwright.ai.config.mjs`, switches it back on for itself).
- Prefer the route: it keeps the default suite independent of Turing's AI settings, which
  D186 is about to let the manifest change.
- Done when `npm run test:e2e` passes on a stack started with `OPENAI_API_KEY` set.

### XLIX.48 Selects keep room for their arrow (D192)

Seen by the owner on the plugin's settings page, Actions tab (2026-10-26): the "Index
All Content" row's content select (`#viglet-dumont-index-type`, "All Content" / "Posts
Only" / "Pages Only") draws its text under its own dropdown arrow, so "All Content"
reads cut and overlapped by the chevron.

Cause: WordPress core styles every admin `select` with the arrow as a background image
on the right and a right padding that keeps the text clear of it.
wordpress/src/admin/css/admin.css overrides that padding on all four sides:

- line 340, `.viglet-dumont-action-buttons select { padding: 6px 10px; ... }` (the Actions tab);
- line 184, `.viglet-dumont-field select { padding: 8px 12px; ... }` (every select in the
  Connection, Indexing and Search tabs), the same flaw wherever a value is long enough.

Build: keep core's right padding by setting only the other sides, or give the right side
room for the arrow, e.g. `padding: 6px 28px 6px 10px` and `padding: 8px 32px 8px 12px`.
Give the action select the button's height beside it (the Start Indexing button is
taller than the select in the screenshot) so the row lines up.

Done when the browser suite opens the Actions tab and the Connection tab and asserts
each select's text box ends left of its arrow (the select's scrollWidth fits its
clientWidth less the right padding), in pt_BR too, where "Todo o conteúdo" is longer.
