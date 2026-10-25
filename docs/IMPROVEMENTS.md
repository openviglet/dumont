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

### XLIX.6 An admin people can run without a search engineer (D150)

**What is there.** `admin/views/settings-page.php` is one long form with these sections:
Server Connection (URL, site name, key, locale, source app, ping), Content Indexing
(post types, categories, tags, comments, comma-separated custom fields, comma-separated
excluded ids, batch size) and Search Settings. A second tab has the Indexing Actions
buttons. Nothing tells an editor whether a given page is in the index.

**The consuming case.** At FESPSP the people who touch this are communications and IT
staff, not search engineers. The owner asked for a friendly interface to configure and
tune the plugin.

**What it should do.**
- **First-run setup:** server URL, key, then a "test" step that names what failed (DNS,
  TLS, 401, unknown SN site, CORS for the front end). Then pick or create the SN site,
  then the first index with live progress.
- **Content and fields:** the per-type mapping as a table per post type, with a preview of
  the document a real post produces (the JSON sent) and a provisioning status per field.
- **Health:** documents in WordPress versus in Turing per type, the last run, queued and
  failed jobs (the queue line) with retry, and the last 50 events.
- **Per post:** landed (list column and editor panel, `class-viglet-dumont-post-state.php`,
  `admin/js/editor-panel.js`, plain `wp.*` globals). Port the panel when the build exists.
- **Search:** the front-end settings of the search-blocks line.
- Built with `@wordpress/components` (React, the admin's own look), translatable, with
  pt_BR shipped, and usable with keyboard and screen reader.

### XLIX.7 Configuration from the environment (D151)

**What happens.** `viglet_dumont_get_settings()` reads everything from the
`viglet_dumont_settings` option, so the API key lives in plain text in `wp_options`. The
settings page is the only way to set it.

**The consuming case.** The FESPSP sites run as images on Japode/cloud. Their whole
configuration comes from environment variables declared in `japode.yml` (secrets
included), and the database is in every backup. A deploy has to come up already
configured, with no wp-admin step. The dev stack also needs the plugin configured by a
setup script.

**What it should do.**
- Accept constants that override the option, each shown as read-only in the admin with
  "set by the environment": `VIGLET_DUMONT_URL`, `VIGLET_DUMONT_SITE`,
  `VIGLET_DUMONT_API_KEY`, `VIGLET_DUMONT_LOCALE`, `VIGLET_DUMONT_SITE_KEY`, and a public
  search URL for the search blocks when the browser reaches Turing on a different origin than PHP
  does.
- When the key does come from the option, mask it in the UI and never echo it back.
- Keep the indexing key server-side only; the browser search never needs it.
- Have `wp dumont status` (the WP-CLI line) say where each value came from.

### XLIX.8 Search through blocks and the JS SDK (D152)

**What happens.** With `search_enabled`, `viglet_dumont_search_redirect` hooks
`template_redirect` on `is_search()`. It calls `get_header()`, includes
`templates/search-results.php` and exits. On a block theme (FSE) `get_header()` is a
classic-theme call, so the page loses the theme's header part, footer and styles. The
server-side search uses the legacy GET `/api/sn/{site}/search`, and there is no
autocomplete, no "did you mean" UI, no analytics and no generated answer, though
`@viglet/turing-sdk` ships all of them.

**The consuming case.** The FESPSP sites are block themes with their own design system.
They will build their search screens as theme blocks on `@viglet/turing-sdk` and need
the plugin to provide configuration, not markup.

**What it should do.**
- Expose a public, cache-safe config: Turing public URL, SN site, locale, and the
  mapping's facet labels and types. Provide it as a `viglet_dumont_public_config()`
  function, a REST route and a registered script module that ships the SDK bundle, so a
  theme imports one module and does not vendor the SDK.
- Ship generic, theme-neutral blocks as the default (search box with autocomplete,
  results with facets, spell-check, pagination), built with the Interactivity API. A
  site with its own blocks turns them off.
- Make the override opt-in and keep `?s=` working, so a site without Turing falls back
  to WordPress search.
- Document the CORS origin the SN site must allow.

### XLIX.9 WordPress connector docs match the plugin (D153)

**What happens.** `docs-dumont/connectors/wordpress.md` and the blog post
`2026-06-22-enterprise-search-for-wordpress` (vigletdocs) both describe settings the
plugin no longer has:
- Host, Port and Path (`/dumont`) fields;
- `viglet-turing-for-wordpress` as the folder name;
- a `DumontPhpClient/` tree;
- `Optimize` and `Index all Pages` buttons;
- batches of 250.

The code has a single Server URL field, the `viglet-dumont` folder and slug,
`includes/class-dumont-*.php`, and a batch size of 50 by default. Someone following the
guide cannot find what it names.

**What it should do.** Rewrite both after the rest of Track S land, from the shipped plugin and not
from memory:
- install with Composer and with a zip;
- the environment constants (the environment line);
- the mapping and provisioning (the mapping line);
- the WP-CLI commands (the WP-CLI line);
- the queue and its failure states (the queue line);
- the site key and unified search (the site-key line);
- the search blocks and the SDK config (the search-blocks line).

Keep one screenshot per admin screen. Until then, add a short "outdated" note at the top
of the page.
