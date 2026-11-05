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

### XLIX.156 The plugin read as a school would use it (D316)

**Analysis task: it delivers D lines, not code.** The WordPress plugin and its demo were
shaped by a store: Tarnfell sells outdoor gear, and `wordpress/demo/story.md` proves
search, a sentence read as filters, the assistant, compare, Build my kit, a product page
and checkout. A school client is the first real non-commerce adopter, and already
surfaced D303–D315.

**What to analyse:**
1. **Store-shaped names and defaults.** Find what stays commerce-only and what should be generic:
   - the copilot block is titled "Ask the catalog" (`blocks/copilot/block.json`);
   - the kit hard-codes `productTypes:['product']` and `guideTypes:['post']` (`public/copilot.js` 38-41);
   - `readPrice` and `readSize` (`public/search-block.js` 913, 998) have no date or place counterpart ("em novembro", "em São Paulo").

   Result cards are already neutral: price shows only with WooCommerce and a numeric price.
2. **A second demo for education and culture:** a fictional school with an agenda, openings, a publisher, a DSpace-like archive and a history tour, proving the non-commerce path the way Tarnfell proves the store.
3. **Source parity:** documents from the plugin and from D314/D315 share the same fields, so one SN site's facets, cards and AI features treat them alike.
4. **Operations for a small team:** what an editor sees when Turing is down, a reindex is slow, or the AI budget runs out.

**Delivers:** one D line per finding in Track S or U, each naming the line it serves,
and a design note for the second demo if it is worth building.

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
