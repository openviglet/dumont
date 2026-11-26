# Improvements

## Block A — Source configuration moves between instances

### §DU12 Replace-all import in the console

With DU9, DU10 and DU11 the server can replace every source of a connector type from one
JSON file, but the source lists only offer Import ZIP, which adds.

Build: an "Import and replace all" action in each source list menu (AEM, DB,
web-crawler, assets), next to Import ZIP in `useSourceExchange`. Picking a `.json` or
`.zip` calls the dry run first and opens a destructive confirmation dialog that says
plainly that every existing source of this type will be overwritten or removed, and
lists what the dry run returned: how many are created, overwritten and removed, with the
removed names spelled out. An invalid name blocks the confirm button. Confirming calls
`mode=replace` and reloads the list; cancelling writes nothing. The plain Import ZIP
keeps adding only. Strings in en and pt.

### §DU13 AEM JPA integration test never runs

`aem/aem-plugin/src/test/.../api/DumAemSourceApiIT.java` is a `@DataJpaTest` that
imports `DumAemSourceApi` and the mapper, but run on its own (`mvn -pl aem/aem-plugin
test -Dtest=DumAemSourceApiIT`) it fails before any test: "Unable to find a
@SpringBootConfiguration by searching packages upwards from the test". The plugin has no
`@SpringBootApplication` in its package tree, and surefire's default includes skip
`*IT`, so nothing ever ran it and nothing reported it.

What it costs: the persistence behaviour the unit tests mock away is unchecked. DU9's
replacing import (delete every source, flush, recreate under the same names in one
transaction) is exactly the kind of change only a real JPA context proves, and it was
gated by Mockito alone.

Build: a minimal `@SpringBootConfiguration` + `@EnableAutoConfiguration` +
`@EntityScan`/`@EnableJpaRepositories` test configuration in the plugin's test tree,
then run the IT in the build (surefire include or failsafe), and add a replace case to
it.
