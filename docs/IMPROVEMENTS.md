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

### XLIX.26 A live-LLM browser suite for the AI features (D174)

**What happens.** The live-LLM suite runs (`npm run test:e2e:ai`, `tests/e2e/ai/`): a
seed of known facts in English and Portuguese and a prompt-injection post, deleted again
by the teardown; chat, answer, natural-language filter (D170), excerpt-draft (D171),
insights summary (D178) and compare summary (D200) specs; answers saved to
`tests/e2e/artifacts/ai/answers.json`. Each run reads Turing's `cost/summary?site=`
before and after (`ai/cost.mjs`), prints the tokens, dollars and calls, and fails over
`AI_TOKEN_BUDGET` or `AI_COST_BUDGET`, or when any call went unpriced. Against Turing
2026.3 with T1146-T1150, 11 of 12 pass: chat answers from the seeded posts, in
Portuguese too, and resists the planted instruction; a run spends about 8,400 tokens and
$0.0014.

**What it should do next.**
- The answer panel's spec fails on Turing's stream, not the plugin: each `token` event is
  raw text, so a token's leading space is the one an SSE client strips, and the panel reads
  "Applicationsfortheresearchgrantcloseon14March2026." (Turing T1153). Once that ships and
  the stack runs the image, the spec passes unchanged.
- The shop copilot (D173) and the site assistant (D175) join the suite as each lands.
- The teardown returns once the queue is sent, before Turing commits the deletions: a
  default run started right after read "5 results" for 4 in `site/url.spec.mjs`. Wait for
  the seeded posts to leave the index before returning.

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

### XLIX.35 A showcase site, admin tab and recorded tour (D179)

**What happens.** Each Turing feature the plugin brings to WordPress lands as its own
block or screen. Nobody can see them together: the Docker stack starts with a test seed,
the docs have one screenshot per screen, and a prospect cannot try any of it.

**What it should do.**
- `run-docker.cmd demo`: the stack on the demo store's theme, catalog and
  journal (D193, D194), and a "Showcase" page holding every block. The page has a short label per
  block naming the Turing feature behind it.
- An admin "Showcase" tab listing each feature: on or off (from D169), the block that
  shows it, and a link to the page section.
- A guided tour, `npm run e2e:tour`, that drives the showcase and records a video and
  screenshots per feature. It is the source for the docs' per-feature screenshots;
  the site and the demo video follow the shopper's story instead (D203, D204).
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

The published `@viglet/turing-sdk` is still 2026.3.9 (24 August), which has none of
T1128-T1130; the plugin copies the SDK from `node_modules` (`bin/copy-sdk.mjs`), so this
waits on a release (Turing T1142, deferred until a maintainer publishes; its pnpm
check:unreleased counts what waits).

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
(D148). Delete-all is one delete by that key and Related filters by it in Turing (D190
part), but the stored API key is not bound to the site key, so it can still read and
delete every other site's documents. Visitor searches send `fq=site_key:<key>`, and
D149's orphan sweep lists ids through a filtered search.

Turing binds a key to a partition (T1133, `POST /api/dev/token` with `partitionField`
and `partitionValue`), and its guard answers such a key 403 everywhere except search,
autocomplete, similar, clicks, the metrics reports and imports. The plugin also needs
the manifest and plan (fields), `/api/sn` and the tuning APIs, `/api/v2/summary` and the
AI probes, so a partitioned key cannot simply replace the shared one.

**Open decision.** Either the network admin keeps the shared key (provisioning and
tuning done once for the network) and each site holds only its partitioned key, or a
shared site gives those screens up. Turing could instead enforce partitions on more
routes.

**Then.**
- Each site stores its partitioned key; features its key cannot reach turn off, Health
  names each, and none falls back to an unbound key.
- Specs: on a multisite stack (`E2E_MULTISITE=1`), site A's key never reads, lists or
  deletes a site B document.

