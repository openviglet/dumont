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

### LII.5 Catalog API under the Key, as the other sources (D339)

**Found by D314.** Creating an OAI-PMH source from a script
(`catalog/dspace-dev/e2e.py`) with the connector's `Key` header was refused until the
script fetched `/api/csrf`, kept its cookie and sent the token, which changes after each
write. `DumWebConfiguration` exempts the `db`, `wc` and `assets` source APIs from CSRF
for Key-authenticated calls, but not `/api/v2/catalog/**`.

**What to build.** The same CSRF exemption for the catalog source API (and any other
plugin API that a Key-holding client writes), checked by a test that walks the
registered `/api/v2/**` controllers so the next plugin cannot be missed again.

**Done when:** `POST /api/v2/catalog/source` with only the `Key` header creates a
source, and a test fails when a plugin API is left out of the exemption.

### LII.6 A structured source's manifest that converges a new or older site (D340)

**Found by D314.** Pointing the catalog connector's DSpace profile at a fresh SN site,
the manifest it posts was refused with 400 "seInstanceId is required": it names no
search engine instance, so Turing cannot create the site. On an existing site whose
`author` and `type` were single-valued with no facet, the same manifest (which declares
them multi-valued facets) was refused with 409, a breaking change with no migration
declared, and the site stayed as it was. `catalog/dspace-dev/e2e.py` works around both
by creating the site first and posting a second manifest carrying `RECREATE` migrations.

**What to build.** The structured-source provisioning (the SPI's manifest builder) names
the SE instance, as the WordPress plugin does when Turing asks for one (the first
enabled instance), and declares a `RECREATE` migration for each field whose cardinality
or type its schema needs (as the plugin does for `author`, D326), then reports Turing's
refusal on the source's run record rather than only in the log.

**Done when:** a DSpace source pointed at a site that does not exist, and at one holding
a single-valued `author`, indexes its items on the first run with no manual step.

### LII.7 Index All that trusts Turing, not the ledger, and says when the queue blocks (D341)

**Found by D314.** Two things met while running the catalog connector against the local
DSpace:

- `indexAll` skipped every item its ledger had already marked as sent, though the SN site had been recreated in between and held none of them; only the synchronous `reindex/{name}/all` sent them again.
- With the disk above 90%, the embedded Artemis broker blocks producers: the run stopped after "Draining N jobs from buffer" with no error, holding the HTTP thread, until the connector was moved to another disk.

**What to build.** Index All checks what the site holds for the source (a count, or the
ids the sweep already lists) and treats a ledger entry Turing does not have as unsent;
and a producer blocked by the broker's disk or memory limit fails the run with that
reason on its run record and the console, instead of waiting forever, with the threshold
named in the docs.

**Done when:** Index All on an emptied site sends every item again, and a run against a
full disk ends failed with "the queue's disk is over its limit".

### LII.8 Connector feedback when Turing cannot take the run (Turing #65) (D342)

**Where it came from.** GitHub issue openviglet/turing#65 (2024, from when the
connectors lived in Turing): a connector that cannot index into a Turing SN site gives
no feedback; only Turing's log says why.

**What happens today.** The structured-source connectors (AEM, DB, Assets, Catalog) send
a manifest before the first document (`DumStructuredSourcePlugin` →
`DumTuringIndexingPlugin.provision()`), so a missing site is created and a refused
manifest lands on the run record as a warning. Two gaps remain:

- The Web Crawler (`DumWCPlugin`) and EDS (`DumEDSPlugin`) implement `DumConnectorPlugin` directly and never check the site or the locale; a run against a site or locale Turing lacks sends everything and fails batch by batch.
- `DumConnectorTuringSitesApi.listSites()` turns any connection error into an empty list, so the console cannot tell "Turing unreachable" from "no sites".

**What to build.** Before the first batch, the Web Crawler and EDS runs check (or
provision, as the structured sources do) the target site and locale and stop with that
reason on the run record when they are missing. `listSites` answers an error with the
cause (unreachable, 401, 404) instead of `[]`, and the source form shows it.

