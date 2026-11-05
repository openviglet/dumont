---
title: "Catalog & JSON API Connector"
description: "Index an already-normalized catalog feed, or any paged JSON API declared as a manifest: paging, call budgets, field mapping and incremental expiry, with no Java code."
---

The Catalog connector republishes records that already exist as JSON. A source runs in one of two modes:

- **Catalog URL**: one GET of a URL that returns a JSON array of records, or a `{ "courses": [...] }` envelope, with the fields of the built-in course schema.
- **JSON API manifest**: any paged JSON API (a job board, a product catalog, a partner feed). You describe the request, the paging, the call budget and the field mapping in a JSON manifest, and Viglet Dumont runs it. You don't write any Java.

When a source has a manifest, the manifest replaces the catalog URL and the manifest's fields become the schema.

The connector ships as `catalog-plugin.jar` and is bundled in the Docker image.

---

## Creating a source

In the console, open **Catalog Sources** and create a source:

1. **General**: a unique name, the Semantic Navigation site to publish to, and the locale (for example `pt_BR`).
2. **Connection**: either the **Catalog URL**, or the **JSON API** section:
   - **Manifest**: the JSON manifest described below.
   - **Secrets**: a JSON object with the values of the manifest's `{secret:NAME}` placeholders, for example `{"appKey": "..."}`.

Saving checks the manifest. If it does not parse, or it uses a secret that is not set, the save is refused with the reason. Use **Index All** to run the source, or let the scheduler run it.

---

## Manifest reference

```json
{
  "name": "jobs",
  "version": 1,
  "request": {
    "method": "GET",
    "url": "https://api.example.com/v1/jobs/search/{page}?app_id={secret:appId}&app_key={secret:appKey}&results_per_page={size}",
    "headers": { "Accept-Language": "pt-BR" }
  },
  "paging": { "mode": "page", "start": 1, "size": 50, "maxPages": 20, "totalPath": "count" },
  "budget": { "maxCallsPerRun": 20, "callsPerMinute": 25, "callsPerDay": 250, "callsPerMonth": 2500 },
  "incremental": { "expiry": "P7D" },
  "records": "results",
  "id": "id",
  "fields": [
    { "name": "title", "path": "title", "type": "TEXT", "role": "TITLE", "mandatory": true },
    { "name": "description", "path": "description", "type": "TEXT", "role": "BODY" },
    { "name": "company", "path": "company.display_name", "facet": true },
    { "name": "city", "path": "location.area[3]", "facet": true },
    { "name": "salary", "path": "salary_min", "type": "DOUBLE" },
    { "name": "url", "path": "redirect_url", "mandatory": true }
  ]
}
```

### `request`

| Key | Description |
|---|---|
| `method` | `GET` (default) or `POST`. |
| `url` | The URL template. Placeholder values are URL-encoded. |
| `headers` | Header templates, for example `"Authorization": "Bearer {secret:token}"`. |
| `body` | The JSON body of a `POST`. Write it as an object. A numeric placeholder that is a whole string value (`"page": "{page}"`) is sent as a number. |

Placeholders work in the URL, the headers and the body:

| Placeholder | Value |
|---|---|
| `{page}` | The page number, counting up from `paging.start`. |
| `{offset}` | The record offset, counting up from `paging.start` in steps of `paging.size`. |
| `{size}` | `paging.size`. |
| `{cursor}` | The cursor the previous response returned (empty on the first call). |
| `{secret:NAME}` | The value of `NAME` in the source's **Secrets**. |

Keep keys in secrets, not in the manifest. A manifest can then be shared and versioned, and the export (`/api/v2/catalog/source/export`) includes the manifest but never the secrets.

### `paging`

| Key | Description |
|---|---|
| `mode` | `none` (one call, the default), `page`, `offset` or `cursor`. |
| `start` | The first page (default `1`) or offset (default `0`). |
| `size` | Records per call. A response with fewer records than `size` is treated as the last page. Required for `offset`. |
| `maxPages` | The most calls one walk makes. |
| `cursorPath` | For `cursor` mode: where the next cursor sits in a response. |
| `totalPath` | Where the total record count sits in a response. The walk stops once it has that many. |

The walk also stops on an empty page, a blank or repeated cursor, and a page that only holds ids it has already seen.

### `budget`

| Key | Description |
|---|---|
| `maxCallsPerRun` | The most calls one run makes. |
| `callsPerMinute` | The pace. Calls are spaced so no minute holds more than this. |
| `callsPerDay` | The most calls in one UTC day, counted across runs. |
| `callsPerWeek` | The most calls in one week (Monday to Sunday, UTC). |
| `callsPerMonth` | The most calls in one calendar month. |

Daily, weekly and monthly counts are stored in the connector database, so they survive a restart. A failed call still counts, because the provider counted it too.

### `records`, `id` and `fields`

