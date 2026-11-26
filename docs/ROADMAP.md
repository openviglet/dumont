# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU5** (deps: —) **npm run lint in dumont-react exits before checking a file, because the tree has no ESLint flat config** — ESLint 10 reads only eslint.config.js, so the frontend has no lint gate and drift goes unchecked. → §DU5
- 📋 **DU6** (deps: —) **The source APIs and both imports save a source name containing spaces, which then fails to index** — The console forms now refuse it, but an API call, the AEM import and the first-start import bypass them. → §DU6
- 📋 **DU7** (deps: —) **Every dumont-react build fails to generate the Module Federation type declarations, yet exits 0** — The generated tsconfig sets no jsx, so the remote ships no types to the Turing host and the error scrolls past unnoticed. → §DU7

## Non-goals
