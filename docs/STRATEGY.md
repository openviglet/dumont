# Dumont — Strategy & Positioning

> Positioning decisions that are **not** individual engineering tasks: what Dumont
> is, where its moat is, the bet it should make next, and what it deliberately will
> **not** become. Executable work lives in [ROADMAP.md](ROADMAP.md).

**Reading time**: ~5 minutes.

---

## What Dumont is today

Dumont is the **connector layer** of the Viglet platform: it collects content from
external systems and publishes it as documents into **Turing** (and optionally Solr /
Elasticsearch) for search, faceting and RAG. Its architecture has three solid pillars:

1. **A connector SPI** — [`DumConnectorPlugin`](../connector/connector-commons/src/main/java/com/viglet/dumont/connector/commons/plugin/DumConnectorPlugin.java)
   (`crawl` / `discoverContentIds` / `indexById` / `indexAll` / `ownsSource`), with
   several plugins coexisting in one JVM and routing by source ownership. The AEM
   connector is the mature reference implementation.
2. **A generic web crawler** — [`DumWCPluginProcess`](../web-crawler/wc-plugin/src/main/java/com/viglet/dumont/connector/plugin/webcrawler/DumWCPluginProcess.java),
   a Jsoup BFS link-spider whose extraction is driven by per-field CSS-selector
   classes (`DumWCExtInterface`) configured per source in the DB / React UI.
3. **Pluggable indexing** — `DumTuringIndexingPlugin` / Solr / Elasticsearch, fed by a
   batch → JMS pipeline with checksum-based reindex/skip strategies.

Dumont is **strong** at CMS-style sources (AEM) where content is already structured
and the connector mostly maps fields. Its **web crawler is a generic link-spider**:
good for "index this site's pages as text + a few attributes", weak for
**structured catalogs** where the value is in the *shape* of the source (tabular
listings, per-item detail pages with a rich, typed schema).

## The gap (why this track exists)

The **cursarei** project independently built, in Python, exactly the thing Dumont's
web crawler is *not*: a **structured-source extraction engine**. 120+ Brazilian
university catalogs, three reusable strategies (tabular SIGAA listings, sitemap,
HTML-listing), each producing a **normalized, typed, grounding-conservative record**
(level, modality, area taxonomy, city/UF, price, MEC/CAPES…), with anti-bot fetching,
politeness, dedup and LLM enrichment.

If you tried to express that today in Dumont, you'd force it into the web crawler's
CSS-selector model and **lose** the tabular parsing, the typed schema and the
conservative grounding — a regression. The right read is the opposite: cursarei is a
**specification of the structured-source connector Dumont should have as a product
capability**. Several gaps it exposes hurt *any* real-world source, not just cursarei:

- The web crawler assumes **ISO-8859-1** — wrong for UTF-8 sites (most of Brazil/LatAm).
- No **sitemap.xml / robots.txt** discovery or `Crawl-delay` politeness.
- A **single fetch path** (plain Jsoup) — no seam for TLS-fingerprint impersonation
  or headless rendering when a WAF or JS-rendered page blocks plain HTTP.
- **Single-threaded** per source; no bounded parallelism.
- No first-class **conservative grounding** (absent field vs empty string).
- No **enrichment stage** (LLM / derived fields) in the indexing chain.
- An empty crawl can **overwrite good data** — no "0 results ⇒ keep prior" guard.

## The bet

**Make Dumont state-of-the-art at *structured-source* connectors — not just
link-spidered HTML — and let cursarei be the reference that proves it.**

Concretely, add a **structured-source connector framework** above the web crawler:
a `discover → extract(id) → typed, schema-bound record` SPI, with reusable
declarative strategies (Sitemap, HtmlListing, TabularListing) and pluggable
fetch/enrichment. This is the durable product win — once it exists, **"index a new
structured source" becomes a small declarative adapter + a field manifest**, not a
bespoke stack. That is the "easier to index new content types / more scalable"
outcome the platform wants.

### Sequencing — sidecar first, native second (the "2 → 3" path)

