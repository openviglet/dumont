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

### I.3 A consistent visual contract with viglet.org (D351)

Surfaces and chips landed (hero, page hero, AEM band, CTA, flow panel, nav and docs
metadata). Left: the tokens and fonts.

dumont-site does not depend on @viglet/viglet-design-system. Its tokens.css already
aliases the shadcn names (--background, --primary, --ring, ...) onto --vg-*, so
src/tokens.css becomes an import plus Dumont's keys: --vg-primary-base and -dark with
their foregrounds, --vg-accent-from/-to/-text/-text-dark and the --vg-brand set, all
teal; the hand-made green-grey neutrals go, so the ground is the hub's. fonts.css brings
Inter for UI and Plus Jakarta Sans as --font-brand on h1/h2, as viglet.org sets them;
JetBrains Mono is not bundled and keeps its Google Fonts link. Docs read the same file
through customCss, so they follow with no second edit. Use tokens.css and fonts.css, not
the ./styles Tailwind bundle, which overrides arbitrary responsive utilities.

Blocked on install (2026-11-09): pnpm add in dumont-site fails with
ERR_PNPM_EXOTIC_SUBDEP. xlsx is a required peer of the design system, and the workspace
overrides xlsx to the SheetJS CDN tarball (D134), which blockExoticSubdeps refuses for a
package that does not declare it. dumont-react declares that tarball directly. Do not
relax blockExoticSubdeps and do not add xlsx to a site that never uses it: the fix is
the design system listing xlsx under peerDependenciesMeta as optional, as it already
does for @resvg/resvg-js and react-router-dom.

Acceptance: compare home, one solution page, a feature page, docs overview and article
at 390/768/1440 pixels, light/dark and reduced motion. Preserve real demos, code
examples, contrast, keyboard focus and original URLs. No framework migration or forced
orange product branding.

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

## L The documentation area

The end-user documentation moves from vigletdocs into this repository, as an Astro +
Starlight area of dumont-site served at dumont.viglet.org/docs, themed from the design
system, and docs.viglet.org/dumont later redirects to it.

## LI Releases and links match what the site describes

Filed from the portfolio review of 2026-10-30 (visibility repo, estrategia/portfolio.md
and produtos/dumont.md): the release, the names on the files and the links in the README
trail the site and the 2026.3 line.

## LII Academic repositories as sources (DSpace, OAI-PMH)

## LIII Document text extraction for downstream processing

Consumers that classify documents or extract fields from them need text page by page and
OCR spent only where a page has no text layer.

### LIII.1 Text extraction loses page boundaries (D356)

Met while designing the document classification service (`d:\Git\vilt\classification`),
which receives files through the Dumont assets connector and needs text page by page.

`DumTextExtractor.extract(...)` (connector-commons, D315) returns one `Extraction` whose
`text` is the whole document as a single string from Tika's `BodyContentHandler`. Page
boundaries are gone by then, so a consumer cannot:

- send only the first pages of a long contract to a classifier;
- cite the page a value was read from, which grounding a field requires;
- run OCR only on the pages that have no text layer (see the OCR line in this track);
- report per-page outcome (text layer, OCR'd, empty, failed).

Expected: an opt-in page mode, e.g. `extractPages(Path)` returning `List<Page>(number,
text, source = TEXT_LAYER | OCR | NONE, ocrConfidence)` plus the document-level
`Outcome`. Tika's PDF parser already emits a `<div class="page">` per page through the
XHTML SAX stream, so a page-aware content handler can split there; Office formats can
stay a single page. The current `extract` stays as it is for indexing.

No workaround written yet: the classification service will wait on this line rather than
parse PDFs with its own PDFBox copy.

### LIII.2 OCR is not decided per page (D357)

Met while designing the document classification service (`d:\Git\vilt\classification`),
which will read large folders of contracts and invoices through the assets connector.

`DumFileUtils.configure(ParseContext)` (assets-commons) sets
`PDFParserConfig.setExtractInlineImages(true)` and a default `TesseractOCRConfig` for
every parse. The effect is OCR on every image embedded in every PDF: logos, signatures
and stamps in a born-digital contract are OCR'd although the page already has a text
layer, while the cost of a scanned page and a digital one is not told apart. On a bulk
run OCR dominates the time per document.

Expected:

- OCR decided per page: Tika's PDF OCR strategy `AUTO` (OCR a page only when its text
  layer is short or mostly unmapped Unicode), with the thresholds configurable per source;
- inline-image extraction off by default, on per source when someone asks for it;
- Tesseract language configurable per source (`por+eng` for Brazilian documents), and the
  render DPI as well;
- the outcome says which pages were OCR'd, so a run record shows the OCR cost.

No workaround written yet: the classification service will depend on this line.

## LIV Enrichment by external services

Enrichers that hand a record to an external service, such as a document classifier, and
wait for its answer without holding the run.

### LIV.1 No asynchronous enrichment (D358)

Met while designing Minos, the document classification service
(`d:\Git\vilt\classification`, task CL48), which Dumont's assets connector is meant to
call for every new or changed file.

The natural hook is a `DumEnricher` in the `DumEnrichmentChain`: it runs after
extraction and before publish, and its contributions are grounded through the schema.
But `enrich(source, record)` is synchronous and returns the contributions in the same
call. Classifying a document can take seconds to minutes (page OCR, several model calls,
an escalation), so a synchronous enricher holds the run on each record, and a timeout
publishes the document without its classification.

Expected: an asynchronous enrichment path, for example:

- an enricher may answer "pending" with a correlation id instead of contributions;
- Dumont keeps the record (or its id) as pending and continues the run;
- the external service calls back (or Dumont polls) with the contributions;
- Dumont merges them through the same schema-bound builder, publishes the record, and the
  run record shows how many are still pending and for how long;
- a pending record that never gets an answer is published without the contributions after
  a configurable time, or held, per source.

No workaround written yet: Minos waits on this line for its Dumont integration.

## LV Source configuration moves between instances

Moving a set of sources from one Dumont instance to another (development to production,
or seeding a new install through the first-start import of export/*.json) is today a mix
of per-source downloads, hand-edited files and API calls. These lines close the path
from the console's export to a working import, and keep a source name from breaking the
run it keys.
