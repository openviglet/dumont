# Wikipedia Explorer — The Sum of All Human Knowledge, Indexed

> *"Discover the sum of all human knowledge. Search millions of articles across every field of science, history, art, and culture."*

**Wikipedia Explorer** is the flagship showcase for the **Dumont Web Crawler Connector**. It politely crawls Wikipedia starting from the Main Page, extracts article content, filters out the noise (Talk pages, User pages, File namespaces…), and surfaces everything through an elegant encyclopedia-grade search interface.

![Web Crawler](https://img.shields.io/badge/connector-Web%20Crawler-06b6d4?style=flat-square)
![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square)
![Respectful](https://img.shields.io/badge/robots.txt-respected-10b981?style=flat-square)

---

## The idea

The open web is the world's largest, messiest knowledge graph. Most of it lives behind paywalls or deep inside CMSes — but some of the best parts are freely linked HTML pages just waiting to be discovered. The Dumont Web Crawler turns any public site into a searchable corpus, and **Wikipedia Explorer** proves the point by pointing the crawler at the single most impressive example on the internet: Wikipedia itself.

## What you get

- 📚 **Clean encyclopedia UI** — sober typography, smart facets, and just the right amount of color.
- 🔦 **Instant search** with hints like *"Einstein, quantum, rainforest…"* — start typing and the suggestions flow.
- 🗂️ **Faceted browsing** — slice by category, language, topic, or any custom attribute your crawler captures.
- 🧭 **Deep-link navigation** — URL-synchronized state means every search is shareable.
- 📄 **Article detail view** with rendered HTML, metadata and backlinks.
- 🌙 **Dark and light modes**, because deep reading deserves both.

## The crawler behind the curtain

The sample configuration (`scripts/sample/export/wikipedia.json`) is a masterclass in polite crawling:

```json
{
  "startingPoints": ["https://en.wikipedia.org/wiki/Main_Page"],
  "allowUrls":     ["https://en.wikipedia.org/wiki/*"],
  "notAllowUrls":  [
    "https://en.wikipedia.org/wiki/Special:*",
    "https://en.wikipedia.org/wiki/Talk:*",
    "https://en.wikipedia.org/wiki/User:*",
    "https://en.wikipedia.org/wiki/Wikipedia:*",
    "https://en.wikipedia.org/wiki/Template:*",
    "https://en.wikipedia.org/wiki/Category:*",
    "https://en.wikipedia.org/wiki/File:*",
    "https://en.wikipedia.org/wiki/Module:*"
  ],
  "notAllowExtensions": [".pdf", ".zip", ".jpg", ".png", ".mp4", "…"]
}
```

Every rule is there for a reason — it keeps you on article pages, skips metadata namespaces, and avoids downloading binaries you don't need. **Swap the starting point** for any other site (your docs portal, a news site, a competitor catalog) and you have a brand-new indexed corpus.

## Architecture

```
  Any Website  ──►  WC Plugin (Crawler4j-style)  ──►  Dumont Connector  ──►  Turing ES  ──►  Wikipedia Explorer
  (HTTP/HTTPS,                                                                                     │
   robots-aware)                                                                                    ▼
                                                                                           You, falling down
                                                                                           a rabbit hole
                                                                                           of curiosity
```

## Fire it up

1. Download the zip from the **Dumont Marketplace**.
2. Upload it to a Turing SN Site named `wikipedia`.
3. Create a Web Crawler source using the provided `wikipedia.json` and launch the crawl.
4. Pour a coffee — the encyclopedia is vast.

## Features worth calling out

| Feature | How it shows up |
|---------|-----------------|
| URL include/exclude patterns | Keeps the crawler on topic |
| Extension blacklist | Skips PDFs, images, archives |
| Starting-point configuration | Crawl a single section or the whole site |
| Pagination + history | Never lose your place |
| Empty-state messaging | *"No articles match your search. Try different keywords?"* |

## Tech Stack

React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui · Tabler Icons · Turing React SDK

---

**Lost in a reference loop?** Report it at [openviglet/dumont](https://github.com/openviglet/dumont/issues) — we'll help you climb back out.
