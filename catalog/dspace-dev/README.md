# Local DSpace for the catalog connector

A DSpace 8 backend in Docker, seeded with a small fictional repository, to try the catalog
connector's OAI-PMH **DSpace profile** (roadmap D314) end to end: harvest, REST completion, full
text, and the items it must drop.

Only the backend runs. There is no Angular UI.

| What | Where |
|---|---|
| REST API | http://localhost:18080/server/api |
| OAI-PMH | http://localhost:18080/server/oai/request |
| Administrator | `admin@lumen.test` / `Lumen-dev-2026` |

Postgres and Solr are not published to the host. Port 18080 keeps clear of Viglet Turing (2700),
the WordPress stacks (8080, 8091, 38082-38086) and Dumont (30130).

## Files

| File | What it is |
|---|---|
| `docker-compose.yml` | DSpace's own compose (tag `dspace-8.5`), on the release images, with no config mount |
| `seed.sh` | Seeds a fresh stack through the DSpace CLI and REST API |
| `seed/` | The seed: `structure.xml`, one Simple Archive Format package per item, `roles.txt` |
| `build_seed.py` | Writes `seed/`. Its output is committed, so run it only to change the seed |
| `e2e.py` | Runs the connector against this DSpace into a local Viglet Turing and checks the result |

## The seed

Every name is invented. The repository is "Lumen University Repository", the handle prefix is
DSpace's placeholder `123456789`, and handle links point at `repository.lumen.test`, a reserved
`.test` host.

- One community, **Lumen University Research**, with two collections: **Theses and
  Dissertations** and **Journal Articles**.
- Nine items with `dc.title`, `dc.contributor.author` (in "Surname, Name" form),
  `dc.contributor.advisor`, `dc.date.issued` (a day, a month or a year), `dc.description.abstract`,
  `dc.subject`, `dc.type` (Thesis, Article), `dc.language.iso` (one item in Portuguese) and
  `dc.publisher`.
- Four theses carry a generated one-page PDF. Each PDF holds a sentence built around a made-up
  word that no metadata contains, so a search hit on it can only come from the full text:
  `quillbramble` (item-01), `glimmerfen` (item-03), `vorlantim` (item-05), `strobilite` (item-07).
  `dspace filter-media` extracts the text into a `TEXT` bundle and draws a `THUMBNAIL`.
- Three items must never be indexed:

  | Item | Done how | What OAI-PMH says |
  |---|---|---|
  | item-07 "Glacier retreat signals..." | withdrawn before `oai import` | `status="deleted"` |
  | item-08 "Pollinator corridors..." | made private (not discoverable) before `oai import` | `status="deleted"` |
  | item-09 "Tide pool temperature..." | made private after `oai import` | a live record: the OAI index is stale |

  item-09 is the one only the connector's REST check can catch. A repository whose OAI index lags
  behind its edits looks exactly like this.

## Start and seed

From this folder, in Git Bash (or any bash with curl and docker):

```bash
docker compose up -d
./seed.sh
```

The first start pulls about 1.5 GB of images, and the backend takes a minute or two to answer.
`seed.sh` waits for it, then runs `create-administrator`, `structure-builder`, `import` (once per
collection), `filter-media`, the REST changes from `seed/roles.txt`, and `oai import -c`. It
seeds a fresh stack once, and on a seeded one it says so and stops. To start over:

```bash
docker compose down -v && docker compose up -d && ./seed.sh
```

## Run the end-to-end check

It goes through the real connector: the Dumont connector app with the catalog plugin on its
`loader.path`, a catalog source created over `/api/v2/catalog/source`, and its `indexAll`.

1. Viglet Turing on http://localhost:2700. The one in `wordpress/docker-compose.yml` will do
   (admin password `dumont-dev`). Mint an API key:

   ```bash
   python e2e.py turing-setup > turing.key
   ```

   The SN site needs no setup. The connector's first run creates `LumenRepository` on Turing's
   first enabled search engine instance, and recreates the fields a new site's template holds
   with another cardinality: `author` and `type` are single-valued there, and the DSpace schema
   declares them multi-valued facets.

2. Build and start the connector with the catalog plugin, from the repository root:

   ```bash
   mvn -q -pl connector/connector-app,catalog/catalog-plugin -am install -DskipTests \
     -Dskip.installnodepnpm -Dskip.pnpm -Dmaven.gitcommitid.skip=true
   java -Dloader.path=catalog/catalog-plugin/target/catalog-plugin.jar \
     -Dturing.url=http://localhost:2700 -Dturing.apiKey=$(cat catalog/dspace-dev/turing.key) \
     -jar connector/connector-app/target/dumont-connector.jar
   ```

   Run it from a scratch directory if you would rather not have its `store/` folder (H2 database,
   queue, logs) in the repository root. Put that directory on a drive under 90% full: above
   that, the embedded Artemis queue blocks every send, and a run stops silently after
   "Draining N jobs from buffer".

3. Run the check:

   ```bash
   python e2e.py run --key "$(cat turing.key)"
   ```

   It creates the source `lumen-dspace` (DSpace profile, full text on, site key `lumen`) bound to
   the SN site `LumenRepository` and starts its `indexAll`. On a rerun it updates the source and
   calls `/api/v2/connector/reindex/lumen-dspace/all` instead, because `indexAll` skips items the
   connector's ledger already holds as sent. Then it waits for the site to settle and checks:

   - only the six live items are indexed;
   - the thesis is found by a title word (`mycorrhizal`) and by its PDF-only word (`quillbramble`);
   - the `author` facet groups "Silva, Ana" (two items) and filtering on it returns the thesis;
   - `glacier`, `pollinator` and `basalt` find nothing (the three dropped items);
   - `community` and `collection` hold names, not set specs.

   It exits 0 only when every check passes. `turing.key` is git-ignored here.

## Stop

```bash
docker compose stop            # keep the data
docker compose down -v         # remove the containers and the volumes
```
