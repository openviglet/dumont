# Structured vs. traditional sources

> **What this file is:** a conceptual reference — the *why* behind Dumont's two ways of
> modelling a source, when to reach for each, and how it maps to the roadmap tracks. It is
> explanatory, not a backlog; task status lives in [ROADMAP.md](ROADMAP.md) and the design
> rationale per task in [IMPROVEMENTS.md](IMPROVEMENTS.md).

---

## TL;DR

Dumont ingests a source one of two ways:

- **Traditional (loose-map):** the connector hand-rolls *discover → extract → publish* and
  emits a loose `Map<String,Object>` with `specs = null`. No declared schema; the search
  engine infers each document's shape at write time.
- **Structured (schema-as-code):** the source **declares its schema once**
  (`DumStructuredSchema`) and implements `discover()` + `extract(id) → DumStructuredRecord`.
  The generic `DumStructuredSourcePlugin` enforces grounding, mandatory-field checks, and
  publishes the schema spec with every document.

Both are correct — for different sources. The decision is a **spectrum**, not a war. The
industry is moving toward *declared contracts + observability at the ingestion boundary*
(driven by RAG/search trust), while keeping loose ingestion for genuinely unstructured
breadth. The winning shape is **layered**: a structured contract on top, a generic crawler
underneath — which is exactly Dumont's architecture.

---

## 1. The two approaches

Both solve the same problem — *pull content from an external system and publish it into
Turing for search/RAG* — but they decide **when the shape of the data is fixed** at
different moments.

### Traditional (loose-map / "schema-on-read")

The connector does everything by hand: enumerate items, extract each, publish a loose
`Map<String,Object>`. Nobody declared up front which fields exist or what type they are. In
Dumont this is what the **AEM, DB, WC and Assets** connectors do today — they emit attributes
with `specs = null`, and the indexer *infers* each document's shape on the fly.

> Philosophy: **"grab the data first, figure out what it is later."**

### Structured (schema-as-code / "schema-on-write")

The source **declares the contract once** — in code, via `DumStructuredSchema` (each field
with `name / type / multiValued / facet / mandatory`). It then implements three methods:

| Method | Responsibility |
|---|---|
| `schema()` | the declared field contract |
| `discover()` | the list of content ids to index |
| `extract(id)` | one **typed, validated** `DumStructuredRecord` |

The generic `DumStructuredSourcePlugin` owns the rest: grounding by construction (drop
null/blank/empty), mandatory-field enforcement (a record missing a mandatory field is skipped,
never fabricated), and publishing the schema's attribute specs alongside every document.

> Philosophy: **"declare what the data must be, and the system guarantees it."**

---

## 2. Side by side

| Dimension | Traditional (loose) | Structured (schema-as-code) |
|---|---|---|
| When the schema is fixed | At runtime, inferred per document | Declared once, up front |
| Validation / grounding | Late or absent (`""` leaks into facets) | By construction (drops blanks, enforces mandatory) |
| Cost to author | Low — just map and publish | Higher — you must declare the contract |
| Tolerance to heterogeneity | High (each item may differ) | Low (items must fit the schema) |
| **Observability** | Almost none | **Per-field coverage, drift, dry-run preview, provenance** |
| Reuse | Each connector reinvents the loop | One generic loop + pluggable enrichment |
| Typical failure mode | Degrades **silently** | Fails **visibly and named** |

---

## 3. Why this distinction is the lever (not academic)

The single most important row above is **observability**, and it is worth making concrete:

**Per-field coverage (D15) only exists because there is a schema.** To measure "what % of
documents carried a value for `workload`", you must know *a priori* that `workload` is supposed
to exist. Without a declared contract, "field missing" is indistinguishable from "field that
never existed". That is precisely why, after D15 shipped, the coverage page shows *no history*
for the loose-map connectors: they declare no schema, so there is nothing to measure. The whole
trust stack stands on the contract:

- **D03 — grounding by construction.** A value can only be set on a declared field; absent
  fields are omitted, never sent as `""`.
- **D14 — dry-run preview.** "What would this run produce?" is only answerable against a known
  schema and a baseline.
- **D15 — coverage & drift.** A field whose coverage drops sharply between two runs is a
  layout/selector break — visible only because the field was declared.
- **D16 — field-level provenance.** Citing/auditing where a field came from presupposes the
  field is a first-class, declared thing.

> **Rule of thumb that decides it:** *"if a field stops arriving tomorrow, do I need to
> know?"* If yes → structured. If "whatever" → loose is fine.

---

## 4. Mental models

The same tension recurs across computing — if one of these clicks, the whole thing clicks:

- **Dynamic vs. static typing** — loose Python vs. typed Java/TypeScript: trade freedom for
  verifiable guarantees.
