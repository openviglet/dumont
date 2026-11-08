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

### I.2 Full product names across the public site (D350)

Review of 2026-11-08: published pages and current source, compared with viglet.org.
hero.tsx and FOOTER_BLURB use Turing ES; site-links.ts renders With Turing and Shio and
Dumont for AEM. The published docs title still ends in Viglet Dumont DEP.

Apply the owner decision in hub docs/STRATEGY.md section I.7 to dumont-site/src,
dumont-site/docs, accessible labels, titles, descriptions, OG assets, JSON-LD, llms.txt
and generated Markdown twins. Always use Viglet Turing, Viglet Dumont and Viglet Shio,
including repeated mentions. Share typed product identity data rather than maintaining
shorter display aliases. Keep stable slugs, package names, commands, code identifiers,
filenames and historical release records intact; classify these exceptions explicitly.
Ensure the product identity remains visible on small screens by changing layout rather
than abbreviating. Category labels remain separate from names.

Acceptance: scan rendered visitor text and machine-readable outputs, not only source
literals; report each intentional technical exception. Review desktop and 390-pixel
navigation, long headings, buttons and docs search results. No backend renames or
technical API changes.

### I.3 A consistent visual contract with viglet.org (D351)

Review of 2026-11-08: published pages and current source, compared with viglet.org.
dumont-site/src/components/sections/hero.tsx uses a rounded metadata chip, hardcoded
dark surfaces, gradient text, grid and animated orbs; the hub uses neutral surfaces and
plain typographic metadata. Header product highlights also use pills.

Adopt the hub typography roles, title scale, content gutters, spacing, button hierarchy,
borders and focus treatment. Use released @viglet/viglet-design-system tokens/components
where applicable and a thin compatible Astro mapping for documentation; inspect actual
exports before designing an adapter. Preserve teal as the product accent. Replace
decorative chips with plain eyebrow text, separators or ordinary navigation links. Keep
genuine filter selections and status controls functional. Consolidate a reusable product
hero for marketing subpages; documentation keeps its reading sidebar and article layout
while sharing brand tokens. Avoid permanent dark heroes in light mode and remove
decorative motion that competes with the product evidence.

Acceptance: compare home, one solution page, a feature page, docs overview and article
at 390/768/1440 pixels, light/dark and reduced motion. Preserve real demos, code
examples, contrast, keyboard focus and original URLs. No framework migration or forced
orange product branding.

### I.4 Navigation, installation and release destinations (D352)

Review of 2026-11-08: published pages and current source, compared with viglet.org.
site-links.ts already defines install but the header Get started goes to the docs
overview, and Changelog points to the internal GitHub task ledger. The hub already
offers /dumont/download/ and /dumont/release-notes/ using the product release feed.

Define one typed destination map used by marketing navigation, footer, CTAs and Astro
overrides. Separate Try/Explore, Install, Documentation, Release notes, Source and
Portfolio by their actual job. Get started should reach an actionable supported setup
path with prerequisites and a first-success check; Documentation may keep the overview.
Keep GitHub Source on openviglet/dumont; label the development roadmap separately from
user release notes. Use existing hub download/release pages and product documentation
instead of adding another release ledger or installation authority. Replace
retired-domain references with verified current destinations, preserving inbound routes
and anchors. The return to viglet.org and product identity must remain discoverable in
mobile menus and docs.

Acceptance: follow every primary CTA from home, a depth page and docs in a fresh
browser, including narrow-screen menus and keyboard use; verify response status and
destination content rather than URL spelling alone. Check release-feed availability and
no circular hub/product redirects. Do not deploy or rewrite historical published slugs.

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
conversion target. No invented testimonials, unsupported performance claims, deleted
routes or promised conversion uplift; any outcome measurement needs later observation.

### I.6 WordPress ownership and cross-product handoff (D354)

Review of 2026-11-08: published pages and current source, compared with viglet.org. The
published docs sidebar still lists WordPress inside Connectors, although the overview
and site-content.ts correctly state direct integration with Viglet Turing. site-links.ts
retains wordpressPlayground pointing to /playground/ on the Viglet Turing host: that
path returned 404, while /wp-demo/ returned 200. That constant is not currently rendered
by the marketing tile.

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

Extend existing tests rather than adding a parallel framework. Enumerate generated
marketing routes and representative documentation templates; verify full product names
with technical exceptions, title/canonical/OG/JSON-LD/llms consistency, internal anchors
and the destination map. Add Playwright coverage at 390/768/1440 pixels, both themes,
reduced motion, keyboard navigation, menu opening/closing and focus return. Inspect
actual screenshots for clipping, readability and decorative chips; do not substitute a
CSS-class assertion for visual review. Test consent fresh, denied and withdrawn without
loosening it to simplify the layout, and preserve demo failure states. Separate
deterministic build checks from retry-bounded external-link checks so network resets are
reported as inconclusive, not product regressions.

Acceptance: run the existing site/docs build gates plus the new checks and retain
representative screenshots and a route-result report. A later production smoke verifies
deployed output; local success alone never claims deployment. No performance-score or
conversion guarantees.

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
