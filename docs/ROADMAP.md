# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU4** (deps: DU1 ✅, DU2 ✅) **The source lists offer no export-all or import action, so the export endpoints are reachable only by API calls** — An operator moving a configuration between environments has no way in the console to take every source out or put a file of them back in. → §DU4
- 📋 **DU5** (deps: —) **npm run lint in dumont-react exits before checking a file, because the tree has no ESLint flat config** — ESLint 10 reads only eslint.config.js, so the frontend has no lint gate and drift goes unchecked. → §DU5
- 📋 **DU6** (deps: —) **The source APIs and both imports save a source name containing spaces, which then fails to index** — The console forms now refuse it, but an API call, the AEM import and the first-start import bypass them. → §DU6

## Non-goals