### XLIX.52 A public hosted demo store (D196)

**What happens.** The demo store runs only from `run-docker.cmd demo` on a developer's
machine. A sales pitch, a post or the site can show a video of it, never the thing.

**What it should do.**
- One public URL serving the demo stack (WordPress, WooCommerce, Turing, the theme and
  seed), deployed from the repo by a workflow, not by hand. Host and domain are decided in
  the task; an idea until they are.
- Reset to the seed nightly and on every deploy, so a visitor's cart or a vandal's edit
  never lasts.
- Visitors browse, search, chat and add to cart, or follow the shopper's story
  with the guided layer on (D203); checkout runs in WooCommerce's test mode
  with a banner saying no order is real and no payment is taken, and asks for no card.
  wp-admin is closed to the public; a read-only demo login, if any, is a later decision.
- The LLM spend has a hard daily cap from Turing's cost ledger (D174's reading of it):
  past the cap the chat and answers turn off with a visible note, while search keeps
  working. Per-IP rate limits on search, chat and copilot.
- A health check and an uptime alert, and the Turing and plugin versions it runs shown in
  the footer.

**Why an idea.** It is the one task in the series that costs money every day and has an
outside attack surface, so its host, budget and owner are decided before it is designed.

### XLIX.53 A WordPress and WooCommerce page on the site (D197)

**What happens.** `dumont-site` has a landing page, a connectors page and an AEM page
(`src/pages/aem.tsx`). The WordPress plugin, which now carries search, facets, answers,
chat, a shopping copilot and insights, has no page, and nothing links to a demo.

**What it should do.**
- `src/pages/wordpress.tsx`, routed and prerendered like `aem.tsx`, with its copy in
  `site-content.ts`: a hero naming the outcome for a store owner with the
  30-second cut of the demo video (D204), then the page told as the story's chapters
  (D203): one section per chapter, its still or clip taken from the story recording, the
  generative ones (answer, kit, chat) shown with their sources, ending on the
  native-versus-Turing compare and the full video.
- Calls to action: try the live demo store (once the hosted demo exists; until then the
  video and the Docker one-liner), install the plugin, read the docs page.
- English and pt_BR, matching how the rest of the site handles languages.
- A WordPress tile on the landing and connectors pages pointing at it.
- The `dumont-site` rule binds: every claim is something the story spec shows
  working. A capability it cannot film is not on the page.

**Gate.** The site's build and prerender pass, the page's images come from the story
recording rather than hand-made mock-ups, and a link check covers the new calls to
action.

### XLIX.54 Turing's generative AI on the demo store (D198)

**What happens.** The plugin carries Turing's generative features as blocks (the answer
slot on results, the shopping copilot, the RAG chat once D175 lands), but the demo tasks
treat them as one marker among many. On a store, they are the headline.

**What it should do.**
- `run-docker.cmd demo` turns the SN site's GenAI on through provisioning (D186), so a
  fresh demo stack answers without a console visit when a key is given, and says plainly
  on the page when none is.
