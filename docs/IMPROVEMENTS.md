# Improvements

## Block A — Source configuration moves between instances

### §DU15 Imports detect the file's shape

The server imports read only the `{ "sources": [...] }` shape of `export.json`. The JSON
a source page's Export writes for a single source (`exportSourceToJson` in
`source-import-export.ts`) is accepted by the console's list-page Import JSON, which
parses it in the browser, but sent to `POST .../import` it maps to no sources and
nothing is imported, with no error.

Build: each exchange process detects the upload's shape before importing: a zip (by its
content, not only the extension), an `export.json` with `sources`, or a single source
object, which is wrapped as a one-source exchange. A JSON that is none of these refuses
with 400 and says which shapes are accepted, in every mode including the dry run.

### §DU16 One import that warns before replacing

The source lists offer three overlapping imports: Import JSON (AEM list only, parsed in
the browser, creates only the file's first source and duplicates an existing name),
Import ZIP or JSON (adds, silently skipping what exists) and Import and replace all
(DU12). An operator has to know which one fits before picking a file.

Build: one Import action per list (AEM, DB, web-crawler, assets) through
`useSourceExchange`. Picking a zip or JSON runs the `merge` dry run. With nothing to
overwrite it imports at once and reports how many were created. Otherwise a dialog lists
the sources to create and, highlighted by name, the ones that will be replaced, and asks
to confirm. An unchecked option "Also delete the sources not in the file (N)" switches
the confirm to `replace` and lists those names in red. Invalid names block the confirm.
Import JSON and Import and replace all leave the menus; the source page's own
Import/Export JSON stays. Strings in en and pt.