**Done when:** a Web Crawler run against a site that does not exist ends with "SN site X
not found" on its run record before any batch is sent, and the source form shows "Turing
unreachable" when Turing is down.

### LII.9 A run stops when Turing is unreachable (Turing #80) (D343)

**Where it came from.** GitHub issue openviglet/turing#80 (2024): the AEM connector must
stop indexing when Turing is unavailable.

**What happens today.** Nothing is lost: `DumTuringIndexingPlugin.index()` throws
`DumIndexingDeliveryException` on a failed delivery, `DumConnectorProcessQueue` records
it (`recordDeliveryFailure` clears those documents' checksums so the next run resends
them) and marks the run partial. But the crawl is never told. Delivery is asynchronous
through the JMS queue, with no failure counter, circuit breaker or health check, so the
AEM traversal keeps reading every page and enqueuing batches that all fail; a failed
`provision()` also logs and goes on.

**What to build.** A run-level breaker shared by every connector: after N consecutive
failed deliveries (or a failed provision), the run checks Turing's health and, while it
stays unreachable, stops reading the source and ends `failed` with "Turing unreachable
at <url>" on its run record and the console. The documents not sent keep their cleared
checksums, so the next run resends them. N and the wait are configurable, with defaults
named in the docs.

**Done when:** an AEM run started with Turing down stops reading the source after the
first failed batches, ends failed with that reason, and a run after Turing returns sends
every document the failed run did not.

### LII.10 AEM once pattern that works, and counts (Turing #93) (D344)

**Where it came from.** GitHub issue openviglet/turing#93 (2024): improve the AEM "once"
regex pattern so matching content is reindexed N times rather than exactly once.

**What happens today.** The once rule is a yes/no gate:
`AemNodeNavigator.shouldProcessNode` skips a path only when `isOnce(config)` is true and
the path matches `oncePattern` (`DumAemCommonsUtils.isNotOnceConfig`). `isOnce` reads a
`DumAemPluginSystem` row keyed `<id>/once` (`DumAemSourceService`), and no code ever
writes that row; the only writer of `DumAemPluginSystem` is the journal cursor. So
`oncePattern` never takes effect unless someone inserts the row by hand. There is no
count of how often a path was indexed.

**What to build.** First make "once" work: mark the source after a full run, so the next
runs skip paths matching `oncePattern`. Then generalise it to a count: `onceTimes`
(default 1) and a per-source count of completed full runs, so matching paths are indexed
in the first N runs and skipped after; resetting the source (or Reindex all) starts the
count again. Show the setting and the current count on the AEM source form.

**Done when:** with `oncePattern` set and `onceTimes` 2, matching pages are sent in the
first two runs and skipped in the third, and an IT covers it.

### LII.11 Clear a source's indexing state (Turing #117) (D345)

**Where it came from.** GitHub issue openviglet/turing#117 (2025): a button in the
connector UI to clean the indexing-state table (the old `turindexing`, now the ledger).

**What happens today.** The ledger can only be wiped as part of a reindex: "Reindex all"
(`GET /api/v2/connector/reindex/{name}/all` → `DumConnectorRunService.start`) calls
`indexingService.deleteByProviderAndSource(...)` and starts a full run, and per-id
reindex deletes those rows. The button exists for AEM and DB only; EDS has its own
clear-and-reindex; the Web Crawler and Assets have neither. There is no way to clear the
state without starting a run, and `DumConnectorApi` exposes no delete on the ledger.

**What to build.** `DELETE /api/v2/connector/{name}/source/{source}/ledger` that removes
the source's ledger rows (refused while a run is in progress), and a "Clear indexing
state" action next to "Reindex all" on every source page (AEM, DB, Web Crawler, Assets,
EDS, Catalog), with a confirmation that says the next run sends everything again. D341
(Index All trusting Turing over the ledger) is the complementary fix; this one gives the
operator the explicit control.

**Done when:** clearing a Web Crawler source's state leaves its ledger empty without
starting a run, and the next run sends every page.