- Results: the cited answer above the products for a question-shaped query ("what do I
  need for a winter hike?"), its citations linking the guides and products it read.
- A site-wide chat launcher in the theme answering from the catalog and the journal, with
  sources under every reply; on a product page it opens scoped to that product.
- The shopping copilot on product and results pages: a need described in words becomes a
  short list of products with reasons, and its add-to-cart action fills the real cart.
- Curated prompts in the seed, in English and pt_BR, each with the sources its answer must
  cite, plus one off-catalog question the assistant must decline rather than invent.
- Every generative element is labelled as AI and shows its sources; none answers without
  one.

**Gate.** The live-LLM suite (D174) runs the curated prompts on the demo seed and
asserts grounding, the declined question and the cost per run; the keyless suite asserts
the no-key notice.

### XLIX.55 Text to filters on the search block (D199)

**What happens.** The search block sends the whole sentence as the query. Price, colour
and size words compete as keywords, and the facets a shopper would have clicked stay
untouched.

**What it should do.** An option on the search block, on by default where the SN site has
GenAI:
- The query and the SN site's facet vocabulary, as closed lists, go to Turing, which
  returns the chosen values. A value outside the lists is dropped; nothing is invented.
- A parser in code recovers price ranges and sizes the model left out, and runs alone when
  there is no LLM, so the feature degrades instead of failing.
- The facets are set visibly and a line says "Understood: rain jackets, blue, up to R$400",
  built in code from the chosen values, never from model prose. One click undoes it.
- Answers cached by query and vocabulary version, with a daily cap.

**Turing side.** If no Turing call takes a closed vocabulary and returns choices (its
roadmap rules out free-form JSON output in favour of tool-calling schemas), the Turing
task is filed first through the friction skill and named in this line's deps.

Seen in cursarei's `nlFilters`, which proved the pattern on courses.

**Gate.** Specs on the demo seed: each curated sentence sets its expected facets, an
off-vocabulary word sets none, and the keyless path still parses price and size.

### XLIX.57 A kit builder from a described plan (D201)

**What happens.** The shopping copilot turns a need into a short list, one need at a
time. "Two days crossing the mountains in July, first time" is several needs, and the
shopper has to know them all to ask.

**What it should do.** A "Build my kit" mode of the copilot block:
- The plan goes to Turing, which retrieves candidates from the SN site and returns a
  summary, the gaps the plan implies (shelter, layers, water), and stages of products,
  each with a one-line reason drawn from its attributes.
- Product ids are checked against the retrieved candidates; an id the model invents is
  dropped, and an out-of-stock item is replaced or marked. A guide from the journal that
  covers the plan is cited.
- "Add the kit to cart" adds every staged item through the copilot's add-to-cart action,
  and the shopper can remove items first.
- Without GenAI, the mode is hidden rather than faked.
- Prices and totals come from WooCommerce, never from the model.

Seen in cursarei's profile analysis, which returns a staged path of real courses with
the same id check.

**Gate.** Live-LLM specs on the demo seed: each curated plan returns only seeded,
in-stock ids, covers its listed gaps, and the cart total equals WooCommerce's.

### XLIX.59 One shopper's story through the demo store (D203)

**What happens.** Each demo task delivers a feature, and the guided layer names them one
by one. Nothing orders them into a journey, so the tour, the site and the docs would
each pick a different sequence.

**What it should do.** One scripted journey, the spine every other surface follows. A
fictional shopper preparing her first winter mountain crossing:

1. **Arrives** — trending chips show the catalog at a glance (D202).
2. **Says what she needs** — "light rain jacket under R$400"; the facets set themselves
   and say what they understood (D199), and a misspelling still finds the jacket.
3. **Asks** — "what do I need for two days in the cold?"; a cited answer quotes a guide
   and its products (D198).
4. **Compares** — three jackets side by side, with a neutral summary (D200).
5. **Builds the kit** — the plan becomes a staged kit of in-stock items (D201).
6. **Checks one item** — the product page, Related and the chat scoped to it (D198).
7. **Buys** — the kit lands in the cart, checkout in test mode.
8. **Backstage** — the same query natively versus Turing (D195), then wp-admin's insights
   with her searches and a failed one turned into a synonym (D178).

Delivered as `wordpress/demo/story.md` (script, queries, expected results, English and
pt_BR), an `npm run e2e:story` spec playing it end to end, and the guided layer's
markers renumbered to these chapters.

**Gate.** The story spec runs green on a fresh demo stack with a key.

### XLIX.60 A narrated demo video of the story (D204)

**What happens.** The plugin has screenshots per screen and, once D179 lands, a raw
recording per feature. Neither is something to put at the top of a landing page or send
to a prospect.

**What it should do.**
- A two-to-three-minute video following the story's chapters in order, ending on the
  backstage chapter and a call to action (try the demo, install the plugin).
- Recorded, not mocked: `npm run e2e:story -- --record` plays the story at a human pace on
  the demo stack at 1920×1080, with cursor highlight and typing visible, and writes one
  clip per chapter. Every frame is the product running.
- An assembly script (ffmpeg) joins the clips with chapter titles, captions and an intro
  and outro card in the demo brand, so a re-record after a UI change is one command, not
  an editing session.
- English and pt_BR: captions in both, narration recorded or synthesised (decided in the
  task, with the voice's licence recorded). A 30-second cut for social posts.
- Output under `wordpress/demo/video/` (sources and script in the repo, rendered files as
  release assets, not committed), embedded on the site's WordPress page and the docs.

**Gate.** The assembly runs from a clean checkout on the demo stack, and a check fails
when a chapter clip is missing or older than the story spec it was recorded from.

### XLIX.73 The demo catalog in Portuguese (D217)

**What happens.** The demo storefront's catalog and journal (`demo/seed/catalog.php`,
`demo/seed/journal.php`, D194) are English only. With the site switched to pt_BR, the
theme and the plugin speak Portuguese while every product and guide stays in English,
and a Portuguese search ("jaqueta impermeável") finds nothing.

**What it should do.**
- A Portuguese copy of each product name, short description and description, and of
  each guide, written for Brazilian shoppers (litres, °C and grams stay; sizes stay).
- Kept beside the English in the seed's own data, so one run seeds both. Either the site
  in pt_BR seeds the Portuguese, or both are indexed with their locale and Turing's
  `_setlocale` picks; choose whichever the SN site's locales already support (the plugin
  indexes with the site's locale today).
- Portuguese synonyms ("capa de chuva", "jaqueta impermeável"; "mochila", "cargueira").
- Curated searches in Portuguese in `curated.json`, checked by a `demo` spec run with
  the site in pt_BR.

### XLIX.74 Licensed images for the demo catalog (D218)

**What happens.** The demo storefront's 132 products (`demo/seed/catalog.php`, D194) and
its journal have no images, so the search block's grid layout, the product pages and the
compare table show text alone.

**What it should do.**
- An image per product model (colours may share one, or tint it) and per guide, whose
  licence allows redistribution in this repository: public domain, or made for the brand
  (illustrations or renders of fictional products). No photo of a real person and no real
  brand's product.
- The source and licence of each in `demo/seed/CREDITS.md`.
- Imported by the seed as attachments, set as product and featured images, so the plugin
  indexes them as each document's `image`.
- Kept small (WebP, about 800 px wide), so the demo's Lighthouse scores hold (90 or more,
  D193).
