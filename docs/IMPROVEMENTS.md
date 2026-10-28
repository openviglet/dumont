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

Waits on Turing T1159, the call that returns the gaps and stages; the plugin draws them
and fills the cart, passing the stock field as the call's availability field. Seen in
cursarei's profile analysis, which returns a staged path of real courses with the same
id check.

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

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

### L.2 The area themed from the design system (D231)

**What happens.** Starlight's default theme is its own: grey ramps, a teal-ish accent,
its own header. roadkeep only overrides the `--sl-color-*` ramp, which is enough for a
tool page but would make `/docs` read as a different product from the page one click
away. The Docusaurus pages today carry inline-styled cards (`index.mdx`'s `Pill`,
`FeatureCard`, `NavCard`) against `--color-*` variables that only that theme declares.

**What it should do.** Theme the area from `@viglet/viglet-design-system`, not by hand:
- Import `preset.css` / `tokens.css` / `fonts.css` and map every `--sl-color-*`, font and
  radius to the `--vg-*` tokens, light and dark, so a token change in the design system
  reaches the docs with no edit here.
- Starlight's `data-theme` and the site's mode toggle agree: one stored choice, no flash
  of the other palette between `/` and `/docs`.
- Override Starlight's `Header` and `Footer` components to render the same brand, nav
  links and footer as `dumont-site`, so the area is a section of the site rather than a
  neighbour.
- The index page and the cards it needs are `.astro` components on the design system's
  site layer (`site.css`, VDS214), not inline styles; asides, tabs, code blocks and tables
  styled to the same tokens.
- Readable at a 320px phone: no horizontal page scroll, wide tables scroll in their box.

**Gate.** Screenshots of the index and one long reference page, light and dark, desktop
and 320px, reviewed before and after; no colour literal in the area's CSS outside a
token mapping.

### L.3 The docs-dumont pages move into the area (D232)

**What happens.** The 20 pages of `docs-dumont` (getting started, installation,
configuration reference, architecture, indexing plugins, connectors, developer guide,
REST API, extending) are written for Docusaurus.

**What it should do.** Move them into `dumont-site/docs/src/content/docs/` with their
content unchanged and their Docusaurus-isms converted:
- Frontmatter: `sidebar_position` / `sidebar_label` / `slug` / `id` become Starlight's
  `sidebar` keys; `index.mdx` drops `@docusaurus/Link` and its inline components for the
  L.2 ones.
- Admonitions (`:::note`, `:::tip`, `:::warning`) checked against Starlight's aside syntax.
- The 15 images under `/img/diagrams/` and `/img/screenshots/dumont-*` copied into the
  area's assets and referenced relatively, so the base is applied.
- Links to `/turing/*` become absolute `https://docs.viglet.org/turing/...` - they leave
  this site; internal links become relative.
- The sidebar written from `sidebars-dumont.ts`, same order and groups.
- Every page keeps its URL tail (`/dumont/rest-api` becomes `/docs/rest-api`), which is what
  makes the redirect in L.6 a prefix rewrite instead of a table.

**Gate.** `starlight-links-validator` passes (no broken internal link or anchor), every
page and image from `docs-dumont` has a counterpart, and a diff of the rendered text
shows no content lost.

### L.4 The site points at its own docs (D233)

**What happens.** `site-content.ts` points `docs`, `install` and `rest` at
`docs.viglet.org/dumont/...`, the footer names `docs.viglet.org`, and the site's
`sitemap.xml`, `llms.txt` and Markdown twins know nothing about a docs area.

**What it should do.**
- Every docs link in `site-content.ts` and the footer resolves inside `/docs`.
- `sitemap.xml` lists the docs pages (Starlight's sitemap merged with the site's, or one
  sitemap index over both); `llms.txt` points at the docs.
- Each docs page gets a Markdown twin at the same address, built from the render the way
  roadkeep's `twins.mjs` does, so the site's twin rule holds across both builds.
- The root `README.md` and `agents.md` (`dumont-site` row) say where the docs live.

**Gate.** A link check over the assembled `_site` reports no link to
`docs.viglet.org/dumont`, and every docs page has an `index.md` twin.

### L.5 Shipping writes the docs here (D234)

**What happens.** The shipping rule sends every user-facing change to
`vigletdocs.github.io/docs-dumont` as a separate commit in another repo, gated by that
repo's `npm run build`. Once the area exists, two copies of the docs would drift on the
first ship that edits only one.

**What it should do.**
- The `dumont-roadmap-docs` skill's "Propagating to end-user documentation" and "When a
  track completes" point at `dumont-site/docs/`, gated by the area's build; the change and
  its page land in **one** commit, which removes the two-repo step.
- `agents.md` names the area in Project Structure and Key Paths.
- `docs-dumont` in vigletdocs is frozen: its index says where the docs moved, and no page
  is edited there again. Removing it from the Docusaurus config waits for L.6, so its
  URLs keep answering until the redirect does.

**Gate.** No rule in the skill or `agents.md` names `docs-dumont` as a place to write.

### L.6 docs.viglet.org/dumont redirects to /docs (D235)

**What happens.** Search engines, the Turing and Shio docs and old links all point at
`docs.viglet.org/dumont/...`. Removing those pages without a redirect turns each into a
404.

**What it should do.** Redirect `docs.viglet.org/dumont/*` to
`dumont.viglet.org/docs/*`, path for path (L.3 keeps the tails identical, so this is a
prefix rewrite). GitHub Pages has no server-side redirect, so each old route becomes a
static page with `<link rel="canonical">` and a `meta refresh` (or Docusaurus'
client-redirects plugin), and then `docs-dumont` leaves the vigletdocs build.
Cross-links from the Turing and Shio docs move to the new address in the same pass.

**Gate.** Every route `docs-dumont` published answers with a redirect to its
counterpart, checked from the old sitemap.
