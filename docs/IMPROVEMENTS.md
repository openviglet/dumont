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

**Where it came from.** A client runs three WordPress sites and wants them in one shared
SN site (D148), searched from the institutional one. Every read path pins the own key,
and in PHP each caller builds `'site_key:' . site_key()` itself (`native-search.php`
326, 546, 687; trending, related); JS has one helper (`siteFilter`, `public/search.js`).
Template mode (`class-viglet-dumont-search.php` 53-66) applies **no** site key at all,
so on a shared SN site it already lists other sites' documents.

**What the plugin should do.**
- **One scope seam per language:** a PHP `Indexer::scope_filters()` and the JS `siteFilter`/`siteFilters`, returning the own key plus the configured siblings; every read path calls them, template mode included (which fixes its leak on its own).
- A setting "Also search these sites" (`search_site_keys`, key plus label, constant `VIGLET_DUMONT_SEARCH_SITE_KEYS` like D151); Related opt-in per block (`sites: own|all`).
- Assistant, copilot, kit and nl-filters send the set as `filters` (T1213 takes a map).
- `site_key` as a facet with the labels, and an origin badge on cards from another site.
- `navigate` compares against the own and sibling origins (today same-origin only, `assistant.js` 43-51), never any other host.

Writes stay on the own key: index, sweep, delete-all and Tuning never touch a sibling.

**Tests:** Turing's semantics for several values of one field in `fq` (OR, not AND); an
unknown sibling key; template mode scoped.

**Done when:** the `network` spec has site A listing B and finding B's documents, and B
(not listing A) finding only its own, in every search mode.

### XLIX.152 Grounded people and themes as facets (D310)

**Where it came from.** A client wants visitors to explore it by **person** and
**theme** across its WordPress sites and DSpace repository (one SN site, D303). People
and themes sit in text, not fields, and the repository's author facet mixes "Surname,
Name" with "Name Surname".

**Where the seam is.** Not Dumont's enrichment: the plugin imports straight to Turing,
and Track F's D18 resolves duplicate *records* by canonical key, not name variants.
Turing already declares `TurSNFieldType.NER` and facets `turing_entity_*` at query time
(`TurSolrQueryBuilder.java` 1065), but nothing populates them. Extraction at Turing's
import serves every source alike (this plugin, D314, D315, every connector), so the core
belongs there: a Turing line, filed before this one is built and named as its dep.

**What the Turing half must do** (stated for that line): extract people, themes,
institutions and places from title and text with the site's LLM, each value a span the
source holds (the non-goal "never fabricate"); resolve variants against a per-SN-site
list of canonical names and aliases; cache by content hash; one extraction per changed
document.

**What the plugin does here:**
- Fields maps the `turing_entity_*` facets to labels (`pessoas`, `temas`…) and offers them on the search block;
- Settings edits the alias list and sends it to the SN site;
- the editor panel shows the values extracted for the post, with remove (never add-by-guess).

**Done when:** a seeded post naming a person and a theme is found under both facets, and
an alias resolves to the canonical name.

### XLIX.153 An immersive layout for the instant overlay (D311)

**Where it came from.** A client wants its search "complete and immersive", one
keystroke from anywhere. The instant overlay (D167, `public/overlay.js`) is a flat list
of six results with no type, date, image, answer or zero state; everything richer lives
only on the results page.

**What the plugin should do.** An "immersive" layout for the overlay (setting `overlay_layout: list|immersive`, list stays the default):
- **full screen** on phones, a large panel on desktop, with focus trapped and the background inert;
- **zero state:** recent searches (already in `localStorage`), popular chips (D202) and an editable "Explore" row of links;
- **results grouped by type** in columns or sections ("Events", "Pages", "Books"), each with the mapped card fields (D305) and its own "see all" into the results page filtered to that type;
- **an answer strip** on top for question-like queries (D166), with "Continue in the assistant" (the next line);
- **a preview pane** on desktop for the highlighted result: image, abstract and card fields, so arrows browse without leaving;
- **keyboard first:** arrows across groups, Tab into the preview, Enter opens, Ctrl+Enter opens the results page; announced with `aria-live`;
- the federated set (D303) and fixed filters (D306) honoured.