- A `demo` spec finds an image on every result of a curated search.

### XLIX.75 The demo store on its own WordPress (D219)

**What happens.** `run-docker.cmd demo` turns the one WordPress the browser tests use
into the Tarnfell store (theme, WooCommerce, catalog and journal from `demo/seed/`), and
the next `npm run test:e2e` turns it back. The demo and the tests share one database and
one SN site in Turing, so the demo is never up while the tests run, and each side
carries code to undo the other: `global-setup.mjs` drafts the demo's pages and deletes
its journal and synonyms, `woo-seed.php` drafts the `TF-` products, `catalog.php` drafts
the tests' jackets. Pages the demo indexed have turned up in the default suite's counts.

**What it should do.**
- A `demo` profile in `docker-compose.yml`, beside `multisite`: `wordpress-demo`,
  `db-demo`, `cron-demo` and `cli-demo` with their own volumes and the same mounts, on
  `WP_DEMO_PORT` (default 38082). The same Turing, with its own SN site (`SN_DEMO_SITE`,
  default `Demo`) and API key, and the demo's origin in `TURING_ALLOWEDORIGINS`.
- `run-docker.cmd demo` starts both, installs and provisions the demo WordPress on first
  run as `bin/turing-setup.ps1` does for the default one, and seeds it, leaving the default
  site untouched. `run-docker.cmd demo reset` drops only the demo's volumes.
