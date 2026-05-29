# WKND EDS — Adventure, Indexed

> *"Surfing, skiing, climbing, trekking — find your next escape."*

**WKND EDS** is the Turing Marketplace sample for the **Dumont EDS Connector**. It takes
Adobe's **WKND** reference site as served by **Edge Delivery Services**
([`main--wknd--hlxsites.aem.live`](https://main--wknd--hlxsites.aem.live/)) — no AEM author
required — and turns it into a fully searchable adventure magazine powered by Viglet Turing ES.

![EDS Connector](https://img.shields.io/badge/connector-EDS-0f766e?style=flat-square)
![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square)
![Turing SDK](https://img.shields.io/badge/Turing_SDK-2026.2-fbbf24?style=flat-square)

---

## What's inside the zip?

A production-ready **single-page search experience** you can drop in front of any Edge
Delivery / `aem.live` site. It ships with:

- 🏔️ **Magazine-style discovery grid** — each page becomes a hero card tinted by its
  `section` facet (cyan for adventures, violet for magazine, amber for authors…), using the
  real page image from the EDS `query-index.json`.
- 🔍 **Instant search with autocomplete** — typeahead, recent-search history and faceted
  refinement via the Turing React SDK.
- 🎨 **Dark/light mode** with a teal–yellow gradient palette nodding to WKND's outdoor DNA.
- 📱 **Fully responsive**, with pagination, filtering and detail views — zero config.

## How does it plug in?

```
  main--wknd--hlxsites.aem.live  (Edge Delivery Services)
           │  query-index.json + HTML
           ▼
  DumEDSPlugin  ──►  Dumont Connector  ──►  Turing ES  ──►  WKND EDS SPA (this zip)
  (discover → fetch →                                              │
   extract → enrich → index)                                       ▼
                                                          You, discovering
                                                          your next adventure
```

The Java-side plugin (in `eds/eds-plugin-sample/`) reads the site's `query-index.json`,
fetches each page, extracts `og:*` metadata + body text, derives a `section` facet from the
URL path, and pushes everything to the Turing SN site `wknd-eds`. This SPA renders the results.

## Get going

1. Import `wknd-eds.zip` via the **Dumont / Turing Marketplace** (it carries the SN site
   config `export.json` + a content snapshot, and this SPA under `app/`).
2. The import creates the SN site **`wknd-eds`** (override with `VITE_SN_SITE`).
3. Point an EDS source at WKND using the sample `scripts/wknd/export/wknd.json`, then hit
   *Index* to (re)crawl live — see the main [sample README](../../README.md).

## Field mapping (EDS → SPA)

| EDS field | Used for |
|-----------|----------|
| `title` / `description` | Card + detail text |
| `image` | Card / hero image (relative path resolved via `VITE_EDS_URL`) |
| `section` | Tag chip + color, and the search facet |
| `url` / `path` | Detail metadata + "open on live site" link |
| `modificationDate` | Detail date |
| `source_apps` | Detail "Source App" badge (`EDS`) |

## Local development

```bash
npm install
npm run dev          # Vite dev server, talks to a Turing at VITE_API_URL (/api)
npm run package      # tsc + vite build, then zip dist + ../export/*.json → wknd-eds.zip
```

`.env` controls the target: `VITE_SN_SITE=wknd-eds`, `VITE_LOCALE=en_US`,
`VITE_EDS_URL=https://main--wknd--hlxsites.aem.live` (image origin).

## Tech Stack

React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui · Tabler Icons · React Router 7 · Turing React SDK

---

**Trail closed?** Open an issue at [openviglet/dumont](https://github.com/openviglet/dumont/issues).
