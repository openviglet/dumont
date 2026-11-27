# Improvements

## Block A — Source configuration moves between instances

### §DU14 Import that merges by name

The source imports have two modes since DU9: `add` skips every source whose id or name
exists, and `replace` makes the file the whole set, deleting what it does not name. The
common case sits between them: bring sources from another environment and update the
ones that already exist here, leaving the rest alone. Today that takes either a replace,
which deletes unrelated sources, or deleting the stale ones by hand before an add.

Build: a third mode, `merge`, on the AEM, DB, web-crawler and assets imports. A source
whose name matches one in the file is replaced by the file's version (deleted and
recreated under the same name, as DU9 does; history is keyed by name); a new name is
created; an existing source the file does not name is kept. Same transaction and refusal
rules as `replace`: invalid names refuse the whole file before anything is written.
`dryRun=true` answers for `merge` too, with `remove` always empty, reusing
`DumSourceReplacePlan` so the preview and the write match by the same code.

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
