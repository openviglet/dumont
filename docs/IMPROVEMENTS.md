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

## XLIV Frontend build and lint gates

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

## XLIX The WordPress connector plugin

### XLIX.83 Releases deployed to the plugin directory (D227)

WordPress.org never reads GitHub: an approved plugin gets its own SVN repository at
`plugins.svn.wordpress.org/viglet-turing-search` (the slug since D273), and what it
holds is the package, not the history. So the monorepo stays the source and no separate
repository is needed; the directory becomes one more target of the `wordpress-v*` tag.

The `release` job in `wordpress-plugin.yml` already builds the `viglet-turing-search/`
folder through `bin/build-zip.sh`, which refuses a tag that disagrees with the plugin
header or the readme's Stable tag. A step after it commits that folder to `trunk/`,
copies it to `tags/<version>`, and sends the directory's banners, icon and screenshots
to `assets/` from a folder kept outside the package.
`10up/action-wordpress-plugin-deploy` does this from a subfolder through `BUILD_DIR`;
the SVN credentials live in repository secrets.

The first submission is manual and outside this task: a wordpress.org account uploads
the zip, the review team approves it, and only then does SVN exist.

Done when a `wordpress-v*` tag updates the directory listing with no step by hand.

### XLIX.125 The video embedded from YouTube, with its transcript (D282)

**What happens.** D246 shipped with its criterion open: the video is neither attached to
a release nor embedded anywhere. Its plan was to embed the release asset, but GitHub
serves a release asset as a download, not a stream, so a `<video>` pointing at it loads
badly. The video goes to YouTube instead, published by Alexandre on the Viglet channel,
and the plugin's pages embed that.

**What it should do.**
- This site's WordPress page and the plugin's docs page embed the English YouTube video,
  with the transcript (the narration of D278) as text below it: assistants and search read
  text, not video.
- The plugin README carries `story-30s.mp4` (under 1 MB) as an inline player, uploaded
  through GitHub's editor.
- The release workflow attaches `story.mp4`, `story-30s.mp4` and the caption files to the
  plugin release, for anyone who wants the file.
- `src/readme.txt` carries the YouTube link, which the wordpress.org directory embeds once
  the plugin is listed (D227).
- The pt-BR video is linked from the English description and the reverse, and is not
  embedded on the English pages.

**Done when.** The site page and the docs play the English video with captions and show
its transcript, the README plays the 30 s cut, the release carries the files, and the
D246 criterion is met.

### XLIX.145 Search across sibling site keys (D303)

**Where it came from.** FESPSP runs three WordPress sites (institutional, projects
microsite, Instituto Walter Leser) and wants all three in **one** shared SN site (D148
makes that safe), searched from the institutional site only. Every search path pins the
own key: `class-viglet-dumont-native-search.php` lines 326, 546 and 687 send
`fq[]=site_key:<own>`, `public-config` returns one `siteKey`, and D287 confines the AI
features to it. No workaround written; FESPSP waits on this line.

**What the plugin should do.** A setting "Also search these sites" (`search_site_keys`, list of site keys plus a label each, settable by constant `VIGLET_DUMONT_SEARCH_SITE_KEYS` like D151) that every read path honours together with the own key:
- native search, the search block (server render and store), the overlay, trending chips, Related (opt-in per block, `sites: own|all`);
- the assistant, copilot and nl-filters, which send the set as `filters` (T1213 already takes a map);
- `site_key` offered as a facet with the configured labels ("FESPSP", "Projetos", "IWL"), and a small origin badge on result cards from another site;
- the assistant's `navigate` tool accepting the sibling hosts, never any other host.

Writes stay on the own key only: index, sweep, delete-all and Tuning never touch a
sibling's documents. With a partitioned key the browser search is PUBLIC, so the key
limits nothing here; say so in the README.

**Done when:** the multisite `network` spec gains a case where site A lists B and finds
B's documents, and B (not listing A) still finds only its own.

### XLIX.146 Results page on another site (D304)

