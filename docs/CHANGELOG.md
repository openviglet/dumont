# Shipped Ledger

## Block A — Source configuration moves between instances

- ✅ **DU1** **An AEM source JSON exported from the console has no id, so the first-start import of export/*.json fails on it** — The first-start import now accepts a source with no id, minting one and skipping a name that exists, and the console export writes the id so a re-import is idempotent.
- ✅ **DU2** **AEM sources cannot be exported or imported as a set through the API, unlike DB and web-crawler sources** — GET /api/v2/aem/source/export returns every AEM source as a zip holding export.json, and POST /import takes that zip or a plain .json, like DB and web-crawler.
- ✅ **DU3** **The console accepts a source name containing spaces, and that source then fails to index** — The AEM source form, the AEM wizard and the DB and assets source forms refuse a name with whitespace before saving, with a message saying the name is part of the URLs.
- ✅ **DU4** **The source lists offer no export-all or import action, so the export endpoints are reachable only by API calls** — The AEM, DB, web-crawler and assets source lists offer Export all (ZIP) and Import ZIP, the import also on the empty list, calling each plugin's export and import endpoints.
- ✅ **DU5** **npm run lint in dumont-react exits before checking a file, because the tree has no ESLint flat config** — npm run lint is a working gate: the flat config is in, and no-explicit-any, purity and refs hold everywhere; the two react-hooks lists left are DU8.
