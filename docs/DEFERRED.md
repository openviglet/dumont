# Set aside

## Track B — Web-crawler hardening (real-world sources)

## Track C — cursarei reference connector

## Track F — Enrichment & semantics (the differentiator)

## Track G — Connector convergence (loose-map connectors → structured SPI)

## Track H — AEM extraction model redesign (component-oriented, per-type, recipe-driven)

## Track J — Console surfaces move to bento chrome

## Track K — Frontend build and lint gates

- ⏸ **D134** (deps: —) **The console ships xlsx 0.18.5, whose prototype pollution and ReDoS have no fixed release on npm** — set aside (a design system release that takes xlsx as a peer (VDS roadmap)): The design system still pulls xlsx 0.18.5. → §XLIV.2

## Track O — Structured sources over paged JSON APIs

## Track P — Connector run control

- ⏸ **D131** (deps: —) **A plugin's only LLM step answers one label of a closed set: no list extracted from the text, no grounded summary** — set aside (a product call: may a generated summary fill abstract under the no-fabrication non-goal): The grounded summary half is not built. → §XLIII.8

## Track Q — Builds from released dependencies

## Track R — API access and credential handling

## Track D — Adoption & authoring

## Track A — Structured-source connector framework

## Track S — The WordPress connector plugin

- ⏸ **D182** (deps: D161 ✅) **No CI job opens the plugin's screens: the browser suite runs only on a laptop** — set aside (Waits for the owner to push and review the first run's baselines.): The CI job exists but has never run, so no linux baselines exist. → §XLIX.38
- ⏸ **D190** (deps: D148 ✅, D177 ✅) **Each site of a shared SN site is kept apart by the plugin's own filters, and its key can still reach the other sites** — set aside (Who keeps the shared key is the owner's call.): The stored key still reaches every site sharing the SN site. → §XLIX.46
- ⏸ **D225** (deps: —) **The readme would fail the WordPress.org review: no External services section, a GitHub-only install, no source link** — set aside (Needs a person: wp.org accounts, validator run.): Left: real wordpress.org accounts in Contributors, and a pass on the directory's readme validator. → §XLIX.81
- ⏸ **D229** (deps: —) **Plugin Check reports 23 warnings on the package, none fixed or annotated, so the CI gate passes them silently** — set aside (Waits on the plugin name, a person's call (D225).): Left: the name's trademark warning, decided with D225, then failing CI on warnings too. → §XLIX.85