cursarei's Python engine is genuinely best-in-class at anti-WAF collection
(`curl_cffi` TLS impersonation, fingerprint rotation, Wayback fallback) — harder to
reproduce on the JVM than to keep. So:

1. **Bridge (sidecar).** Ship a thin Dumont **catalog connector** that ingests the
   external engine's *already-normalized* records and publishes `TurSNJobItem`s to
   Turing (track C, **D10**). This gets the real user-facing wins (Turing search/RAG)
   live fast and keeps the Python engine as the mandated fallback.
2. **Native, leg-by-leg.** Build the structured-source framework (track A) + crawler
   hardening (track B), then migrate sources **one leva at a time** from the sidecar
   to native declarative strategies, gated on parity (**D11**). Sources with the
   hardest anti-bot posture may stay on the sidecar **indefinitely** — that is an
   acceptable end state, not a failure.

A full one-shot Java rewrite of the engine is explicitly **rejected**: maximum effort
and it throws away the anti-WAF edge that is the Python side's whole advantage.

## The second bet — adoption & trust on top of ingestion

The first bet (ingestion fidelity) answers *"can Dumont extract this source
correctly?"*. It does not answer the two questions that actually gate value once the
framework exists — and those two are where the **aggregate value** beyond a "good
connector" lives:

1. **Adoption — make a new source cheap, not just possible.** A declarative manifest is
   only a win if *producing* it is cheap. Today that still means a developer reading HTML
   and authoring selectors. The lever: **AI-assisted source inference** — point Dumont at
   a URL, get a draft strategy + field manifest that a human confirms (never auto-published,
   never a guessed value). This is what actually delivers the "easier to index new content
   types" outcome the platform wants; the framework is necessary but not sufficient for it.
   → ROADMAP Track D (D13–D14).
2. **Trust — make the output reliable and auditable.** A connector that silently drops a
   field when a catalog changes layout, with no record of where each published value came
   from, is not safe to feed a RAG answer. The levers: **drift monitoring** (catch the
   silent partial-degradation failure mode, not just total failure) and **field-level
   provenance** (every present field traceable to a source, enabling citation downstream).
   → ROADMAP Track E (D15–D16).

And the **differentiator** that earns the product's name — *Data Exchange Platform*, not
*pipe*: a semantics layer over the enrichment hook — **index-time embeddings**,
**cross-source entity resolution/dedup**, and a **reusable recipe & taxonomy library** so
one SIGAA recipe serves all the federals instead of being copied per source.
→ ROADMAP Track F (D17–D19).

All three layers are **grounding-safe by construction**: inference may omit, enrichment
may normalize/derive/merge — none may ever fabricate a sourced attribute. They sit
*above* the structured-source framework (Tracks A–C); they are not a substitute for it.
The sequencing follows: A–C are prerequisites, D13/D15 are the highest-leverage next
steps (they make the framework adoptable and the D11 parity gate measurable rather than
manual), and Track F follows Turing's RAG maturity. The design rationale for each task
is in [IMPROVEMENTS.md](IMPROVEMENTS.md).

## Non-goals

- **Not** a general-purpose competitor to Scrapy / Playwright crawler farms. Dumont
  crawls **to feed Turing**; breadth of the open web is not the goal — *fidelity of
  structured sources* is.
- **Not** a scraping framework that guesses. The grounding contract (omit absent
  fields) is non-negotiable; Dumont never invents values to fill a schema.
- **Not** a replacement for the AEM/CMS connectors. The structured-source framework
  sits **beside** them under the same `DumConnectorPlugin` SPI.
- **Not** AI authoring that decides for you. Inference (D13) drafts a manifest a human
  confirms — it never auto-publishes a source and never proposes a value it could not
  ground. Likewise enrichment (Track F) derives and merges over sourced data; it never
  invents a sourced attribute.

---

> This strategy is driven by the cursarei `V##` migration track and pairs with
> Turing **Block R** / `IMPROVEMENTS.md` §XIX. See [DOCS_INDEX.md](DOCS_INDEX.md) for
> the cross-repo map.
