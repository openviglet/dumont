# Improvements

## Block A — Source configuration moves between instances

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

### §DU6 Source names unchecked on the server

DU3 made the four console forms that set a source name (AEM source, AEM wizard, DB,
assets) refuse whitespace through `sourceNameHasNoWhitespace` in
`dumont-react/src/lib/source-name.ts`. Nothing on the server checks it: `POST
/api/v2/aem/source`, `/api/v2/db/source`, `/api/v2/assets/source`, the AEM `POST
/import` added by DU2 and the first-start import of `export/*.json` all save a name with
a space, and that source then fails on `/api/v2/connector/index/{name}` and the other
name-keyed URLs.

Build: one check in connector-commons, called on create and update by each source API
and by the import paths. The API answers 400 with a message naming the rule; an import
logs and skips the source rather than failing the whole file. Web-crawler sources are
keyed by id and stay out of scope.
