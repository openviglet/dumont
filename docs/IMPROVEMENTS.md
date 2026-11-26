# Improvements

## Block A — Source configuration moves between instances

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

### §DU5 Frontend lint has no config

`dumont-react/package.json` declares `"lint": "eslint ."` and depends on ESLint 10 with
`eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`, but the 2026.2 tree has
no `eslint.config.js`. ESLint 9+ reads only the flat config, so `npm run lint` exits 2
with "couldn't find an eslint.config file" before checking a single file. Found while
gating DU1: the frontend gate is `tsc -b` alone.

Dumont 2026.3 has `dumont-react/eslint.config.js`. Port it, run `npm run lint`, and fix
or explicitly disable what it reports in the 2026.2 sources, so the gate is green and
stays one.
