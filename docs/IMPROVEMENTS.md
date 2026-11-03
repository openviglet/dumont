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

### XLIX.106 A second narration line at each chapter's highlight (D261)

**What happens.** After the story learned to hold on its results (each chapter now 10–27
s), each chapter's narration is one line of 2–8 s read over its card, then the chapter
plays on in silence. The longest, the kit (27 s), says nothing while the stages, prices
and cart total are on screen.

**What it should do.** `demo/narration/script.json` takes an optional second line per
chapter read at its highlight (chapters.json has the time), saying what the screen shows
("Two tents under four hundred dollars, and an answer that cites them");
`story-video.mjs` places it there, and `story:narration` buys it like the others.

**Done when.** No chapter has more than about 6 s without a voice, and the takes still
fit before the next card.

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

### XLIX.119 The English video, the primary one (D276)

**What happens.** Only the pt-BR video exists (Recanto da Serra, 7m28s). D261 shipped
with its criterion still open: the English takes are not bought and no `--language en`
video has been assembled. Every place the video will be embedded is in English (this
site's WordPress page, the docs, the plugin README, the wordpress.org listing, the
Turing site), and the Playground the closing card points at is the English store, so the
pt-BR video alone has nowhere to go.

**Why two videos and not one with two audio tracks.** The footage differs by language:
the pt-BR store is Recanto da Serra in reais on its own SN site, the English one is
Tarnfell in dollars. English audio over the Portuguese store would not match the screen,
so each language is its own recording and its own video, with its own title and
chapters.

**What it should do.**
- The demo stack runs a Turing image that carries T1190 and T1191, so the kit's reasons no
  longer show "Site: localhost".
- `npm run story:narration -- --language en` buys the English takes from the current
  script; `DEMO_LOCALE` unset, `npm run e2e:story:record` records Tarnfell;
  `node bin/story-video.mjs --language en` assembles it with the cards of D275.
- English is the primary video: the embeds and the chapter cuts start from it.

**Done when.** `tests/e2e/artifacts/video/story.en.mp4` (or the language-suffixed name
the script settles on) plays the Tarnfell story narrated in English, no frame shows
"localhost", and the D261 criterion is met.

### XLIX.122 One clip per chapter (D279)

**What happens.** The video is one 7.5-minute piece plus a 30 s teaser. Someone
searching for one thing the plugin does (natural-language search with a price filter, an
assistant that cites the site's own pages, a kit built from a trip description) only
finds it inside the long video, if at all: one video is one entry point.

**What it should do.**
- `story-video.mjs --chapter <n>` builds a standalone clip of one chapter, 40 s to 1 min:
  a short title card, the chapter's action and result with Ana's lines and captions, and
  a closing card pointing at the full video and the Playground.
- The first three cuts are the strongest chapters: 2 (a sentence read as a price filter),
  3 (the Site assistant answering from the guides, with sources) and 5 (the kit).
- English first. pt-BR cuts only if the pt-BR long video shows interest in its numbers,
  which the visibility review reads.
- Each cut is built from the same recording as the long video, so a new recording rebuilds
  them all.

**Done when.** `story-video.mjs --language en --chapter 2`, `3` and `5` each produce a
clip under a minute that opens on its card, carries its captions and closes on the
full-video and Playground card.

### XLIX.123 A phone-viewport recording for vertical cuts (D280)

**What happens.** The story is recorded at a 1920 x 1080 desktop viewport. Vertical
formats (YouTube Shorts and the like) need 9:16, and cropping the desktop recording cuts
the search results, the answer and the filter line in half.

**What it should do.**
- A second story run at a phone viewport (for example 1080 x 1920 at device scale), using
  the same spec and the same takes: the search block, the assistant and the compare table
  are responsive, so the story plays there as it does on the desktop.
- `story-video.mjs --vertical` assembles the chapter cuts of D279 from that recording,
  with the cards laid out for 9:16 and the captions placed above the bottom UI band
  vertical players draw.
- Anything the story cannot show at a phone width is a finding for the plugin's
  responsive layout, filed as its own line, not worked around in the video.

**Done when.** The vertical cuts of chapters 2, 3 and 5 play under a minute, every frame
shows the whole result without cropping, and the captions do not sit under the player's
own controls.

### XLIX.124 A short technical video for developers (D281)

**What happens.** The video speaks to a shop owner. A developer or an agency deciding
whether to put the plugin on a client's site wants something else: how it is installed,
what runs where, and what the owner sees behind the scenes. Chapter 8 ("Behind the
scenes: native search and its searches in the insights") is the only part with that
angle, and it sits at the end of a story told to another audience.

**What it should do.**
- A short technical piece, 2 to 3 minutes, recorded by script like the story: installing
  the release zip in wp-admin (and the Composer route in a line), the settings page
  connecting to a Turing instance, the first index of the catalogue, then chapter 8's
  native search and insights.
- Narrated plainly, not by Ana, with the same captions and cards pipeline; the closing
  card points at the repository, the docs and the Playground.
- If Dumont is named anywhere in the video, it is here, as where the plugin's code lives,
  and only where the words are true of what the viewer sees.

**Done when.** The technical piece is assembled in English from its own spec, every step
it shows works on a clean demo stack, and its captions and chapters are written like the
story's.

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

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.
