# WKND — Adventure, Indexed

> *"Surfing, skiing, climbing, trekking — find your next escape."*

**WKND** is the flagship marketplace sample for the **Dumont AEM Connector**. It takes Adobe's beloved *WKND* reference site — the one full of lifestyle articles about surfing at sunrise, climbing crags in the Alps, and skating down California backstreets — and turns it into a fully searchable adventure magazine powered by Viglet Turing ES.

![AEM Connector](https://img.shields.io/badge/connector-AEM-0f766e?style=flat-square)
![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square)
![Turing SDK](https://img.shields.io/badge/Turing_SDK-2026.2-fbbf24?style=flat-square)

---

## What's inside the zip?

A production-ready **single-page search experience** that you can drop in front of any AEM content repository. It ships with:

- 🏔️ **Magazine-style discovery grid** — each article becomes a hero card tinted by its dominant adventure tag (cyan for surfing, orange for climbing, emerald for trekking…).
- 🔍 **Instant search with autocomplete** — typeahead suggestions, recent-search history and faceted refinement powered by the Turing React SDK.
- 🎨 **Dark/light mode** baked in, with a teal–yellow gradient palette that nods to WKND's outdoor DNA.
- 📱 **Fully responsive** — from a phone screen on the trail to a 4K monitor in the office.
- 🧭 **Pagination, filtering, and detail views** with zero configuration.

## Why WKND?

Because indexing real content is more fun than indexing Lorem Ipsum. The WKND sample ships with authentic AEM Content Fragments — surf reports, ski-resort reviews, road-trip diaries — giving you a realistic canvas to test:

- Delta indexing of incremental content updates
- AEM Content Fragment flattening and JSON model traversal
- Multi-locale path routing
- Author vs. Publish tier selection

## How does it plug in?

```
  Adobe Experience Manager
           │
           ▼
  DumAemExtSample*  ──►  Dumont Connector  ──►  Turing ES  ──►  WKND SPA (this zip)
  (delta + content                                                     │
   extensions)                                                          ▼
                                                              You, discovering
                                                              your next adventure
```

The Java-side plugin (in `aem/aem-plugin-sample/`) crawls your AEM instance, flattens Content Fragments with the custom `DumAemExtSampleModelJson` extractor, pushes everything to Turing, and this SPA renders the results.

## Get going

1. Download the zip from the **Dumont Marketplace**.
2. Upload it to a Turing SN Site named `wknd-publish` (or configure your own site name via `VITE_TURING_SITE`).
3. Point an AEM source at your WKND instance using the sample `wknd.json` configuration.
4. Hit *Index All* and watch the adventures roll in.

## Tech Stack

React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui · Tabler Icons · React Router 7 · Turing React SDK

---

**Trail closed?** Open an issue at [openviglet/dumont](https://github.com/openviglet/dumont/issues) and we'll re-open the route.
