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

### XLIII.8 No grounded LLM extraction or summary (D131)

Met in trabalharei (TRA28, job description enrichment) against Dumont 2026.3.

trabalharei wants, for each job with a full description, the required skills (an open
list), and a two-line summary for its job page and abstract, both grounded in the text.
The only LLM surface a plugin can reach is `DumClassifierFactory` (D106): it builds a
`DumClassifier` that answers one label of a closed set (D107), cached per input. That
cannot express a list of values, an open vocabulary drawn from the text, or a generated
summary, so the plugin has no grounded LLM step for either.

Expected: a second factory method, or an `DumExtractor` beside `DumClassifier`, that
takes a task prompt and answers a typed result: a list of strings each of which must
occur in the input (checked after the model answers, so an invented skill is dropped),
or a short text with a length cap. Same contract as the classifier: empty when nothing
is grounded, a thrown error not cached, answers cached by input hash under the task's
prefix, and a per-day call cap the plugin sets. A `DumExtractionEnricher` writing a
multi-valued field (skills) and a text field (the summary into `abstract`) would make it
declarative.

Workaround in trabalharei: none. Benefits ship from a deterministic dictionary; skills
and the summary wait on this line (TRA28's remainder).

### XLIII.10 Run outcomes as Prometheus metrics (D136)

Met in trabalharei (TRA62, monitoring) while wiring viglet/cloud's Prometheus and
Alertmanager for the trabalharei jobs source.

viglet/cloud alerts on everything through Prometheus (`docker/observability`), and
trabalharei wanted one alert: "the last ingestion run of a source failed, or no run
finished in the last N hours". Dumont keeps run records (the console shows them, and the
jobs extension writes its own `DumJobRun` notes), but nothing outside the console can
read a run's outcome: there is no `/actuator/prometheus` (viglet/cloud's C086 is still a
commented-out scrape job waiting on it), and no documented JSON endpoint for "last run
per source, status, finished at, items indexed / failed".

What came back: nothing to scrape. Workaround in trabalharei: none for run failures; it
alerts only on the visible effect (open jobs under 20% of the past week's peak, from the
site's own /api/metrics/), which fires late and cannot tell a failed run from a source
that legitimately shrank.

Expected: Micrometer gauges and counters per connector source, exposed on
`/actuator/prometheus` (behind the management port, not the public one):
`dumont_run_last_status{source}` (1 ok, 0 failed),
`dumont_run_last_finished_seconds{source}`,
`dumont_run_items_total{source,outcome="indexed|deleted|failed"}`, and
`dumont_run_duration_seconds{source}`. Then a cloud rule such as `dumont_run_last_status
== 0` or `time() - dumont_run_last_finished_seconds > 2*3600` pages on a broken or
stalled schedule for any plugin, not only trabalharei's.

### XLIII.12 Failed deliveries stay pending until Turing accepts them (D138)

Met in trabalharei (TRA25), with `PROOF_OUTAGE=1 node scripts/dumont-publish-proof.mjs`
against the local stack (Dumont 2026.3 jars, Turing CE local).

Steps: a structured source publishes 8 documents with parity. The fixtures then gain one
new posting and the Turing container is stopped (`docker stop`) for one run. The run
finishes "complete, 9 sent"; the connector queue then logs `ERROR
c.v.turing.client.sn.job.TurSNJobUtils : No route to host` and drops the batch. Turing
is started again and the next run finishes "complete, 9 sent, 9 recorded", but the site
holds 8: `job-bd33f8...` is in Dumont's ledger and never in Turing. Every later run
treats it as unchanged, so it is never sent again until its content changes.

Expected: the ledger records a document only once Turing accepted it, or a failed
delivery marks its ledger rows (checksum cleared, or a `pending` state) so the next run
re-sends them; and the run whose batch failed is not recorded as complete. The queue's
failure should also name the batch and source on the run record (D96), not only the log.

Workaround in trabalharei: none; the proof's outage step fails until this ships, and
TRA25 waits on it.

### XLIII.13 Failed provisioning shows on the run (D139)

Met in trabalharei (TRA25), running a structured source while the Turing container was
paused. `DumTuringIndexingPlugin.provision` logged `ERROR Could not provision site
'trabalharei-vagas': Could not reach the manifest endpoint at
http://turing:2700/api/sn/manifest` (`SocketTimeoutException: Read timed out`, after
about three minutes), and the run went on and was recorded "complete".

A site whose fields were not provisioned can reject or mis-index what the run sends (a
new field declared since the last provisioning never reaches Turing), and nothing on the
run record says provisioning failed.

Expected: a failed provisioning marks the run partial with the reason on its record
(D96), or stops it before sending; and the manifest call uses a shorter read timeout
than the three minutes seen, since the run waits on it.

Workaround in trabalharei: none.

### XLIII.14 Search weights in the structured schema (D140)

Met in trabalharei (TRA79). The site's field contract weighs searches as title 10,
company 3, abstract 2, text 1 (`JOB_FIELDS` in `src/lib/jobDoc.ts`), so a word in a
job's title outranks the same word deep in its description. Turing applies a field's
search weight since T1065, and its manifest declares one per field since T1072
(`searchWeight`, 1 to 100, a non-breaking change for the planner).

Dumont provisions the SN site from the structured schema (`DumTuringIndexingPlugin`
through `TurSNManifestClient`), and `DumStructuredField.Builder` has `type`,
`multiValued`, `facet`, `mandatory`, `description` and `embeddingRole`, but no search
weight. The manifest Dumont sends carries none, so every field searches at weight 1 and
a plugin cannot state its ranking; a weight set by hand in the console is the only way,
and nothing keeps it in step with the plugin.

Expected: `DumStructuredField.Builder.searchWeight(int)` (1 to 100), carried into the
manifest field as `searchWeight`, with the schema helpers (`title`, `body`, `text`) left
at the default. A test pins the manifest field.

Workaround in trabalharei: none; the weights stay in the site's contract until this
ships, and TRA79's remainder waits on it.

## XLIV Frontend build and lint gates

### XLIV.2 Unpatched xlsx through the design system (D134)

Found in the Dependabot alerts on `openviglet/dumont` (2026-10-11): four high alerts,
prototype pollution and a ReDoS, on `xlsx` 0.18.5. SheetJS stopped publishing to npm at
0.18.5; the fixed releases (0.20.3 today) are served only from
`https://cdn.sheetjs.com`.

Landed: `dumont-react` no longer declares `xlsx`, which no source imported. What still
pulls it is `@viglet/viglet-design-system`, whose `dependencies` carry `xlsx` ^0.18.5
for `exportToXlsx`.

What did not work: a `pnpm-workspace.yaml` override pointing `xlsx` at the CDN tarball.
pnpm 11 refuses it with `ERR_PNPM_EXOTIC_SUBDEP`, since `blockExoticSubdeps` forbids a
subdependency resolved by URL, and turning that policy off to silence an alert would be
the worse trade. The same rule means the design system cannot fix it by depending on the
tarball itself: every pnpm consumer would hit the refusal.

The fix, filed as VDS211 in the design system's roadmap: it takes `xlsx` as a
`peerDependency`, so each consumer declares the CDN tarball as its own direct
dependency, which pnpm allows. Then `dumont-react` adds `"xlsx":
"https://cdn.sheetjs.com/xlsx-0.20.3/xlsx-0.20.3.tgz"` and the alerts close.

Exposure meanwhile is low: `exportToXlsx` only writes a workbook (`aoa_to_sheet`,
`writeFile`), and both CVEs are in the parser. Nothing in the console reads a
spreadsheet.