- README's "Try it in a real WordPress" describes both sites and their ports.

**Done when** both answer at the same time on their two ports, and a search on one never
returns a document of the other.

### XLIX.76 The demo specs on the demo's own WordPress (D220)

**What happens.** The browser suite's `demo` project (`playwright.config.mjs`) is a
variant step: `switch:demo` activates Tarnfell and WooCommerce on the default WordPress
and reseeds the catalog (about 1.6 minutes a run), and the next run's `global-setup.mjs`
undoes it. A `demo` spec run without its switch tests the default site and fails
confusingly, and every default spec depends on that clean-up having worked.

**What it should do,** once the demo has its own WordPress (D219):
- The `demo` project runs against the demo's URL (`WP_DEMO_URL`, default
  `http://localhost:38082`) with no `switch:demo`, and its own `seed` read from that site;
  it seeds only when the catalog is missing, so a run does not pay for it again.
- Remove what only existed to share one site: the demo pages, journal and synonym clean-up
  in `global-setup.mjs`, the `TF-` drafting in `tests/e2e/fixtures/woo-seed.php`, the
  test jackets' drafting in `demo/seed/catalog.php`, and the extra `switch.setup.mjs`
  timeout if nothing else needs it.
- A `test:e2e:demo` script that runs the demo specs alone, while the demo stays up.

**Done when** `npm run test:e2e` passes with the demo profile running and serving its
own search, and `test:e2e:demo` passes without touching the default site.

### XLIX.77 Global setup refuses a WP_URL that is not the stack (D221)

**What happens.** `global-setup.mjs` seeds the stack through `docker compose run cli
wp`, which always reaches this compose project's WordPress, but the browser goes to
`WP_URL`, defaulting to `http://localhost:8080`. A stack started with another `WP_PORT`
(38081 here, while another project's WordPress held 8080) is seeded correctly and never
visited: every spec loads the other site, and the failures read as plugin bugs (D210's
run showed `/?p=9` redirecting to that site's image attachment, and all four Related
cases red).

**What it should do.** Global setup already runs WP-CLI before anything else. Read `wp
option get siteurl` there and compare its origin with the projects' `baseURL`; on a
mismatch, stop before seeding with one message that names both and says to set `WP_URL`
(or start the stack on that port). The multisite pair does the same against `WP_MS_URL`
when `E2E_MULTISITE` is set. Do not derive `baseURL` from the stack silently: the config
is read before global setup runs, and a second source of the URL is the drift this
removes.

**Done when** `WP_URL` pointing at a WordPress that is not the stack fails global setup
with that message, and a matching one runs as today.

### XLIX.78 No Related box in a generated excerpt (D222)

**What happens.** With "Related after posts" covering products (the woo suite turns it
on since D212), a WooCommerce product without a short description shows, under its
price, "A Red jacket for the field. Related posts Wool jacket October 26, 2026 Compare
Linen jacket ..." on Twenty Twenty-Five. The summary is generated from the content, and
`wp_trim_excerpt()` runs `the_content` on the main query's post inside the loop, so
`Viglet_Dumont_Related::append()` adds the box and the trim keeps its text with the tags
stripped. A post whose template shows `core/post-excerpt` gets the same tail.

**What it should do.** `append()` returns the content untouched while an excerpt is
being built: `doing_filter( 'get_the_excerpt' )` (the hook `wp_trim_excerpt` is called
from), and WooCommerce's `woocommerce_short_description` path if its summary is built
another way (check which one Twenty Twenty-Five's single-product template reaches). Unit
test beside D210's in `RelatedTest`; spec in `woo/compare.spec.mjs` or a woo product
page spec: the summary under the price holds no "Related posts".

**Done when** a product page with the setting on shows the Related box once, in the
description, and the summary carries none of its text.