**Where it came from.** In the FESPSP setup (see the federated-search line filed just
before this one) the projects microsite and the IWL index into the shared SN site but
have no search of their own: their header field must send the visitor to the
institutional site's search page. The plugin assumes the results page is local. The
overlay's Enter goes to `search_page` (a local page id), else the first local page
holding `wp:viglet-dumont/search`, else `home_url('?s=')`; a box-only search block
(D208) also targets a local page; and the core/search block posts to the own `?s=`.

**What the plugin should do.** A setting "Results page on another site" (`search_url`, absolute URL, constant `VIGLET_DUMONT_SEARCH_URL`) that, when set:
- makes Enter in the overlay, the box-only block and every core/search form go to `<search_url>?q=…`;
- keeps the overlay's instant results (they already come from Turing, and with the federated line they can include the target site's documents);
- turns off native search replacement on this site, since its own `?s=` page is no longer the results page, and says so on the Settings screen;
- appears in `public-config` so a theme's own field can read it.

An index-only site is then: indexing on, `search_url` set, blocks optional. Nothing else
changes for a site that leaves it empty.

**Done when:** a spec on the multisite stack sets `search_url` on the second site and a
search typed there lands on the first site's results page with the query.

### XLIX.147 Mapped fields on result cards (D305)

**Where it came from.** FESPSP indexes `evento` (start date, venue), `vaga` (job
opening: subtitle, deadline) and `livro` (authors, publisher, cover), and its design
(fespsp-base FB93) asks for a card per type. The search block's cards (D162) show title,
snippet, publication date, type and image; the markup is fixed in
`blocks/search/parts/*.php` and the store, with no filter on result HTML. An event card
shows the day it was *published*.

**What the plugin should do.**
- In Fields, a per-field option "Show on result cards" (`card: true`) with an order. The value travels in the search response already (mapped fields are document fields), so the store renders it as a labelled line; DATE fields are formatted in the site's locale and timezone (`wp_date` format), lists joined, URLs never shown raw.
- Per type, "Date shown on cards": publication date (default) or a mapped DATE field, so an event shows its start.
- Per type, "Image": the thumbnail (default) or a mapped image field.
- A PHP filter on the server-rendered card (`viglet_dumont_result_card`, `array $parts, array $document`) with the same keys the store uses, so first paint and hydration agree; document that the client mirrors it only through the mapping.
- The overlay uses the same date and first card field.

**Done when:** a seeded type with a DATE meta shows that date on the card, in pt_BR
format under `pt-br`.

### XLIX.148 Fixed filters on the search block (D306)

**Where it came from.** FESPSP wants section boxes: "Buscar na agenda" on `/eventos/`,
"Buscar na Editora", "Buscar nas notícias". Each searches only its slice (`type:evento`,
`editora:<slug>`, `type:post`) and show it as the context, not as a filter the visitor
removed by mistake. The `viglet-dumont/search` block has no attribute for a fixed
filter: `fq[]` comes only from the URL and the facets, so a scoped box today means a
link with `?fq[]=type:evento` that the visitor can clear and that leaks into shared
URLs.

**What the plugin should do.** A block attribute `filters` (array of `field:value`, editable in the sidebar as "Always filter by", picking from the mapped facets and `type`) that:
- is applied on every request the block makes, server render included, and on autocomplete and nl-filters (sent as `filters`, which T1213 already accepts);
- is not shown as a removable chip; the box can show it as a scope label ("em Agenda") with a link "buscar no site todo" to the site's search page with the same `q`;
- composes with the federated set of site keys when that ships;
- leaves trending chips scoped too (top terms whose results still match the scope), or hides them when that costs a call per chip.

Box-only use (D208) carries the scope to the results page as a URL scope parameter,
never as a plain `fq[]`, so the results page shows it the same way.

**Done when:** a page holding `{"filters":["type:page"]}` never lists a post, with and
without JavaScript.

### XLIX.149 Period sets for date range facets (D307)

