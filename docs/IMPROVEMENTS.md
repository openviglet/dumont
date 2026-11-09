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

### I.5 A focused home page with product-specific depth (D353)

Review of 2026-11-08: published pages and current source, compared with viglet.org. At
390 CSS pixels the published home measured 20,950 pixels tall. The illustrative
RunReport is a useful account of index drift, but the home combines connector inventory,
trust mechanisms, AI, WordPress and installation. The opening chip lists AEM, Solr and
Elasticsearch without naming Viglet Turing.

Lead with reliable source-to-index ingestion, the real sources and supported
destinations, one labelled run example, and a first-connector path. Keep AEM as a strong
dedicated solution and retain alternatives to Viglet Turing without turning the hero
into a technology list. Preserve information by linking to its authoritative page
instead of repeating the same inventory. Maintain existing anchor destinations with
concise summaries when sections move. Align the short product descriptor with the hub
but retain product-specific examples and technical depth. Give every retained section a
decision it helps the visitor make.

Acceptance: provide a before/after section map and walk a newcomer through what the
product does, whom it serves, evidence and the next action without reading the whole
page. Re-measure page length under the same viewport as supporting evidence, not as a
conversion target. No invented testimonials, unsupported performance claims (fix the
universal docs getting-started/intro.md:34 'Send to any search engine': three targets
only), deleted routes or promised conversion uplift; any outcome measurement needs later
observation.

### I.6 WordPress ownership and cross-product handoff (D354)

Review of 2026-11-08: published pages and current source, compared with viglet.org. The
published docs sidebar still lists WordPress inside Connectors
(docs/astro.config.mjs:121; the connectors/wordpress.md and wordpress-turing.md stubs
already hand off), although the overview and site-content.ts correctly state direct
integration with Viglet Turing. site-links.ts retains wordpressPlayground pointing to
/playground/ on the Viglet Turing host: that path returned 404, while /wp-demo/ returned
200. That constant is not currently rendered by the marketing tile, but public/playground/index.html still redirects visitors to that 404; point it at /wp-demo/.

Move WordPress discovery to a clearly named Viglet Turing integration handoff instead of
presenting it as a Viglet Dumont connector. Preserve old documentation URLs as short
explanatory pages or redirects with usable fallback links. Keep the visible marketing
tile linked directly to the Viglet Turing product page and canonical plugin docs. Remove
the unused invalid demo constant or point it to the canonical /wp-demo/ entry only if a
consumer needs it. Do not create a second plugin, duplicate instructions or operate the
WordPress demo from this project. Coordinate with existing T1256 deployment cleanup
rather than filing a duplicate migration task.

Acceptance: compare the published wording, linked documentation and source capability;
assert the corrected boundary in generated HTML and machine-readable text.

### I.7 Rendered brand and journey regression coverage (D355)

Review of 2026-11-08: published pages and current source, compared with viglet.org. The
sampled home and docs routes returned 200 at 1440/390 pixels without document-wide
overflow or page errors. That is a healthy baseline, not a whole-site accessibility
certification. Existing dumont-site build checks do not assert the new cross-site brand
contract.

dumont-site has no tests; extend its gates (tsc, starlight-links-validator,
scripts/check-readme-links.mjs) rather than adding a parallel framework. Enumerate
generated marketing routes and representative documentation templates; verify full
product names with technical exceptions, title/canonical/OG/JSON-LD/llms consistency,
internal anchors and the destination map. Add Playwright, absent from the workspace,
pinned exactly (@vitest/browser-playwright on the vitest 5.0.5 dumont-react uses), at
390/768/1440 pixels, both themes, reduced motion, keyboard navigation, menu
opening/closing and focus return. Inspect actual screenshots for clipping, readability
and decorative chips; do not substitute a CSS-class assertion for visual review. Test
consent fresh, denied and withdrawn without loosening it to simplify the layout, and
preserve demo failure states. Separate deterministic build checks from retry-bounded
external-link checks so network resets are reported as inconclusive, not product
regressions.

Acceptance: source checks run in validate.yml (push/PR, beside README links), output
checks in the build:site that dispatch-only publish-github-pages.yml runs; run the
existing gates plus the new checks and retain representative screenshots and a
route-result report. A later production smoke verifies deployed output; local success
alone never claims deployment. No performance-score or conversion guarantees.

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
