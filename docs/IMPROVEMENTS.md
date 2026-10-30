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

A sort item over an undeclared field is refused at build, as a facet's is. Check the
release on Central, not `~/.m2`: a local Turing `mvn install` overwrites 2026.3.6 with
classes the release lacks, so a build passes here and fails in CI.

Done when a test schema declaring a `missingLast` sort leaves it on a fresh site, and a
second run reports nothing changed.

## XLIX The WordPress connector plugin

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

### XLIX.52 A public hosted demo Turing (D196)

**What happens.** The demo store runs only from `run-docker.cmd demo` on a developer's
machine. D239 moves WordPress into the visitor's browser through Playground, which
resets on every load and costs nothing per visitor, but the store it boots still needs a
Turing it can reach. None is hosted.

**What it should do.**
- One public demo Turing, deployed from the repo by a workflow, not by hand, with the
  `Demo` SN site in PUBLIC mode and its index built from the seed (D238). Host and
  domain are decided in the task; an idea until they are.
- `turing.allowedOrigins` names the Playground origin (D237); no key is handed out.
- The LLM spend has a hard daily cap from Turing's cost ledger (D174's reading of it):
  past the cap the chat and answers turn off with a visible note, while search keeps
  working. Per-IP rate limits on search, chat and copilot.
- A health check and an uptime alert, and the Turing version it runs exposed for the
  store's footer.

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

### XLIX.81 A readme the plugin directory accepts (D225)

The plugin ships today as a zip on a GitHub release, and `src/readme.txt` is written for
that path. WordPress.org reviewers reject a plugin that sends data to a service without
saying so, and this one sends every published post to Turing and every search to it. The
readme needs an "External services" section naming Turing, what is sent (post content,
fields, the search query, the analytics events the SDK reports), when, and links to the
terms and privacy notice of the service the site points at.

Installation must describe Plugins > Add New, with the GitHub zip and Composer kept as
alternatives in the plugin README. The admin screens ship built by `wp-scripts`, so the
readme links the public repository for the human-readable source. `Contributors:` must
name real wordpress.org accounts. MIT is GPL-compatible and accepted; switching to
"GPLv2 or later" is optional and is decided here, not assumed.

Done when the readme validates on the wordpress.org readme validator and the package
holds nothing the directory forbids.

### XLIX.83 Releases deployed to the plugin directory (D227)

WordPress.org never reads GitHub: an approved plugin gets its own SVN repository at
`plugins.svn.wordpress.org/viglet-dumont`, and what it holds is the package, not the
history. So the monorepo stays the source and no separate repository is needed; the
directory becomes one more target of the `wordpress-v*` tag.

The `release` job in `wordpress-plugin.yml` already builds the `viglet-dumont/` folder
through `bin/build-zip.sh`, which refuses a tag that disagrees with the plugin header or
the readme's Stable tag. A step after it commits that folder to `trunk/`, copies it to
`tags/<version>`, and sends the directory's banners, icon and screenshots to `assets/`
from a folder kept outside the package. `10up/action-wordpress-plugin-deploy` does this
from a subfolder through `BUILD_DIR`; the SVN credentials live in repository secrets.

The first submission is manual and outside this task: a wordpress.org account uploads
the zip, the review team approves it, and only then does SVN exist.

Done when a `wordpress-v*` tag updates the directory listing with no step by hand.

### XLIX.85 Plugin Check warnings, fixed or annotated (D229)

**What happens.** `wp plugin check viglet-dumont` (Plugin Check, which the D226 CI job
now runs) finds no error and 23 warnings, measured on 2026-10-28:

- 18 `PrefixAllGlobals.NonPrefixedVariableFound` in `admin/views/settings-page.php` and
  `templates/search-results.php`: template variables in the global scope without the
  `viglet_dumont_` prefix the block templates already use.
- 2 `trademarked_term`: "WordPress" in the plugin name and the readme title.
- 2 `WPQueryParams.PostNotIn_*`: `post__not_in` in the indexer (line 159) and `exclude` in the
  mapping (line 663).
- 1 `load_plugin_textdomainFound`: the call in `viglet_dumont_init()`, which WordPress 4.6+
  does for a plugin hosted on wordpress.org.

