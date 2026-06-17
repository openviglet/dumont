# Embedding partition contract (D17)

Reference for **how structured content reaches Turing's index-time embedding**, and how a
Dumont source shapes that content so it embeds well. Read this when declaring a structured
schema for a source whose meaningful text lives under non-standard field names.

## The short version

Turing already generates embeddings **at index time**, on the exact path Dumont uses —
`DumTuringIndexingPlugin.index()` → `POST /api/sn/import` → `TurSNImportAPI.sendToGenAi()` →
`TurSNGenAi.addDocuments()` — **when the target SN site has RAG enabled**. There is no
separate embed pass, and `/api/sn/import` does not accept a pre-computed vector. So Dumont
does **not** generate embeddings; it shapes the document so Turing's embedder sees the right
text.

## What Turing's partitioner reads

`TurSNGenAi.partitionFields(attributes)` splits a document's attributes into the embedding
input by a **hardcoded field-name rule**:

| Canonical attribute | Goes to | Effect on the embedding |
|---------------------|---------|-------------------------|
| `title` | chunk **header** (`# <title>`) | Propagated to every chunk so each chunk is self-contained and carries the document identity. |
| `abstract` | embedded **body** | Long-form content that gets chunked and embedded. |
| `text` | embedded **body** | Long-form content that gets chunked and embedded. |
| any other *simple* short field | header (`key: value`) **and** facet metadata | Used for filtering/facets; weak signal for semantic recall. |
| any other *simple* long field | body, prefixed `key: value` | Reaches the embedding, but as noisy `key:`-prefixed prose. |
| complex / nested field | **dropped** | Never reaches the embedding. |

If `title`, `abstract` and `text` are **all** absent, the document is logged as having "no
indexable text" and the embedding quality is whatever the `key: value` fallback produced.

**Consequence:** a structured source whose primary content lives under a custom name (e.g.
`course_name`, `ementa`, `description`) embeds poorly — the heading is missing and the body
is noisy `key:`-prefixed text — unless the source maps its content onto the canonical keys.

## How a Dumont source targets the contract

Declare an **embedding role** on the schema field (`DumEmbeddingRole`). At publish time
(`DumStructuredSourcePlugin.toJobItem`) the field's value is **projected** onto the canonical
key the partitioner reads, *in addition to* keeping the original field for display/facets.

| Role | Projects onto | Use for |
|------|---------------|---------|
| `NONE` (default) | — | metadata / facets only |
| `TITLE` | `title` | the document's primary heading |
| `SUMMARY` | `abstract` | a short summary |
| `BODY` | `text` | long-form content to embed |

Schema-as-code:

```java
DumStructuredSchema.builder()
    .title("course_name")   // → projected onto `title`  (TEXT + TITLE role)
    .body("ementa")         // → projected onto `text`   (TEXT + BODY role)
    .facet("area")          // stays a facet, not embedded
    .build();
```

or explicitly: `DumStructuredField.builder("course_name").embeddingRole(DumEmbeddingRole.TITLE).build()`.

### Projection rules (grounding-safe by construction)

- Only a **present** value is projected — `DumStructuredRecord` already dropped blanks, so a
  canonical key is never synthesized for an absent field.
- An **explicitly-supplied** canonical key always wins (`putIfAbsent`); projection never
  overwrites a value the source set directly.
- If the schema declares the canonical field itself (a field literally named `title`/
  `abstract`/`text`), the mapping is 1:1 and projection is skipped.
- The original field is **kept** — nothing is renamed, nothing is fabricated.
- If two fields claim the same role, the **first declared wins**.

The canonical targets are core SN fields, so projection adds no attribute spec — only the
value is copied.

## Operating checklist

1. The target SN site must have **RAG enabled/bound** (default LLM + embedding model +
   store configured) — otherwise the document is keyword-only, regardless of roles. See
   `turing/2026.1/docs/RAG_HYBRID_CONFIG.md`.
2. The source's primary heading carries `TITLE`; its long-form content carries `BODY`
   (and/or `SUMMARY`).
3. Verify: Turing's RAG chunk debug log (`TurSNGenAi` at DEBUG) shows the `# <title>`
   header and clean body prose per chunk; semantic retrieval returns the document for a
   meaning-based query, not just a keyword match.

## See also

- `IMPROVEMENTS.md` §VI — the rationale and why the original "embed in Dumont" framing was
  dropped.
- `turing/2026.1/docs/RAG_HYBRID_CONFIG.md` — embedding model/store + hybrid retrieval setup.
