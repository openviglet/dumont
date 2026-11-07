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

### XLIX.177 The WordPress pages point at Turing (D346)

The plugin never went through Dumont: its PHP client sends content straight to Turing.
Turing's T1251 to T1260 move the plugin, its docs, the Playground demo and the release
into the Turing repository (Block CT there), with a page of its own at
turing.viglet.org/wordpress, the address the video already ends on. Dumont keeps a
pointer, not the product:

- `dumont-site/src/pages/wordpress.tsx` gives way to a redirect to turing.viglet.org/wordpress/, the reverse of today's redirect, and `/playground/*` redirects to turing.viglet.org/playground/ for a transition, since shared Playground links may be in circulation.
- `wordpress-tile.tsx` stays on the landing and connectors pages, now with the YouTube video (a click-to-load youtube-nocookie facade) and a line saying the plugin is part of Viglet Turing, linking there.
- `site-links.ts` (`wordpressDocs`, `wordpressDemo`, `wordpressPlayground`), the nav and footer entries, `sitemap.xml`, `llms.txt` and `changelog.json` point at Turing, and `public/wordpress/chapter-*.webp` leaves with the page.
- `connectors/wordpress.md` and `wordpress-turing.md` become short stubs linking to the Turing docs page; the sidebar keeps one entry.
- The README, `intro.md`, `index.mdx`, `architecture.md`, `connectors/overview.md`, `catalog.md`, `developer-guide.md`, `core-concepts.md` and the two diagrams present WordPress as a Turing integration, not a Dumont connector.

Done when no Dumont page links a Dumont-hosted WordPress asset.

### XLIX.178 Remove the plugin from this repository (D347)

Once Turing's T1252 (the move, with history, through git filter-repo) and T1255 (the
ported workflow) have landed, this repository stops carrying a second copy that would
drift. `wordpress/` is removed whole, with `.github/workflows/wordpress-plugin.yml`, the
Playground step of `publish-github-pages.yml` (around lines 117-126) and any root script
or ignore entry that only served it. The OAI-PMH comments that mention sharing field
names with WordPress (`DumOaiPmhSource.java:115` and its test) stay, since they describe
a field contract, not the plugin. The four open Track S lines (D227, D282, D312, D313)
were retired as superseded by the Turing tasks, and Track S's history stays in this
CHANGELOG. Done when grep for `wordpress/` finds no build, workflow or script path.

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.

## LII Academic repositories as sources (DSpace, OAI-PMH)

### LII.12 A JSON API source that refills what the site lost (D348)

**Found by D341.** Before a full run, the structured plugin now forgets the ledger rows
of ids the index lost and calls `listEverything()` on the source, so the run sends them
again. The OAI-PMH source honours it by harvesting without its watermark. The JSON API
source does not: with an `incremental` block (expiry), a run reads what its call budget
allows and reports the rest through `unchangedIds()`, which only keeps those ids alive.
After the site is emptied, those ids are neither re-fetched nor sent, and they stay
missing until the source happens to read them again.

**What to build.** `DumJsonApiSource.listEverything()` stops reporting `unchangedIds()`
for this run, and walks its pages from the start, spending the budget as usual. Ids the
budget does not reach stay missing and are named in a run note ("N items the site lost
wait for a later run"), so the next runs keep going until the site holds every id. The
seen-state that drives `unchangedIds()` must not record an id as current while the site
lacks it.

**Done when:** a JSON API source with expiry, pointed at an emptied site, refills it
over one or more runs, and each run that stops short names how many lost items are still
waiting.

### LII.13 One name for an EDS source at the connector level (D349)

**Found by D345.** `DumEDSPlugin.ownsSource` and `indexAll` look the source up by its
**title** (`findByTitle`), so every connector-level call names an EDS source by title:
`/api/v2/connector/index/{title}/all`, `reindex/{title}/all` and the D345 `DELETE
ledger/{title}`. But `DumEDSPluginProcess.run` opens its session with `source.getId()`,
so the ledger, the run record and the deindex sweep all file EDS rows under the **id**.
The two never meet: Reindex all and Clear indexing state delete the rows of a source
named by title, which hold nothing, and leave the real rows in place. The crawl state is
unaffected, since `resetState` resolves the entity first.

**What to build.** One name for an EDS source at the connector level. The cheapest fix
that keeps existing ledgers is `ownsSource` and `indexAll` (and `resetState`) accepting
the id as well as the title, with the console calling the connector API by id, as the
Web Crawler does. Alternatively the session takes the title, which needs a migration of
the rows already filed under ids.

**Done when:** Reindex all and Clear indexing state on an EDS source empty the rows its
runs wrote, checked by a test that runs the source and then clears it.