- `records` is the path to the record array in each response (`results`, `data.listVagas`). Use `$` (the default) when the response is the array itself.
- `id` is the path to each record's id, relative to the record. A record without an id is skipped.
- Each field has a `name` (the field in the index) and a `path` (where the value is in the record, defaulting to the name), plus optional `type` (`STRING`, `TEXT`, `INT`, `LONG`, `FLOAT`, `DOUBLE`, `BOOL`, `DATE`, `CURRENCY`), `multiValued`, `facet`, `mandatory` and `role` (`TITLE`, `SUMMARY`, `BODY` for the RAG embedding).

Paths are dotted keys with optional array indexes: `company.display_name`, `locations[0].city`. A key applied to an array reads it from every element, so `skills.name` gives the list of skill names.

The connector never invents values. A record missing a `mandatory` field is not published, and a value that does not fit its type (text in a `DOUBLE` field) is dropped rather than converted.

---

## How a run ends

| The walk… | What happens to records the run did not fetch |
|---|---|
| reached the end of the API | Removed from the index (the normal sweep). |
| stopped at `maxPages`, a call budget, or a failed call | Kept. The run publishes what it fetched and removes nothing. |

A rate limit is not a failed call at first. When the API answers 429 or 503, the walk waits for the `Retry-After` it sent (ten seconds when it sent none, five minutes at most) and asks again, up to three times. Only a limit that outlasts those retries fails the call. The retries are not counted against the call budget.

### Incremental sources

An API with a tight budget may never return everything in one run, so the sweep would never run and stale records would stay in the index. Declare the source incremental:

```json
"incremental": { "expiry": "P7D" }
```

With `incremental` set, a run never sweeps. The connector stores when it last saw each record, and at the end of each run it removes only the records unseen for longer than `expiry` (an ISO-8601 duration: `P7D` is seven days, `PT12H` is twelve hours). A run that failed before it fetched anything removes nothing.

---

## OAI-PMH repositories

A catalog source can harvest any OAI-PMH 2.0 repository instead of a JSON API: DSpace, EPrints, an OJS journal, Tainacan or Koha. In the console, open the source's **Connection** tab and fill the **OAI-PMH repository** card: **Base URL** is the repository's OAI endpoint (for example `https://repo.example.org/server/oai/request`). A source uses either a base URL or a JSON API manifest, never both: the form refuses to save one with both, and so does the API. Through the API or an import file the same settings are `oaiPmhBaseUrl` and the fields below.

| Form field | API field | Default | What it does |
|---|---|---|---|
| Metadata prefix | `oaiPmhMetadataPrefix` | `oai_dc` | The metadata format asked for. Only its Dublin Core (`dc:`) elements are read. |
| Set | `oaiPmhSet` | none | One OAI set (a collection) to harvest instead of the whole repository. |
| Full text from PDFs | `oaiPmhFullText` | off | Also read each record's PDF and add its text to `text`. See below. |

Each run follows `resumptionToken` to the end. The first run harvests everything and sweeps like any source. Later runs ask only for what changed since the last complete harvest (`from`, as a day), and do not sweep. A record the repository marks deleted leaves the index on that run. A repository that keeps no deletion records never reports them, so its removals leave on the next full harvest. Changing the address, the format, the set or the full-text switch starts over with a full harvest.

Dublin Core becomes these fields: `title`, `text` (the descriptions), `abstract` (the first description), `author` (every creator), `subjects`, `publication_date` (when it reads as a date, else `dateText`), `type`, `publisher`, `language`, `relations` and `url`.

`author`, `publication_date` and `abstract` are Turing's own names for the author, the date and the snippet, the ones a WordPress post carries too. An SN site that holds both a WordPress site and a repository therefore has one author facet, one date that result cards, the Newest sort and period facets read, and one snippet. The [WordPress plugin](/dumont/docs/connectors/wordpress/) declares `author` with several values, as the connector does. The `url` is the record's handle or DOI link when it has one, else a web address on the repository's own host, else its first web address. An address a visitor cannot open is never used: one on a private, loopback or link-local IP, or on a name without a dot such as `intranet`, which some repositories leak into their records. Nothing is filled in that the record does not hold. A repository that answers 429 or 503 is retried after its `Retry-After`, up to three times.

### Full text

With **Full text from PDFs** on, the connector looks at each record's `dc:identifier` and `dc:relation` values for web addresses on the repository's own host, the host of the base URL. Addresses ending in `.pdf` are tried first, then the others, at most three per record. The first one that answers with a PDF (served as `application/pdf`, or a generic binary from a `.pdf` address) has its text appended to `text`, after the descriptions. An article or item page is read only for the PDF its `citation_pdf_url` tag names, which journal and repository systems such as OJS and DSpace put on it; that PDF is fetched in its place. No other link on the page is followed.

