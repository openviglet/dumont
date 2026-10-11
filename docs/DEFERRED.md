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
- ⏸ **D140** (deps: —) **A structured field cannot declare a search weight, so the manifest Dumont sends gives every field weight 1** — set aside (Turing T1082: searchWeight on the SDK spec): A plugin cannot say that its title outranks its body text, and a weight set by hand drifts from the schema. → §XLIII.14
