# Improvements

## Block A — Source configuration moves between instances

### §DU9 Import that replaces every source

Every source import (`POST /api/v2/{aem,db,wc,assets}/source/import`, the console's
Import ZIP from DU4, and the first-start import) only adds: a source whose id or name
already exists is skipped (`isNewSource` in `DumAemExchangeProcess`, the `findById`
filters in the DB, web-crawler and assets exchange processes). Nothing can make an
instance's sources match a file.

Build: a `mode` parameter on each import, `add` (today's behaviour, the default) and
`replace`. In `replace` the file is the whole set for that connector type: a source
whose name matches one in the file is overwritten in place, keeping its id so indexing
state and monitoring history stay attached; a source in the file with no match is
created; an existing source the file does not name is deleted. One transaction per
connector type, so a file that fails halfway changes nothing. The DU6 name rule still
applies: a file holding an invalid name is refused whole in `replace`, never
half-applied. The first-start import keeps `add`.

### §DU10 Dry run of a replacing import

DU9's `replace` deletes every source the file does not name and overwrites the rest, so
the console has to say what a file will do before it does it, and today no endpoint can
answer that without writing.

Build: `POST .../import?mode=replace&dryRun=true` on each source API reads the file and
answers `{ "create": [names], "overwrite": [names], "remove": [names], "invalid":
[names] }` without touching the repository. It shares the matching code with the real
import, so the preview and the write cannot disagree about which source is which. A file
it cannot read answers 400 with the reason.

### §DU11 Plain JSON for every source import

DU2 made the AEM import read a plain `.json` (`importFromJsonUpload` in
`DumAemExchangeProcess`): the per-source file the console exports, or an `export.json`
taken out of its zip. The DB, web-crawler and assets `importFromMultipartFile` still
unzip whatever arrives, so a `.json` upload fails, and the console's Import ZIP
(`source-exchange.tsx`) only accepts `.zip`.

Build: the same `.json` branch in the three other exchange processes, and the console's
file input accepting `.zip,.json`. Same `{ "sources": [...] }` shape as `export.json`.

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