- **Same host only.** An address on any other host is never fetched, and neither is a redirect that leaves the host. A leading `www.` does not count as another host, so a journal harvested at `www.journal.example` still reads PDFs from `journal.example`.
- **Capped.** A PDF over 50 MiB is not read, and its text is cut at one million characters.
- **Polite.** Downloads are spaced one second apart, and a 429 or 503 is retried after its `Retry-After`, like the harvest.
- **Never fatal.** A PDF that fails or holds no text leaves the record as its metadata describes it.

Full text costs a request per address tried, so a large repository takes longer to harvest. It is off by default.

### DSpace

Most Brazilian university repositories run DSpace. Its `oai_dc` output merges authors with advisors and drops the community and collection, so a DSpace repository is better read with the **DSpace profile**: the same harvest, in DSpace's own `dim` format, with each item completed from the DSpace REST API. Turn on **DSpace profile** in the OAI-PMH card and fill **DSpace REST API URL**. That is the REST base on the repository's host, usually ending in `/server/api`. The connector never guesses it, because an install can serve it under any path, such as `/repository/api`. Through the API or an import file, the same settings are `oaiPmhProfile: "dspace"`, `dspaceRestUrl` and `siteKey`.

| Form field | API field | Default | What it does |
|---|---|---|---|
| DSpace profile | `oaiPmhProfile` | `generic` | `dspace` reads the repository as DSpace. The metadata prefix is then always `dim`. |
| DSpace REST API URL | `dspaceRestUrl` | none, required | The REST base the items, names and files are read from. |
| Site key | `siteKey` | the base URL's host | Stored on every record as `site_key`, to tell this repository apart in a shared index. |
| Full text | `oaiPmhFullText` | **on** | Adds each item's text after the abstract. Only `false` turns it off. |

Each `dim` record becomes these fields:

| Field | From |
|---|---|
| `title` | `dc.title` |
| `author` | `dc.contributor.author`, then `dc.creator` |
| `advisors` | `dc.contributor.advisor` and its numbered forms (`advisor1`, `advisor-co1`) |
| `publication_date` | `dc.date.issued` as a year, month, day or instant; else `dateText` as written |
| `abstract` | the first `dc.description.abstract` (else `dc.description`) |
| `text` | `dc.description.abstract` (else `dc.description`), then the full text |
| `subjects` | `dc.subject`, every qualifier (`cnpq` included) |
| `type`, `publisher`, `language` | `dc.type`, `dc.publisher`, `dc.language.iso` and `dc.language` |
| `url` | the handle link in `dc.identifier.uri`, never one on a private address |
| `relations` | `dc.relation`, every qualifier |
| `community`, `collection` | the names of the record's `com_` and `col_` sets |
| `image` | the first file of the item's `THUMBNAIL` bundle |
| `site_key` | the source's site key |

Nothing is filled in that the record does not hold. Community and collection names are read from the REST API once per run, page by page. A set whose name REST does not give is left out rather than indexed as `com_123456789_7`.

Each item is looked up in REST by its handle. An item that REST reports withdrawn or not discoverable, or that answers 401 or 403 (private), is removed from the index and never indexed. A withdrawn item that the OAI-PMH feed marks deleted leaves the same way. An item REST cannot find is indexed from its metadata alone.

With full text on, the connector reads the item's `TEXT` bundle first, which holds the text DSpace already extracted. When there is no `TEXT` bundle, it reads the item's `ORIGINAL` PDFs, at most three. The limits of the generic full text apply: the REST host only, 50 MiB and one million characters per file, and a 429 or 503 retried after its `Retry-After`. REST calls are spaced half a second apart. A file the public may not read (401, 403, an embargo) adds no text, and the item is still indexed with its metadata. A lookup, its bundles and one text file take about four requests per item, so the first harvest of a few hundred items runs for several minutes.

DSpace's OAI-PMH answers from its own index, which is built by `dspace oai import`. A repository that never ran it answers `noRecordsMatch` to every harvest, even though the site shows items. The connector reports that as an empty, complete harvest, so ask the repository's administrators to run the import.

---

## REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v2/catalog/source` | List sources |
| `POST` | `/api/v2/catalog/source` | Create a source (`400` with the reason if the manifest cannot run) |
| `PUT` | `/api/v2/catalog/source/{id}` | Update a source |
| `DELETE` | `/api/v2/catalog/source/{id}` | Delete a source |
| `GET` | `/api/v2/catalog/source/{id}/indexAll` | Index the source now |
| `GET` | `/api/v2/catalog/source/export` | Export every source as JSON (without secrets) |
| `POST` | `/api/v2/catalog/source/import` | Import sources from a JSON export (multipart `file`) |

An import file can carry `jsonApiManifest` (an object) and `jsonApiSecrets` (an object) for each source, so a first-boot provisioning file can bring its keys.
