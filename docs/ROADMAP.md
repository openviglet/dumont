# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU1** (deps: —) **An AEM source JSON exported from the console has no id, so the first-start import of export/*.json fails on it** — The import looks each source up by id, and Spring Data refuses a null id, so the file the console hands out cannot seed a new instance. → §DU1
- 📋 **DU2** (deps: —) **AEM sources cannot be exported or imported as a set through the API, unlike DB and web-crawler sources** — DumAemExchangeProcess already builds the zip and reads it back, but no controller exposes it, so moving every AEM source to another instance means one download per source. → §DU2
- 📋 **DU3** (deps: —) **The console accepts a source name containing spaces, and that source then fails to index** — The name is the source key in index, reindex, audit and monitoring URLs, so an operator learns the rule only after a run fails. → §DU3
- 📋 **DU4** (deps: DU1, DU2) **The source lists offer no export-all or import action, so the export endpoints are reachable only by API calls** — An operator moving a configuration between environments has no way in the console to take every source out or put a file of them back in. → §DU4

## Non-goals
