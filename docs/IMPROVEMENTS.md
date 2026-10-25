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
  ARIA attributes. D160 dropped the list's `listbox`/`option` roles, which wrapped a button
  in each option and failed axe (`nested-interactive`); the options must be the focusable
  items themselves, and the open list stays under the axe gate in `site/search.spec.mjs`.

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
- A spec in the live-LLM `ai` project (D174), skipped when D169's probe reports no LLM.
- On the local stack with an OpenAI key, `/answer` still says "AI is not enabled for site"
  while chat answers: the site needs its GenAI config, which only the console sets today
  (Turing T1116, surfaced by D169).

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

### XLIX.25 The admin shows and enables the site's AI features (D169)

**What happens.** Booted with `OPENAI_API_KEY`, the local Turing creates an LLM, a
default agent and an embedding store (commit 97c5e3db). The plugin cannot see any of it.
Chat on the SN site answers, `/answer` says "AI is not enabled for site" (T1116), and
the admin has no screen that says which AI features this site can use.

**What it should do.**
- An "AI" section on the Health screen: one row per feature (answer, chat, similar by
  vector, natural-language filters, copilot), each read from Turing's own probe
  (`/chat/enabled`, `/copilot/available`, `nl-filters` `available`, discovery) and
  showing its reason when off.
- Provisioning turns the site's GenAI on through the manifest once T1116 ships. Until
  then, the row names the console step and links to it, rather than working around it.
- A `wp dumont ai status` command that prints the same rows, for scripts and CI.
- Every visitor-side AI block from this track reads this probe and stays hidden when its
  feature is off, so no visitor meets a broken widget.
- Specs: the rows on a stack with a key, and on one without (every row off, with a
  reason).

Depends on Turing T1116 for the provisioning half.

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

**What happens.** The editor's Turing panel shows a post's index state and a Reindex
button (D152). Turing can do more for an author: summarise (`/api/v2/summary`), find
near duplicates (`/search/duplicates`) and similar posts, and score content against a
persona (`/persona/{id}/content-fit`). None of it reaches the editor.

**What it should do.** Add an "Assist" section to the panel, with every result a draft the
author accepts, edits or ignores. Nothing is written on its own, and nothing is
published (the D13 non-goal).
- "Draft an excerpt": a summary of the saved content through the plugin's REST route,
  which calls Turing with the stored key (never the browser). "Use" fills the excerpt
  field.
- "Already covered?": near duplicates of this post above a threshold, with links, before
  the author publishes a second copy.
- "Link ideas": similar published posts, each with "Insert link" at the cursor.
- "Fits the audience?": when the SN site has personas, a content-fit score per persona
  with its reasons.
- Each action shows its cost from Turing's token count, and is hidden when D169's probe
  says the feature is off. Capability `edit_post` only.
- Specs: draft and accept an excerpt; a seeded duplicate is flagged; an inserted link
  points at the suggested post.

### XLIX.33 A search tuning screen in wp-admin (D172)

**What happens.** Improving results needs Turing's console: synonyms, spotlights (pinned
results for a query), ranking expressions (boosts and recency) and search rules. A
WordPress owner works in wp-admin, and does not have, or want, a second login for this.

**What it should do.** A "Search tuning" admin screen, writing through the plugin's REST
routes with the stored key to Turing's own APIs:
- Synonyms: list, add and remove; "Suggest" runs Turing's synonym mining (an LLM over the
  search logs) and offers each pair to accept, never applying one on its own.
- Promoted results: pick a query and one to three posts from a post search, stored as
  a Turing spotlight. A post-edit sidebar action "Promote for a search…" reaches the same
  form.
- Boosts: switches the plugin owns, as ranking expressions: newer first (recency decay
  on the publication date), sticky posts up, chosen post types up or down.
- A "Try it" preview: the same query before and after the change, side by side, through
  the public search.
- Each item records that the plugin made it, so the screen never edits what an operator
  set in the console, and lists those separately as read-only.