**Where it came from.** FESPSP's agenda (`evento` type) maps its start date as DATE with
`range: true` (D164). The custom facet the plugin declares has one fixed set of periods
built for publication dates: Upcoming (NOW→), Last 7 days, Last 30 days, This year,
Before this year. For an agenda, four of the five look backwards; what a visitor asks is
"today", "this week", "this month", "upcoming" and "past". The set is hard-coded in
`class-viglet-dumont-mapping.php`.

**What the plugin should do.** In Fields, a "Periods" choice per range-enabled DATE field:
- **Past** (today's default, unchanged for existing sites);
- **Agenda**: Today, This week, This month, Upcoming, Past;
- **Custom**: up to 8 `{label, from, to}` with Turing date math (`NOW/DAY`, `NOW+7DAYS`…), validated before the manifest is sent.

Labels are translated (en, pt_BR); weeks follow `start_of_week`. The chip and nl-filters
read the chosen set, so "debates em novembro" can ground on This month only when the set
has it.

A related need: an event with a poster period runs from start to an end date. "This
week" should match an event whose span overlaps the week. Name in the section whether
that is two fields on the custom facet (start ≤ end of period and end ≥ start of period)
or a Turing addition, and file the Turing half if so.

**Done when:** with Agenda periods, a seeded event later this week is under This week
and Upcoming, and one from last month under Past only.

### XLIX.150 Documents that leave search on a date (D308)

**Where it came from.** FESPSP's job openings (`vaga`) carry `fespsp_vaga_ate`, the last
day to apply; after it the theme drops the opening from its own list, but the post stays
published, so the plugin keeps it in the index and the search keeps offering an opening
nobody can apply to. Calls for papers and enrolment windows have the same shape. The
plugin reindexes only on `save_post`, `delete_post` and stickiness changes, and offers
no "should this be indexed" filter beyond `_viglet_dumont_exclude` (a theme cron
flipping it is the workaround to avoid). Turing has no document expiry window either
(checked: nothing in SN code or CHANGELOG).

**What the plugin should do.**
- In Fields, a per-type "Leaves search on" choice: none (default) or a mapped DATE field.
- On index, a document whose date is past is deleted instead of sent; one in the future is sent and a single WP-Cron event (`viglet_dumont_expire`, args post id) is scheduled for that moment, rescheduled or cleared when the post is saved again.
- A daily sweep catches what a missed cron left (`wp dumont sweep` already compares; it should apply the rule too).
- The editor panel (post state) says "Leaves search on 12/11" and, after it, "Out of search: the date has passed".
- A filter `viglet_dumont_should_index( bool $index, WP_Post $post )` beside it, for the rules a field cannot express.

**Done when:** an opening whose date is yesterday is not found, and moving the date
forward and saving brings it back.

### XLIX.151 PDF text in the index (D309)

**Where it came from.** Much of what FESPSP visitors look for lives in PDFs: the
academic office's regulations and forms (the `fespsp/doc-list` block links media-library
PDFs), the library's rules, and the projects microsite's publications (`publicacao`
type, whose `fespsp_arquivo_url` meta points at the paper's PDF). The plugin indexes
post content only: `text` is the rendered `the_content`, attachments are not an
indexable type, and a PDF URL in meta is sent as a string at most. A paper's own words
are never searchable.

**What the plugin should do.** Pick the seam, and name it in the design:
- **Attachments as documents**: "Index PDF files" under Settings → Content, indexing `application/pdf` attachments that are attached to (or linked from) a published, indexed post. The document is the PDF: title, the file URL, the parent post as `section`, and the text extracted by Turing on import (if `/api/sn/import` cannot take a binary today, file the Turing half: Tika already sits in Turing's ingestion for other connectors, check `turing-app` before assuming).
- **PDF text into the parent**: a mapping source `file:<meta key>` that appends the referenced PDF's text to the parent document's `text`, for the `publicacao` case where the PDF is the content.

Limits to state: size cap, only the site's own uploads (never fetch arbitrary hosts),
removal when the attachment or parent goes, and a "PDF" value on the `type` facet with a
pt_BR label.

**Done when:** a seeded PDF's inner sentence is found, and deleting the attachment
removes it.

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.
