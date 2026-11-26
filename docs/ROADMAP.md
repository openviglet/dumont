# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU6** (deps: —) **The source APIs and both imports save a source name containing spaces, which then fails to index** — The console forms now refuse it, but an API call, the AEM import and the first-start import bypass them. → §DU6
- 📋 **DU7** (deps: —) **Every dumont-react build fails to generate the Module Federation type declarations, yet exits 0** — The generated tsconfig sets no jsx, so the remote ships no types to the Turing host and the error scrolls past unnoticed. → §DU7
- 📋 **DU8** (deps: —) **Two react-hooks rules are switched off in 31 named console files, so new violations there pass the lint gate** — set-state-in-effect and exhaustive-deps hold the rest of the lint debt DU5 left as named-file lists in eslint.config.js. → §DU8

## Done when — DU8

- **eslint.config.js holds no rule turned off in named files** Checked by reading
  dumont-react/eslint.config.js: no block that turns a rule off for a files list is
  left, and npm run lint still exits 0.

## Non-goals