- Specs: add a synonym and the search finds through it; promote a post and it heads the
  results; a recency boost reorders the seeded dates.

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

**What happens.** The Playwright suite (D159) covers search, which is deterministic.
Turing's generative features (answers, chat, natural-language filters, summaries) call a
real LLM through the stack's `OPENAI_API_KEY`, and nothing checks them: neither that
they are grounded, nor that they are fast, nor what they cost.

**What it should do.**
- An `ai` Playwright project, run by `npm run test:e2e:ai` and skipped (with a printed
  reason) when D169's probe reports no LLM. It is never part of the default run, which
  must stay free and deterministic.
- A seed of its own with known facts (a grant's deadline, an event's venue, a product's
  price), so a test asserts an answer contains the fact and cites the post holding it.
- Grounding checks: every citation links to a seeded post; a question the content cannot
  answer gets no answer, never an invented one (the project's no-fabrication non-goal);
  English and pt_BR questions.
- Robustness: a prompt-injection post in the seed ("ignore your instructions") must not
  change an answer; a slow or failed LLM shows the block's error state.
- Cost and time: each run reports tokens, dollars and the p50/p95 time to first token,
  read from Turing's token usage, and fails above a budget set in the config.
- The answers are saved beside the screens, so a reviewer reads what the model said.

Uses each feature as it lands: the answer panel (D166), natural-language filters (D170),
the editor assist (D171), the shop copilot (D173) and the site assistant.

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

### XLIX.31 Click and search analytics from the blocks (D176)

**What happens.** Turing records each search as a metric, but the search block never
tells it which result was opened. So the site has no click-through rate, no "searched
and left" signal, and Turing's ranking learns nothing from use.

**What it should do.**
- The search block, the overlay (D167), the assistant's citations (D175) and related
  content call the SDK's `trackResultClick` / `postClick` (public `POST
  /api/sn/{site}/click`) with term, document id and position. They use `sendBeacon`, so
  the navigation is never delayed.
- `createTuringAnalytics` with an optional GA4 / GTM sink, so a site already measuring in
  Google Analytics gets `search`, `select_content` and abandonment events with no code.
  A setting turns the sink on and names the measurement.
- Respect consent: no tracking until the WP Consent API (or a filter) reports the
  statistics category, and no personal data in any event.
- Specs: a click sends one beacon with the right position; with consent off, none is
  sent; the GA sink receives the events in debug mode.

The data the search insights screen reads.

### XLIX.28 A related-content block on Turing's similar search (D177)

**What happens.** Turing answers `GET /api/sn/{site}/search/similar?id=&mode=MLT|VECTOR`
anonymously. On the local stack, a seminar post returned the two other seminars.
WordPress sites use a separate plugin (or a tag query) for "related posts", which knows
nothing of the index and picks by tag overlap.

**What it should do.**
- A "Related content" block, and an optional automatic placement after the post content
  on chosen post types. It is built on the SDK's `createSimilarController`, with the
  current post's Turing id (`<site_key>:post:<id>`).
- Settings: mode (keyword `MLT`, or `VECTOR` when D169 reports embeddings, else falls
  back to MLT), how many, same post type only, and the card fields (image, date,
  excerpt).
- Server-side render of the first results (the block's `render.php` calls the API
  client), so related links are in the HTML for crawlers and no-JS visitors, then
  hydrated.
- Clicks recorded through D176's tracking, so related links show in the insights.
- Specs: a seeded post shows its seeded siblings, never itself, and a post with no
  neighbours renders nothing rather than an empty box.

Needs no LLM: a showcase piece that works on every Turing.

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
`content_gaps`), so their two tables wait on Turing T1119. Top terms are REST today, and
the rest of the screen can ship first.

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
theme's hover, focus and padding states. The same rule set styles "Did you mean" and the
suggestion buttons, so they are exposed the same way. The baseline
`tests/e2e/snapshots/win32/site-classic/search-block-facet.png` holds the wrong look.

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
