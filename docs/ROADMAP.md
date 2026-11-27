# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU15** (deps: —) **The server imports silently ignore the JSON a source page exports for a single source** — They read only the sources array of export.json, so a single-source file imports nothing and reports no error. → §DU15
- 📋 **DU16** (deps: DU14 ✅, DU15) **The source lists offer three overlapping imports, and none says which existing sources a file will replace** — An operator must guess between Import JSON, Import ZIP or JSON and replace-all before picking a file. → §DU16

## Non-goals
