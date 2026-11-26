# Improvements

## Block A — Source configuration moves between instances

### §DU1 Console export carries no id

The source page's Export action builds `{ "sources": [ ... ] }` in the browser
(`source-import-export.ts`, `exportSourceToJson`) and leaves `id` out. The first-start
import (`DumAemOnStartupJpa` reading `export/*.json`) calls
`DumAemExchangeProcess.importFromFile`, which filters sources with
`dumAemSourceRepository.findById(exchange.getId())`. A null id makes Spring Data throw
`IllegalArgumentException`; `importFromFile` only catches Jackson exceptions, so the
error reaches the `ApplicationRunner`.

Expected: the import accepts a source without an id (it mints one, and skips a source
whose name already exists), and the console export also writes the id so a re-import on
the same instance is idempotent.

Same change as Dumont 2026.3 D359. Workaround until then: add an `"id"` to every entry
of `sources` by hand.

### §DU2 No AEM export or import endpoint

`DumDbSourceApi` and `DumWCSourceApi` expose `GET .../export` (a zip holding
`export.json`) and `POST .../import` (multipart). The AEM plugin has the same
`exportObject` and `importFromMultipartFile` methods in `DumAemExchangeProcess`, but
`DumAemSourceApi` maps neither.

Expected: `GET /api/v2/aem/source/export` and `POST /api/v2/aem/source/import`, same
shape as DB and WC; the import also accepts a plain `.json`. The exported `export.json`
is the file the first-start import reads, so it can be dropped into `export/` on a new
instance unchanged. Same change as Dumont 2026.3 D360.

### §DU3 Source names accept whitespace

The AEM source form, the AEM wizard, the DB source form and the assets source form only
check that the name is present. The name is then used as the path segment of
`/api/v2/connector/index/{name}`, `reindex/{name}`, `audit/{source}` and the monitoring
endpoints, and a name with a space fails there. Web-crawler sources are keyed by id;
their title is display-only.

Expected: every form that sets a source name refuses whitespace with a message that says
why, before the source is saved. A backend check on save is the follow-up that also
covers the API and the first-start import. Same change as Dumont 2026.3 D361.

### §DU4 No export-all or import in the source lists

The AEM, DB and web-crawler source lists show the sources and a New action (the AEM list
also imports one source from a JSON file). The DB and WC export/import endpoints exist,
and DU2 adds the AEM pair, but nothing in the console calls them: the only console
export is the per-source JSON on the AEM source page.

Expected: each list gets an "Export all (ZIP)" action that downloads the zip from its
`/export` endpoint and an "Import ZIP" action, also on the empty list, that uploads a
zip to its `/import` endpoint and then reloads the list. Same change as Dumont 2026.3
D362.
