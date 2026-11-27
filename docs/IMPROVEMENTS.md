# Improvements

## Block A — Source configuration moves between instances

### §DU17 Missing collections in an imported source read as empty

`DumAemSourceExchange` declares `attributes`, `models` and `localePaths` with
`@Builder.Default`, but Jackson builds it through the `@NoArgsConstructor`, which leaves
those fields null. `setSource` then calls `getLocalePaths().forEach(...)`, and
`setFacetNames`/`setModels` do the same for the other two, so a source object that
leaves any of them out (a hand-written file, or a single source accepted since DU15)
fails the whole import with a NullPointerException instead of creating the source. Found
while testing DU15 with `{ "name": "wknd", "endpoint": "..." }`. The zip export and the
source page's Export always write the three arrays, so they are not affected.

Build: treat a missing collection as empty. Either initialise the three fields in the
no-args path (field initialisers Jackson keeps, or null-safe getters) or iterate through
a null-safe helper in `DumAemExchangeProcess`. Check the nested exchange beans
(`DumAemModelExchange.targetAttrs`, `DumAemTargetAttrExchange.sourceAttrs`) and the DB,
web-crawler and assets exchange beans for the same pattern (`DumWCSourceExchange`
starting points, allow and deny lists, attributes). A test imports a source object
holding only a name and an endpoint and gets one saved source.
