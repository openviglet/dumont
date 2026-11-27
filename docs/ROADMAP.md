# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU16** (deps: DU14 ✅, DU15 ✅) **The source lists offer three overlapping imports, and none says which existing sources a file will replace** — An operator must guess between Import JSON, Import ZIP or JSON and replace-all before picking a file. → §DU16
- 📋 **DU17** (deps: —) **An imported source that omits localePaths, attributes or models fails the whole import with a NullPointerException** — Jackson builds the exchange bean without its builder defaults, so a hand-written or minimal source file cannot be imported. → §DU17

## Non-goals