Same Interactivity store as the search block, so a theme can style it with the same BEM
classes.

**Done when:** axe passes on the open overlay at 390 and 1280px, and a type group's "see
all" lands on the results page with that filter.

### XLIX.154 From the search answer into the assistant and back (D312)

**Where it came from.** On a client's site the search answer (D166) and the Site
assistant (D175) are separate: a visitor who reads the answer to "como funciona a
biblioteca" and wants "and on Saturdays?" must start over. The answer runs its own
`createAnswerController` with its own conversation (`search-block.js` 528); the
assistant runs `createChatController` and cannot be opened with turns already in it. Nor
does an assistant answer citing many documents link to the results page.

**Nothing is missing in Turing.** `ConversationRequest.messages` already becomes the
chat's `history`, and the SDK's chat controller takes `initialMessages`
(`controllers/chat.ts` 257). The gap is the plugin's alone.

**What the plugin should do.**
- **Search → assistant:** under the answer, "Continue in the assistant" opens the assistant (floating or the page's block) seeded through `initialMessages` with the query and the answer as the first exchange. The assistant gains a small open-with-turns API the answer calls.
- **Assistant → search:** when an answer cites more than three documents, or the visitor asks to "see everything", a link opens the results page with the query and, where the citations share a type, that filter.
- **One session** (`TUR_SESSION`) across both, so the answer's helpful signal and the chat's are one trail; prove the cookie is shared rather than assume it.

Same in the overlay and the results page.

**Done when:** a follow-up typed after "Continue in the assistant" is answered with the
first question's context, and an assistant answer with many sources links to the
matching results page.

### XLIX.155 Turing chat flows in the Site assistant (D313)

**Where it came from.** A client wants its site assistant immersive, with guided
conversations built as Turing chat flows: a tour through the school's near-century of
history with choices, and an orientation flow ("I want to study / research / work here")
routing to the right pages. Turing runs flows (`genai/flow`: forms with `select` fields,
slots, flow-select; endpoints `/form-submit`, `/flow-select`, `/slots` in
`TurSNSiteGenAiAPI`), and `@viglet/turing-sdk` exports `postSiteFlowSelect`,
`postSiteFormSubmit`, `fetchSiteChatSlots` and the `TurChat…` response types, with
`submitForm` and `resetFlow` on the chat controller. The plugin's assistant
(`public/assistant.js`) renders only streamed text, citations and the `navigate` tool.

**What the plugin should do.** The assistant renders what a flow sends:
- **choices** as buttons, typed answers still accepted;
- **forms** as native labelled fields with validation, sent through `submitForm` only when the visitor submits;
- **slot prompts** with label and allowed values;
- **cards** for cited documents with the card fields D305 adds, so a history tour shows a photo and its source;
- **a flow menu**: block attribute `flows` offered as starting chips beside the suggested questions;
- **handoff** shown as "a person will answer" when the flow sends one, hidden otherwise.

Each turn is announced, focus moves to the first control, and keyboard-only use is
proven by the a11y specs; pt_BR ships with it.

**Done when:** a seeded two-step flow with a choice and a form runs end to end in the
block, at 390 and 1280px.

### XLIX.157 Untranslated strings fail a gate (D317)

**Where it came from.** D305 regenerated the `.pot` and the pt_BR `.po` (README,
"Translations") and found eight strings shipped by earlier tasks with no `msgstr`: the
Health screen's AI agent reasons ("The SN site has no AI agent in Turing.", ...) and
"Turing refused this site's API key here: %s". A pt_BR site showed them in English. D304
shipped "Results page on another site" the same way, and only the pt-br spec caught it,
because the untranslated label happened to contain an older translated phrase ("Results
page").

**Why nothing caught it.** `tests/e2e/i18n/pt-br.spec.mjs` reports a leak only when a
string the `.po` already translates shows up in English. A string missing from the
`.po`, or present with an empty `msgstr`, is invisible to it, and nothing regenerates
the `.pot` in CI.

**What should happen.** A gate that fails when the `.pot` the source would produce today
differs from the committed one, or when the pt_BR `.po` holds an entry with an empty
`msgstr`: `wp i18n make-pot` in the `wordpress-plugin.yml` job (the README's Docker
line), then a check that every msgid has a translation. Run locally from an npm script
so a task sees it before committing.

**Done when:** adding `__( 'Something new', 'viglet-turing-search' )` without
translating it fails the gate, naming the string.

### XLIX.158 Suggestions inside the block's scope (D318)

**Where it came from.** D306 gave the search block fixed filters ("Always filter by")
and kept every search, the first paint and nl-filters inside them. The box's suggestions
are the one read left out: `createSiteAutoComplete()` (`public/search.js`) asks Turing's
autocomplete with the query alone, so a box scoped to `type:event` suggests terms only
posts and pages hold, and picking one finds nothing in the scope.

**What to build.** Turing's autocomplete endpoint reads `fq[]` like search does
(`TurSNSiteAutoCompleteAPI` takes the filter operators). Check whether the SDK's
`createAutoComplete` passes filters; if it does, give `createSiteAutoComplete()` a
filters argument and have the block pass `blockScope()` (search-block.js), so the site
key (D303) and the block's fixed filters both scope the suggestions. If the SDK drops
them, file the gap in Turing's roadmap first and wait on it.

**Done when:** a block set to `type:page` suggests no term that appears only in posts.

### XLIX.159 PDFs linked from a post, and gone with it (D319)

**Where it came from.** D309 made PDFs searchable two ways: a PDF attached to a post in
search is a document of its own (`Viglet_Dumont_Pdf`, synced on `add_attachment`,
`edit_attachment`, `delete_attachment` and Index All), and a mapping source `file:<meta
key>` reads a meta-named PDF into the post's own text. Two cases it left:

- **Linked, not attached.** A block in a page links a media-library PDF uploaded elsewhere
  (`post_parent` 0, or another post). The academic office's forms are this shape. It is
  never sent, since `indexable()` requires the parent to be in search.
- **The parent leaves search.** A post unpublished, excluded or past its "Leaves search on"
  date (D308) is removed at once, but its attached PDFs stay in search until the next
  `wp dumont sweep` (orphans() reads them as no longer indexable).

**What to build.**
1. When a post is indexed, find the links in its rendered content to this site's uploads
   (`attachment_url_to_postid`) that are PDFs, and treat each as in search while any post in
   search links it; record the linking posts in a meta on the attachment so removal knows
   when the last one goes.
2. When a post is removed from search (the queue's `delete` job, and `expire()`), remove the
   PDFs it attaches or links that no other post in search holds.

**Done when:** a page linking a PDF it did not upload makes the PDF findable, and
unpublishing the page removes the PDF without a sweep.

### XLIX.161 Plugin deletes scoped to its own documents (D322)

**Found by D316.** A site that sets its WordPress site key on a Dumont source too (the
DSpace profile sets `site_key` from its own setting) loses those documents on the
plugin's next full reindex: `orphans()` marks every held id under the own key that is
not `<key>:post:<n>` as an orphan (`class-viglet-dumont-operations.php` 150-175),
`reindex()` ends with that sweep, and delete-all filters on `site_key` alone
(`class-viglet-dumont-indexer.php` 348). The obvious way to make an archive searchable
from WordPress therefore deletes the archive.

**What to build.** Scope the plugin's deletes to its own documents, not to the key: the
sweep and delete-all also require the plugin's `source_apps` (or the `<key>:post:` id
prefix), and the Health screen warns when foreign documents share the own key.

**Done when:** a document with the site key but another source survives Index All and
Delete All.

### XLIX.162 A copilot that is not a shop's (D323)

**Found by D316.** The copilot block reads as a shop on any site: its title "Ask the
catalog", description and `shop` keyword (`blocks/copilot/block.json` 5-9), its defaults
("Looking through the catalog…", "Nothing in the catalog matches that.", `render.php`
18, 47-70), the editor copy (`copilot-block-editor.js` 36-76) and the AI status labels
(`viglet-dumont.php` 101, 108). Its kit is on by default (`render.php` 19, 92-95) and
hard-codes `productTypes:['product']`, `guideTypes:['post']` and `stock_status`
(`public/copilot.js` 36-41), so on a site with no WooCommerce it shows "Plan a kit" and
can never stage anything.

**What to build.** Neutral default strings ("Ask this site") with the shop wording only
while WooCommerce is active; the kit on by default only with WooCommerce; the staged
types, guide types and availability field as block attributes, so a school stages
courses or events and cites pages.

**Done when:** on a site without WooCommerce the block names no catalog, shows no kit,
and a kit set to stage `event` stages events.

### XLIX.163 Periods read from a sentence (D324)

**Found by D316.** The search block reads a price and a size in code (`readPrice`,
`readSize`, `public/search-block.js` 776-796, 948, 1033) but nothing reads a period:
`dateWords` (665-667) holds only backward words (today, yesterday, last, passado,
último…), with no next, próximo, tomorrow, amanhã, fim de semana or month names, so "em
novembro" is not even treated as a sentence and nl-filters is never asked.

**What to build.** Future and month words in `dateWords` (en, pt_BR), and a code-side
`readPeriod` that maps "next week", "this month", "em novembro" onto the mapped date
field's period facet (D307's agenda set), the agenda counterpart of `readPrice`, applied
or suggested like the others.

**Done when:** "debates em novembro" on an agenda filters the event dates to November
with no language model.

### XLIX.164 A second demo for education and culture (D325)

**Found by D316.** Every shipped non-commerce feature (period sets, leaving search,
PDFs, card date and image, fixed filters) is proven only by isolated fixtures, never on
a coherent site, a recorded story or the docs. The demo tooling runs two stores but each
step assumes a store: `setup_demo` hard-codes the Tarnfell title, theme, WooCommerce and
catalog seed (`bin/run-docker.sh` 188-222, `run-docker.cmd`), `demo/setup.php` the shop
pages, and every service mounts only the Tarnfell theme.

**What to build.** First a store descriptor (theme, seed, title, WooCommerce yes or no,
port, SN site) the scripts and Playwright read; then a fictional school beside Tarnfell:
a block theme with front page, agenda, openings, books and a tour, a bilingual seed with
events, openings closing on a date, books with PDFs, a `story.md` and five to eight
specs. Leave the archive half (a Dumont source and a mock OAI-PMH) for after D322's
seam.

**Done when:** `run-docker.cmd demo edu` serves the school, and its specs walk the
agenda, an opening leaving search and a book's PDF.

### XLIX.165 One field contract for posts and archive records (D326)

**Found by D316.** One SN site holding WordPress posts and Dumont's OAI-PMH or DSpace
records gets parallel fields for the same concept: `author` (one string) beside
`authors` and `advisors`; `publication_date` beside `date`/`dateText`, so cards, the
Newest sort, period facets and the nl "Published" chip miss archive items; `abstract`
only on posts, so archive cards have no snippet; `categories`/`section`/`tags` beside
`subjects`/`community`/`collection` (plugin `class-viglet-dumont-indexer.php` 396-409;
source `DumOaiPmhSource.java` 113-151, `DumDSpaceProfile.java` 89-131).

**What to build.** One canonical contract both sides honour for the shared concepts
(authors multi, a single publication date field, abstract, subjects), stated in the
docs: either the source maps onto the plugin's names, or the plugin offers a mapping
preset that sends `authors`, `subjects` and `date`. Decide which side moves by what
Turing's default fields and the SN site's facets expect.

**Done when:** a post and an archive record by the same author share one author facet
value and both show a date and a snippet on their cards.

### XLIX.166 Types from other sources (D327)

**Found by D316.** A document from another source carries a free-text, possibly
multi-valued `type` (`dc:type`), and the search block assumes a WordPress post type: the
server reads `(string) $fields['type']` (`class-viglet-dumont-native-search.php` 580),
likely "Array" with a notice on a list, and the browser joins the values with commas
(`public/search.js` 115). Type labels, card date and image come from WordPress post
types only (`blocks/search/render.php` 30-36; `native-search.php` 360-371), so such a
document gets a raw badge and cannot be configured.

**What to build.** Read the first value of `type` on both sides, and let Fields list the
type values the SN site holds beyond WordPress's, with a label and the card date and
image like a post type's.

**Done when:** a record with `type: ["Article","Thesis"]` shows the label set for
Article, and its card date follows the field chosen for it.

### XLIX.167 Fast failure while Turing is down (D328)

**Found by D316.** When Turing hangs, every page that reads it waits up to the client's
30 s timeout (`class-viglet-dumont-api-client.php` 548): the search block's first paint
(`class-viglet-dumont-native-search.php` 548-566), the theme search (905-908, which
falls back to WordPress search only after the wait), and Related, which tries VECTOR
then MLT and caches no failure, so it pays twice on every view
(`class-viglet-dumont-related.php` 201-231).

**What to build.** A short "Turing is down" mark (a transient of about a minute, set by
any failed read) that every render path checks before calling, and a render timeout of a
few seconds, apart from the admin and indexing calls that may wait longer.

**Done when:** with Turing stopped, a page with the search block and Related renders in
under three seconds, and the theme search falls back at once.

### XLIX.168 Stopped jobs said and replayed (D329)

**Found by D316.** After about three hours of a Turing outage, edits stop reaching
search for good: a job gives up after `MAX_ATTEMPTS = 8` with backoff from 60 s to 3600
s (`class-viglet-dumont-queue.php` 41-47), and the only place it shows is the Health
page's "stopped retrying" count (`assets/admin/health.js` 292-330). No admin notice
warns (`class-viglet-dumont-admin.php` hooks only the demo and missing-build notices),
and nothing replays the jobs when Turing comes back.

**What to build.** An admin notice and a dashboard line while failed jobs exist, linking
to Health; and once a ping succeeds again, the stopped jobs re-queued automatically.

**Done when:** an outage longer than the retries ends with every edit in search after
Turing returns, with no click.

### XLIX.169 The panel shows why a reindex failed (D330)

**Found by D316.** The editor panel's "Reindex now" shows "Failed" with no reason: on
failure the server answers 502 with `describe()` (state, label, detail) and no `message`
(`class-viglet-dumont-post-state.php` 474-497), while the panel prints `error.message`,
which is empty (`src/admin/js/editor-panel.js` 267-268, 283-284).

**What to build.** The panel renders the error's own `label` and `detail` (Turing's
reason), and the server adds a `message` to the 502 so any client gets one.

