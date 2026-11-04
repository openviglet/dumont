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

### XLIX.110 Photos for the demo store (D265)

**What happens.** The demo store's pictures are flat drawings the seed makes with GD,
which read as a mock-up on video. `npm run demo:photos` buys photorealistic ones from
Ideogram 4.0 through polyweave (clothing as a flat lay, gear on the hill, guides as
outdoor scenes), keeps an 800 px WebP of each in `demo/photos/`, and the seed imports a
photo wherever one exists.

**What is left.** On 2026-10-31 Ideogram answered 402 (credit run out) and then 401 after 14 of
the 68 photos. Still to buy, about USD 3.20 at 0.06 each:
- products: blencathra-mountain-tent, borrowdale-gaiters, borrowdale-travel-pack, bowfell-winter-down-bag, catbells-20-litre-kids-pack, catbells-merino-beanie, coniston-bivvy-bag, ennerdale-65-litre-trekking-pack, ennerdale-insulated-bottle, ennerdale-trail-shoe, fairfield-camp-stove, fairfield-synthetic-summer-bag, glaramara-two-person-tent, grisedale-sleeping-mat, grisedale-wool-jumper, haystacks-dry-bag-set, haystacks-one-person-tent, helvellyn-28-litre-daypack, helvellyn-waterproof-gloves, kentmere-map-case, kirkstone-silk-liner, kirkstone-winter-boot, langdale-cook-set, langdale-family-tent, langdale-heavyweight-fleece, mardale-approach-shoe, pillar-lightweight-hiking-boot, scafell-45-litre-pack, scafell-trekking-poles, skiddaw-three-season-down-bag, wansfell-running-top, wasdale-12-litre-running-vest, wasdale-head-torch
- guides: choosing-a-backpacking-tent, choosing-hiking-boots, down-or-synthetic-insulation, family-walking-kit, first-wild-camp-kit, how-to-care-for-leather-boots, how-to-dry-wet-boots, how-to-fit-a-backpack, how-to-pitch-a-tent-in-rain, how-to-read-a-hydrostatic-head, how-to-reproof-a-waterproof-jacket, how-to-stay-warm-at-night-camping, how-to-store-a-sleeping-bag, how-to-use-trekking-poles, how-to-wash-a-down-jacket, layering-for-the-hills, sleeping-bag-temperature-ratings, trail-running-kit-list, travel-light-kit, what-size-backpack, winter-hillwalking-kit

**Done when.** Every product and guide has a photo, `npm run demo:photos` reports every
photo current, and the seed draws none.

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

### XLIX.128 Generative calls scoped to the site's key (D287)

**What happens.** D148 lets several WordPress sites share one SN site: every document
carries `site_key`, and the search block, Related, trending, tuning and the native
search all filter on `fq[]=site_key:<key>`. The calls that go to Turing's generative
endpoints do not. `copilot.js` posts Build my kit to `/api/sn/{site}/copilot/kit` and
the product question to `/copilot` with no filter, and Turing's `KitRequest` and
`CopilotRequest` (`TurSNCatalogCopilotAPI`) take none. On 2026-11-03 the local
`tarnfell` SN site held the demo store (`site_key=localhost`) and a Playground run's
copy of the same catalog (`site_key=tarnfell`): the kit staged every product twice, each
with a "Site: localhost" or "Site: tarnfell" reason, and a shopper on one site could add
the other site's products.

**What it should do.** The kit, the product question, the Site assistant and the compare
summary read only this site's documents. Turing's answer is a key bound to the site's
partition (T1133): its searches are scoped already, while chat, nl-filters and copilot
refuse such a key until the deferred half of T1133 puts the partition in the vector
store. D190 binds each network site's key; once T1133 lands, a single site sharing an SN
site gets the same bound key, and the plugin sends nothing extra.

**Done when.** Two sites sharing an SN site each get a kit, an answer and a comparison
built from their own products only, covered by a browser spec on the multisite network.

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.
