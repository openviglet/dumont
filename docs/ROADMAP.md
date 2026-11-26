# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- ⏳ **DU8** (deps: —) **Two react-hooks rules are switched off in 31 named console files, so new violations there pass the lint gate** — react-hooks/set-state-in-effect is still off in 19 named files in eslint.config.js; empty that list and delete its block. → §DU8

## Done when — DU8

- **eslint.config.js holds no rule turned off in named files** Checked by reading
  dumont-react/eslint.config.js: no block that turns a rule off for a files list is
  left, and npm run lint still exits 0.

## Non-goals