**Done when:** a reindex refused by Turing shows Turing's reason in the panel.

### XLIX.170 Reindex in the background, resumable (D331)

**Found by D316.** Index All from Settings runs its batches in the browser tab
(`src/admin/js/admin.js` 105-128): closing the tab or one failed batch stops it, the
offset is lost, nothing warns before leaving, and a missing `post_type` defaults to
`post` (`viglet-dumont.php` 754-756). A slow reindex on a shared host is a session the
editor must babysit.

**What to build.** Full reindexes run through the queue or WP-Cron with their progress
stored, resumed after a failure, shown on Health with a cancel; the Settings button only
starts it. A `beforeunload` warning while the old in-tab path remains.

**Done when:** closing the tab mid-reindex, the reindex finishes, and Health shows its
progress meanwhile.

### XLIX.171 A spent AI budget said, with its reset (D332)

**Found by D316.** When the site's AI budget runs out, Turing answers 429
`quota-exhausted` with `resetsAt`, and the plugin reads none of it: the answer panel
just hides (`public/search-block.js` 233-240), nl-filters swallows the error (742-744),
the copilot says "The catalog could not answer right now." (`blocks/copilot/render.php`
48), and Health has no AI card; the AI status reports on/off from capabilities, never
usage (`viglet-dumont.php` 94-124).