- **Schemaless NoSQL vs. relational** — a bag of JSON docs vs. typed columns with `NOT NULL`.
- **Data lake (schema-on-read) vs. warehouse (schema-on-write)** — dump raw and interpret
  later vs. model at the door.
- **Loose REST JSON vs. GraphQL / Protobuf / OpenAPI** — "send anything" vs. a contract the
  consumer can trust and audit.

In every case: **loose optimizes for speed-of-authoring and tolerance of chaos; structured
optimizes for trust, governance and composition.**

---

## 5. Is it normal to have both? Yes — and it is the correct design.

This is not "one will kill the other". It is a **spectrum**, and the choice turns on two
questions:

1. *How structured is the source actually?*
2. *How much do I need to trust / observe the output?*

- Highly structured source (a database table, a CMS content model, a course catalog) →
  structured pays off handsomely.
- Genuinely chaotic source (open-web crawl, arbitrary file dump) → loose is the pragmatic
  floor; imposing a schema would fake an order that isn't there.

Dumont **encodes this coexistence on purpose**: the structured framework was built to sit
*above* the generic web-crawler link-spider. That is why the web crawler is deliberately **not**
converted to a structured source — a link-following crawl has no enumerable set of typed
records, and forcing `discover()/extract(id)` on it would destroy the very strength it has.
AEM/DB/Assets, on the other hand, *are* structured underneath and simply never **declared** it
— which is what [Track G](ROADMAP.md) (connector convergence) addresses, via thin adapters that
reuse each connector's existing extraction without a rewrite.

> **Golden rule:** declare the contract at the boundary where trust matters; defer it where it
> would only get in the way.

---

## 6. Where the market is going

Nuance matters here — anyone saying "it's all structured now" is over-simplifying. The real
trend is sharper:

> **The market is moving toward "declared contract + observability at the ingestion boundary",
> especially where AI/search consumes the data.** Raw ingestion stays alive where the transform
> is pushed downstream.

What pulls toward structured:

1. **The RAG/LLM era.** Garbage-in becomes *hallucination*-out. For an LLM to cite a source and
   for a human to audit an answer, you need to know the origin and shape of every field —
   exactly what grounding (D03) + coverage (D15) + provenance (D16) provide, and none of which
   exist without a schema.
2. **The "data contracts" / shift-left-quality movement.** Data mesh and data observability
   (Great Expectations, Monte Carlo) all preach: declare the contract, detect the break *before*
   it reaches the consumer. Drift detection (what D15 does) is this idea applied at ingestion.
3. **Connector frameworks already standardized on schema.** Airbyte, Fivetran, Singer/Meltano
   model a source as *declared streams with a JSON Schema per stream* — i.e. schema-as-code is
   already the de-facto connector contract.

The counterweight is still strong: **lakehouse + ELT** (dump raw, transform later with dbt) is
huge and schema-on-read has not died. So the winning synthesis is **not** "structured kills
loose" — it is the **layered architecture**: a structured, observable contract on top, a generic
ingestor underneath for the chaotic tail. Which is, again, Dumont's design.

---

## 7. Decision guide

| Use **structured** when… | Use **traditional** when… |
|---|---|
| The source has a stable, known shape | The source is heterogeneous / unpredictable |
| The data feeds RAG/search that must be trustworthy | It's a best-effort index; coverage > precision |
| You need coverage / drift / preview / provenance | Fine-grained observability isn't a requirement |
| Many consumers depend on the fields | Consumption is internal and tolerant |

---

## 8. How this maps to Dumont

The roadmap tells this story end to end:

- **Track A** created the structured SPI as the one ingestion contract (D01–D04).
- **D03 / D14 / D15 / D16** stack grounding, preview, coverage/drift and provenance *on top of
  that contract* — the trust layer.
- **Track G** (connector convergence) realizes that AEM/DB/Assets were *already structured
  underneath, they just never declared it* — so a thin adapter brings them under the contract
  and they inherit the whole machinery, **without a rewrite**.
- **The web crawler stays out by design** — it is the generic layer the framework was built to
  sit above; it gets only a lightweight coverage signal (D23), and structured sites it serves
  migrate to the D02 declarative strategies.

**One-sentence thesis:** the future is not "structured *instead of* traditional" — it is **a
structured, observable contract at the boundary, with a generic ingestor beneath it for what
doesn't fit a contract.** Dumont is converging on that point, and Track G is the step that
unifies the legacy connectors under it.

---

## See also

- [STRATEGY.md](STRATEGY.md) — the structured-source bet and positioning.
- [ROADMAP.md](ROADMAP.md) — Track A (framework), Track E (trust), Track G (convergence).
- [IMPROVEMENTS.md](IMPROVEMENTS.md) — §I (the second bet), §II (connector convergence).
- `agents.md` → "Structured-source framework" and the grounding contract.
