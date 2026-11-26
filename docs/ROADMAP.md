# Roadmap (active backlog)

## Block A — Source configuration moves between instances

- 📋 **DU12** (deps: DU9 ✅, DU10 ✅, DU11 ✅) **The source lists cannot replace every source from a JSON file, nor warn that everything will be overwritten** — An operator syncing environments has only the adding import in the console, with no confirmation of what gets removed. → §DU12
- 📋 **DU13** (deps: —) **The AEM plugin's JPA integration test cannot start, and the build never runs it** — DumAemSourceApiIT finds no @SpringBootConfiguration and surefire skips *IT, so persistence behaviour such as DU9's replace is never checked against a database. → §DU13

## Non-goals