**What to build.** AI failures recorded as events by kind (quota, model error, off), and
"AI budget exhausted until <date>" on Health and the AI screen; visitor surfaces say
answers are paused rather than failing silently.

**Done when:** with the quota spent, Health names it and its reset date, and the copilot
says answers are paused.

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.

## LII Academic repositories as sources (DSpace, OAI-PMH)

### LII.1 A DSpace source (D314)

**Where it came from.** A client wants its institutional repository in the unified
search of its WordPress sites (one SN site, D303). Measured 2026-11-04: the repository
runs **DSpace 9.2**, with REST and OAI-PMH under a non-default server path (public, no
key), about two hundred items in six communities, and `deletedRecord=transient`. DSpace
is the default repository of Brazilian universities, and crawling its HTML loses
authors, dates and collections.

**A profile on the OAI-PMH source, not a second harvester.** Every DSpace speaks OAI,
and its `dim` format carries all metadata, deletions and incremental `from` (D315).
D92's JSON machinery is no shortcut here: its incremental mode expires ids by age and
never filters by `lastModified`. This line adds, from REST, what OAI lacks.

**What Dumont should do.** A `dspace` source over D315:
- **metadata** from OAI `dim`: title, `dc.contributor.author` and `.advisor` (multi), `dc.date.issued` (DATE), `dc.type`, `dc.subject`, abstract, language; the handle URL as the document URL;
- **community and collection** as facets by name, resolved once per run through REST;
- **full text** from the TEXT bundle (DSpace's own extraction), else the ORIGINAL PDF through the shared capped extraction; THUMBNAIL as image; the REST base configured;
- **withdrawn, private or embargoed** items deleted, never indexed;
- a site key per source.

Name variants of one author ("Surname, Name" beside "Name Surname") are D310's to
resolve, not this line's.

**Done when:** an item's author, year, community and a sentence of its full text are
searchable, and withdrawing it removes it on the next run.

### LII.3 Public URLs and landing-page PDFs for OAI-PMH records (D321)

**Where it came from.** D315's live check harvested two pages from a real OJS journal
(revistas.usp.br/rce, 200 records) and from demo.dspace.org (199 records, one deleted).
Both harvested, but two things came out wrong:

- **A private address as the record's URL.** On the DSpace demo the `url` rule
  (`DumOaiPmhSource`: a handle or DOI link first, else the first http(s) identifier) picked
  `http://10.17.50.146:4000/handle/123456789/4970`, an internal host the repository leaked
  into its Dublin Core. A visitor can't open it.
- **No PDF text from OJS.** With `oaiPmhFullText` on, nothing was extracted from either
  site: OJS links the article page (`article/view/{id}`), whose PDF sits at
  `article/download/{id}/{galley}`, and DSpace's `/handle/` pages answered 403.

**What to build.**
1. In the URL rule, skip addresses whose host is a private, loopback or link-local IP or a
   bare hostname without a dot, and prefer one on the base URL's own host; fall back to the
   next candidate.
2. For full text, learn the PDF from the landing page instead of guessing: fetch the
   same-host `article/view` (OJS) or `/items/{uuid}` (DSpace 7) page, read its
   `citation_pdf_url` meta tag (both systems emit it), and fetch that when it is on the same
   host. The size caps and pacing stay as they are.

**Done when:** the DSpace demo's records carry no private URL, and an OJS article's PDF
words find it, both checked live as in D315.