**What it should do.** Fix each, or annotate it where it stands with the reason, as the
PHPCS rules are: prefix the template variables; rename to a title the directory accepts
(the trademark rules allow "for WordPress" at the end; decide it with D225's readme);
keep the two exclusions if the lists stay short and say so; keep
`load_plugin_textdomain` while the GitHub zip is a supported install, since a site not
installed from the directory loads no language pack. Then make the CI job fail on
warnings too.

### XLIX.87 Reaching Turing from WordPress Playground (D237)

**What happens.** The plugin reaches Turing from PHP (`wp_remote_*` in
`class-viglet-dumont-api-client.php`). In WordPress Playground, PHP runs in the browser
and its outbound HTTP goes through the browser's `fetch`, so every call to Turing is a
cross-origin request from `playground.wordpress.net`. Turing does not answer it with
CORS headers, and the plugin reports Turing as unreachable.

**What it should do.**
- The blueprint turns networking on (`features.networking`), without which Playground
  makes no outbound request at all.
- The demo Turing is a dedicated instance, so it sets `turing.allowedOrigins` to the
  Playground origin. That allowlist covers all of `/api/**` and needs no Turing change;
  narrowing it to SN endpoints is unnecessary because the browser holds no key (D238).
- A browser hides a CORS refusal from `fetch`, so PHP cannot tell it from a dead host.
  Inside Playground the diagnostics name CORS as the likely cause and the origin Turing
  must allow, instead of "Turing unreachable".

**Gate.** From the blueprint against a local Turing (`http://localhost` is allowed from
an HTTPS page), the diagnostics show Turing reachable and a search returns seeded
products; with the origin removed from the allowlist, they name the origin to allow.

### XLIX.88 A keyless demo mode (D238)

**What happens.** On a normal install the plugin indexes on activation and on every
save, with a Turing key stored in its settings. In WordPress Playground that wp-admin
belongs to the visitor, so any key the blueprint stores is readable by every visitor.
Turing keys carry no scope (`TurDevToken` has a partition, never a permission), so the
key would let a stranger rewrite or delete the demo site for everyone.

**What it should do.**
- No key at all. The demo SN site stays in PUBLIC mode, which already serves search,
  autocomplete, chat, copilot and answer anonymously (`TurSNSiteApiKeyGateFilter`),
  while every write needs a key the browser never holds. The plugin already sends no
  `Key` header when none is set.
- A demo mode, set by the blueprint as a wp-config constant like the other locked
  settings: activation, saves and the queue are no-ops, the indexing screens say why,
  diagnostics stop asking for a key, and features whose endpoints need one are hidden.
- The demo index is built once from the seed, by the same workflow that deploys the demo
  Turing (D196), and rebuilt when the seed changes.

**Gate.** With demo mode on, saving a product queues nothing; a test asserts every call
the plugin makes in demo mode hits a public SN path; an indexing call sent without a key
is refused by Turing.

### XLIX.89 The demo store in WordPress Playground (D239)

**What happens.** Trying the plugin means Docker (`run-docker.cmd demo`) or a WordPress
host of one's own. D196 plans a public hosted store, but it has to host WordPress and
WooCommerce, reset them, and defend wp-admin from strangers.

**What it should do.**
- A WordPress Playground blueprint (`wordpress/demo/playground/blueprint.json`) that boots
  WordPress in the visitor's browser with WooCommerce, the plugin, the demo theme and the
  seed, already configured against a hosted demo Turing, and lands on the store's front page.
- A link to it (`playground.wordpress.net/?blueprint-url=...`) on the site's WordPress page
  (D197), in the README and in posts.
- The tour fits one visit: Playground starts from the blueprint on every load, so a reload is
  the reset D196 wanted, and nothing a visitor does survives. The guided layer (D203) is on by
  default.
- wp-admin is the visitor's own, so the backstage chapter (settings, the indexing queue)
  is shown rather than closed.

**What this leaves to D196.** Only Turing has to be hosted, behind the read-only demo
key (see the two tasks this depends on). WordPress costs nothing per visitor.

**Gate.** A Playwright run opens the blueprint URL, waits for the store, searches a
seeded product and gets a result from the hosted Turing.

### XLIX.90 A kit stage offers alternatives, not a bundle (D240)

**What happens.** The live kit run on the demo (D201) staged five needs with three
products each, and for most stages the three were colours of one product: three
Blencathra tents, three Pillar boots. The block ticks every in-stock product, so "Add
the kit to cart" put 13 items in the cart for one person's weekend, $2,549.20 of it,
three tents included.

**What it should do.** A stage lists alternatives for one need, not a bundle:
- Only the first sellable product of each stage starts ticked; the others stay listed and
  unticked, so the shopper can swap one in. The cart button counts what is ticked, as now.
- Products that differ only by colour (the same title before the `, <colour>` the seed
  appends) collapse to one entry in a stage, so the alternatives are different products.
  If Turing can return one document per product group, prefer that over collapsing in
  the block.
- Out-of-stock handling is unchanged: an unsellable first product passes the tick to the
  next sellable one.

**Gate.** woo/kit.spec.mjs with a fixed reply holding two colours and two products per
stage: one ticked per stage, the colours shown once. demo-ai/kit.spec.mjs then asserts
the cart holds one product per stage.

### XLIX.91 A staged id the shop does not sell leaves the kit (D241)

**What happens.** Playing the demo's story (D203) live, Build my kit staged post 11, the
demo's "Search the shop" page, as a product. `buildKit` in `src/public/copilot.js` asks
the Store API for each staged id (`shopProduct`); for a page it gets a 404 and `null`,
and only a product that answers is checked for stock, so the page stays ticked. "Add the
kit to cart" then posts `{"id":11}` to `/wc/store/v1/cart/add-item` and WooCommerce
answers 400 `woocommerce_rest_cart_invalid_product`: the shopper reads "Added 13 of 14".

Turing's kit stages any document that is not a guide type, so any indexed page can be
staged; Turing has no option to say which types are products (filed there).

**What it should do.**
- A staged id the Store API does not know as a product leaves the kit before it is shown
  as buyable: removed from its stage, never ticked, never sent to the cart. A stage left
  empty is said in the unfilled line, as a need the catalog has nothing for.
- Once Turing takes the product types, the block sends `product` beside `guideTypes`, so
  the page is never staged and the 404 lookup is never made.

**Gate.** woo/kit.spec.mjs with a fixed reply staging a page id beside two products: the
page is not listed, "Add 2 products to the cart", and no add-item request names it.

### XLIX.92 Ask about the product on the page (D242)

**What happens.** The demo's product page carries the copilot under "Not sure it fits
your trip? Ask us", and the Site assistant waits in the corner. Neither knows which
product the page shows: `src/blocks/copilot/render.php` and
`src/blocks/assistant/render.php` send no product, so "Will this keep me dry?" is asked
of the whole catalog and may cite other jackets. The story spine (D203, chapter 6) had
to phrase its question around that.

**What it should do.**
- On a singular product (or any singular indexed post), both blocks send the document's
  Turing id with the question, and the answer is grounded on that document first: it is
  cited, and other products appear only as alternatives.
- The question box says so ("Ask about Catbells light rain jacket"), so the shopper knows
  what "this" means.
- Off a product page, nothing changes.

**Open choice.** Whether Turing's copilot and chat already take a document to ground on.
If not, file the Turing line (an optional `documentId` that is retrieved and cited
first) and make this depend on it; the block side is then a few lines.

**Gate.** A woo spec on a product page: the copilot request carries the product's id,
and the box label names the product.

### XLIX.93 The curated searches hold their order on the published Turing (D243)

**What happens.** On 2026-10-30, with the demo stack on the published Turing image
(`ghcr.io/openviglet/turing-ce:latest`, sha-b9bc, which carries T1170's stemming), three
curated searches in `demo/theme/tarnfell/curated.json` stopped showing their buying
guide first, and `npm run test:e2e:demo` fails six cases (curated and compare, en_US):

- `waterproof jacket` and `raincoat` → four colours of "Catbells light rain jacket" first;
  "How to choose a waterproof jacket" is expected first.
- `puffer` → "Down or synthetic: choosing an insulated jacket" first; "How to wash a down
  jacket" is expected first.

The guides are indexed (each found by its own title). The demo's curated order is what
the site and the tour promise, so the demo now shows something its copy does not say.

**What it should do.** Find why the order changed: the stemming release (Turing T1177,
filed beside this) or the colour variants each indexed as a product (four near-identical
documents now outscore one guide). Then either Turing restores the title-phrase weight,
or the curated entries are re-read against the new order with the reason recorded beside
them. Never edit `curated.json` to whatever comes back without that reason: it is the
demo's contract.

**Gate.** `npm run test:e2e:demo` green on the published image.

### XLIX.94 The story plays in pt_BR too (D244)

**What happens.** `npm run e2e:story` (`tests/e2e/story/shopper.story.mjs`) plays
`demo/story.md` in English and skips, with the reason, on a demo started with
`DEMO_LOCALE=pt_BR`. `story.md` already carries each chapter's Portuguese query, but
nothing checks that the Portuguese journey works end to end.

**What it should do.** The spec reads its words from a per-locale table, as
`tests/e2e/demo/tour.spec.mjs` does: the queries from `story.md`'s pt_BR lines, and the
labels a pt_BR demo shows (popular searches, "Entendido:", the compare bar, "Planejar um
kit", the checkout fields and the offline payment's translated title, the insights
cards), read off a pt_BR stack rather than guessed. Chapter 3 asks the pt_BR curated
prompt in `ai-prompts.json`, which already names its cited guide. The synonym chapter
uses a Portuguese word the catalog does not hold.

**Gate.** `run-docker.cmd demo reset`, then `DEMO_LOCALE=pt_BR run-docker.cmd demo` with
`OPENAI_API_KEY`, and `npm run e2e:story` green; English still green after switching
back.

### XLIX.95 One product per design, its colours as variations (D245)

**What happens.** `wordpress/demo/seed/catalog.php` creates one simple product per
colour ("Catbells light rain jacket, slate", "…, moss", "…, ember", "…, sky"): 132
products for about 45 designs. Turing then sees four near-identical documents per
design, and it shows everywhere the story goes (D203's video, 2026-10-30):

- Chapter 4 compares "three jackets" that are three colours of one Catbells jacket.
- Build my kit stages three colours of one tent (D240 is the block-side half).
- Four colours of the rain jacket fill the top of `waterproof jacket`, above the buying
  guide (possibly part of D243).

A real shop sells a jacket in four colours, not four jackets.

**What it should do.**
- The seed makes one variable product per design, its colours as variations of the
  `pa_color` attribute, each variation with its own D218 picture and the stock rule the
  seed uses today.
- The WooCommerce mapping indexes the parent once, colours as a multi-value facet, so
  search, compare and the kit show one card per design.
- Adding to the cart from search, the copilot or the kit: the Store API needs a variation
  id for a variable product. The block either sends the first in-stock variation or links
  to the product page to choose; decide in the task, and keep the kit's one-click promise.

**Gate.** `npm run test:e2e:demo` and `npm run e2e:story` green; the compare chapter
shows three different jackets.

### XLIX.96 Narrate the demo video and publish it (D246)

**What happens.** `npm run story:video` (D204) builds `story.mp4` and `story-30s.mp4`
from the recorded story, with brand cards and English and pt_BR captions as soft tracks,
under `wordpress/tests/e2e/artifacts/video/`. The video is silent, and nothing publishes
or embeds it: a prospect cannot meet it.

**What it should do.**
- Narration in English and pt_BR, one line per chapter read over its clip. Recorded by a
  person or synthesised; whichever is chosen, the voice's licence is recorded beside the
  script (`wordpress/demo/seed/CREDITS.md` holds the demo's other credits). The assembly
  mixes a narration file per chapter when one is present, and stays silent without.
- The release workflow attaches `story.mp4`, `story-30s.mp4` and the captions to the
  plugin's GitHub release, built on the demo stack, never committed.
- The site's WordPress page (D197) and the plugin's docs page embed the release asset with
  both caption tracks.

**Why it waits.** The voice and its licence are a person's decision, and the embed needs
a published release to point at.

**Gate.** The released video plays with narration and either caption track on the site
page; `npm run story:video` still builds a silent one without the narration files.

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.
