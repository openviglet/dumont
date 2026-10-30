import type { LucideIcon } from "lucide-react";
import { LINKS, sitePath } from "./site-links";
import {
  Activity,
  BookOpen,
  Boxes,
  Braces,
  Database,
  FileSearch,
  FileStack,
  FolderTree,
  Gauge,
  GitCompareArrows,
  Globe,
  History,
  Layers,
  ListChecks,
  Plug,
  Radar,
  ScrollText,
  ShieldCheck,
  Sparkles,
  Trash2,
  Webhook,
} from "lucide-react";

/**
 * Every claim on this site is something the connector does today, in the
 * 2026.3 line, and each one names the place it can be checked. The rule is the
 * one shio.viglet.org is held to: a sentence the code cannot back is not copy,
 * it is a bug report waiting to be filed by a reader.
 */

export { LINKS };

export const DOCKER_RUN = "docker run -p 30130:30130 ghcr.io/openviglet/dumont-ce:latest";

/* ── Sources and targets ─────────────────────────────────────────────── */

export interface Connector {
  id: string;
  icon: LucideIcon;
  name: string;
  short: string;
  body: string;
  points: string[];
}

export const CONNECTORS: Connector[] = [
  {
    id: "aem",
    icon: Layers,
    name: "Adobe Experience Manager",
    short: "On-prem, AMS and AEM as a Cloud Service",
    body:
      "Pages, Content Fragments and assets, each read the way its content kind wants. Extraction is declared per content type, so one recipe serves every site that shares the template.",
    points: [
      "Author servlets on-prem, delivery APIs on Cloud Service with no author credential",
      "A changed Experience or Content Fragment reindexes every page that embeds it",
      "Changes arrive by Adobe I/O Events, an AEM-side bundle, or a journal poller",
    ],
  },
  {
    id: "eds",
    icon: Globe,
    name: "Edge Delivery Services",
    short: "aem.live sites, from their query index",
    body:
      "Reads the site's query-index.json (and its sitemap, if you want it), takes meta tags and body text by CSS selector, and derives facets from the URL path.",
    points: [
      "Checksum-based incremental runs",
      "Index, reindex and dry scan from the console",
      "A WKND EDS sample in the marketplace",
    ],
  },
  {
    id: "database",
    icon: Database,
    name: "Databases (JDBC)",
    short: "A query, a key, and a typed field map",
    body:
      "One SQL query and its primary key become documents. Each column maps to a field with a type, and can be multi-valued, a facet, mandatory or an embedding input.",
    points: [
      "Reads file paths or file contents from columns",
      "Strips HTML from the fields you name",
      "A custom class when a mapping is not enough",
    ],
  },
  {
    id: "web-crawler",
    icon: Radar,
    name: "Web crawler",
    short: "Sites you do not own the CMS of",
    body:
      "Start points, allow and deny lists, and CSS selectors per attribute. It reads robots.txt and sitemaps, honours Crawl-delay, and backs off when a host answers 429 or 403.",
    points: [
      "Parallel workers with a per-host rate limit",
      "Re-crawls with ETag and Last-Modified, skipping what did not change",
      "A crawl that finds nothing does not empty the index",
    ],
  },
  {
    id: "files",
    icon: FolderTree,
    name: "Files and documents",
    short: "A directory, read with Apache Tika",
    body:
      "Walks a directory and extracts the text of each document with Apache Tika, so PDFs, Office files and the rest become searchable alongside the pages that link to them.",
    points: [
      "Title, URL, date, size, extension and content per file",
      "Images keep their file URL as the image field",
      "The Celestial Gallery sample indexes a folder of astrophotography",
    ],
  },
  {
    id: "catalog",
    icon: Braces,
    name: "Structured catalogs and JSON APIs",
    short: "Paged JSON, with request budgets",
    body:
      "Ingests catalog JSON that is already normalised, or pages through a JSON API under a daily, weekly or monthly request budget, so a rate-limited source is never overrun.",
    points: [
      "Declared as data: discover, extract, map",
      "A parity gate before a source moves to a native recipe",
      "Built for catalogs the generic crawler reads badly",
    ],
  },
];

export interface Target {
  name: string;
  property: string;
  note: string;
}

export const TARGETS: Target[] = [
  {
    name: "Viglet Turing ES",
    property: "turing",
    note: "The default. Dumont sends the field schema before the first document and audits what Turing holds.",
  },
  {
    name: "Apache Solr",
    property: "solr",
    note: "Writes straight to a Solr collection.",
  },
  {
    name: "Elasticsearch",
    property: "elasticsearch",
    note: "Writes straight to an index, with optional basic auth.",
  },
];

/* ── What goes wrong with an index, and what Dumont does about it ───── */

export interface Problem {
  icon: LucideIcon;
  failure: string;
  answer: string;
}

export const PROBLEMS: Problem[] = [
  {
    icon: Trash2,
    failure: "A run dies halfway, and the cleanup step deletes everything it did not reach.",
    answer:
      "A run that was interrupted, partial, or failed on half its items or more removes nothing. Only a run that finished well may take documents out.",
  },
  {
    icon: History,
    failure: "A page was unpublished months ago and still turns up in search.",
    answer:
      "The nightly audit compares the source with the index. Leftovers go into a dated report you apply or discard, and the report expires in a day.",
  },
  {
    icon: GitCompareArrows,
    failure: "A template change quietly empties a field on four thousand pages.",
    answer:
      "Field coverage is tracked per source and per run, and drift between runs is reported, so a field that stops arriving shows up as a number.",
  },
  {
    icon: Gauge,
    failure: "Every run re-sends the whole site, and the search cluster pays for it.",
    answer:
      "A SHA-256 checksum per item skips what did not change. Batches the target rejected are resent on the next run instead of being forgotten.",
  },
];

/* ── Trust: the operational half ─────────────────────────────────────── */

export interface TrustFeature {
  id: string;
  icon: LucideIcon;
  title: string;
  body: string;
  detail: string;
}

export const TRUST: TrustFeature[] = [
  {
    id: "safe-finish",
    icon: ShieldCheck,
    title: "A failed run cannot empty the index",
    body:
      "De-indexing is the last step of a run and it is conditional. An interrupted run, a partial one, or one where at least half the items failed keeps every document where it was.",
    detail: "The run is recorded with its status and notes, and the next run picks up from there.",
  },
  {
    id: "audit",
    icon: ListChecks,
    title: "A nightly audit, against the index itself",
    body:
      "On a cron you choose (03:00 by default) Dumont lists what the target holds and compares it with the source. Missing or stale documents are queued again.",
    detail: "Index inventory is read from Turing; on Solr and Elasticsearch the audit is not available yet.",
  },
  {
    id: "orphans",
    icon: Trash2,
    title: "Deletions are a report, not a side effect",
    body:
      "Documents that left the source but are still indexed go into a dated orphan report. A person applies it or discards it; an untouched report expires after 24 hours.",
    detail: "Nothing is removed from the index because a crawler did not happen to see it today.",
  },
  {
    id: "coverage",
    icon: Activity,
    title: "Coverage and drift, per field",
    body:
      "For each source, the share of documents that carry each field, and how that changed since the last run. A field that stops arriving is a number on a screen, not a support ticket.",
    detail: "An optional summary of the coverage report is written by the LLM configured in Turing.",
  },
  {
    id: "provenance",
    icon: FileSearch,
    title: "Field-level provenance",
    body:
      "Each document can carry where each of its fields came from, so a wrong value in search leads back to the extractor and the source item that produced it.",
    detail: "Plus a validation that compares what Dumont recorded as sent with what the index actually holds.",
  },
  {
    id: "metrics",
    icon: Gauge,
    title: "Prometheus, and a webhook when it goes wrong",
    body:
      "Run outcomes are exposed as dumont_run_* metrics on a separate management port, never the public one. A run that starts warning posts JSON to the webhook you configure.",
    detail: "Last status, finish time, duration and document count per source, plus a run counter.",
  },
];

/* ── AEM depth ───────────────────────────────────────────────────────── */

export const AEM_POINTS: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: Layers,
    title: "Each content kind, read its own way",
    body:
      "Pages through model.json, Content Fragments through the Content Fragment API, assets through Assets Search. A source decides per kind, so nothing is scraped that has an API.",
  },
  {
    icon: Plug,
    title: "AEM as a Cloud Service without an author login",
    body:
      "A source can read from the delivery side instead of the author servlets. On-prem and AMS keep infinity.json and QueryBuilder; Cloud Service needs no author credential at all.",
  },
  {
    icon: Boxes,
    title: "Extraction declared per content type",
    body:
      "Composable extractors and versioned recipes, chosen by content type and shared across sites. A new site on the same templates is configuration, not a new connector.",
  },
  {
    icon: GitCompareArrows,
    title: "Fragments propagate",
    body:
      "When a shared Experience Fragment or Content Fragment changes, every page that embeds it is reindexed. The footer edit reaches search the same day it reaches the site.",
  },
  {
    icon: Webhook,
    title: "Three ways to hear about a change",
    body:
      "An Adobe I/O Events webhook, an OSGi bundle on the AEM side that sends replication events, and a journal poller that recovers events the other two dropped.",
  },
  {
    icon: Sparkles,
    title: "A wizard for the first source",
    body:
      "Test the connection, browse the content tree and pick extractors in the console before the first run, instead of writing a configuration file and hoping.",
  },
];

/* ── WordPress and WooCommerce ───────────────────────────────────────── */

/**
 * The /wordpress page, told as the demo store's story (wordpress/demo/story.md): each
 * chapter is one step `npm run e2e:story` plays live against Turing, and its picture is a
 * still from that recording (wordpress/bin/story-video.mjs copies them into
 * public/wordpress/). A capability the story does not play is not a chapter here.
 */
export interface StoryChapter {
  title: string;
  feature: string;
  body: string;
  alt: string;
}

export const WORDPRESS_STORY: StoryChapter[] = [
  {
    title: "Arrives",
    feature: "Popular searches",
    body:
      "Before she types, the week's searches that found something sit under the box as chips, so the catalog shows at a glance. A search that found nothing never becomes a chip.",
    alt: "The demo store's search page with popular-search chips under the box.",
  },
  {
    title: "Says what she needs",
    feature: "A sentence becomes filters",
    body:
      "“light rain jacket under $400” sets the price filter, says back Understood: Price ≤ 400, and keeps the words that are left in the box. A misspelt name is offered its correction.",
    alt: "Search results for a sentence, with the price filter it set shown as understood.",
  },
  {
    title: "Asks",
    feature: "Answers with their sources",
    body:
      "The Site assistant answers from the shop's own guides and products and lists what it read. Asked what size of backpack a night in a hut needs, it cites the backpack guide.",
    alt: "The Site assistant's answer to a question, with the guide it cites below it.",
  },
  {
    title: "Compares",
    feature: "Side by side, with a summary",
    body:
      "Compare, ticked on the search results, opens the products together: the rows that differ are marked, and a summary written by AI from the table sits above it, labelled as such.",
    alt: "Three jackets compared in a table, with an AI summary of the table above it.",
  },
  {
    title: "Builds the kit",
    feature: "A plan becomes a kit",
    body:
      "She describes the trip, and the catalog copilot stages products by need, each priced and stocked by WooCommerce, with the journal guides that cover the plan. One click puts the kit in the cart.",
    alt: "A kit staged by need for a two-day trip, each product with its price.",
  },
  {
    title: "Checks one item",
    feature: "Related, by meaning",
    body:
      "On a product's page, Related shows the products and guides most like it, and the copilot under the product answers her question with the products it cites.",
    alt: "A product page with Related products and guides and the catalog copilot.",
  },
  {
    title: "Buys",
    feature: "WooCommerce's own checkout",
    body:
      "The cart and checkout are the store's own: prices and the total come from WooCommerce, never from the model. The demo pays with an offline method, so an order is placed and nothing is charged.",
    alt: "The WooCommerce checkout with the kit in the order summary.",
  },
  {
    title: "Backstage",
    feature: "What the owner sees",
    body:
      "WordPress's own search finds nothing for “raincoat”; Turing finds the rain jackets through a synonym. Search insights list what was searched and what found nothing, and one click turns a miss into a synonym.",
    alt: "Search tuning in wp-admin with the synonym just added from Search insights.",
  },
];

/* ── AI, carefully ───────────────────────────────────────────────────── */

export const AI_POINTS: { title: string; body: string }[] = [
  {
    title: "It drafts, a person confirms",
    body:
      "Point source inference at a URL and an LLM drafts the source and its fields. A dry run shows coverage and a diff, and nothing is indexed until someone accepts the draft.",
  },
  {
    title: "No model inside Dumont",
    body:
      "Every AI feature calls the LLM you configured in Turing, with Turing's cache. Dumont holds no provider keys and no prompts of its own to keep current.",
  },
  {
    title: "Enrichment is opt-in and bounded",
    body:
      "LLM classification is off by default. When it is on, it picks from a closed label set, under a daily cap, with a SHA-256 cache so the same text is never paid for twice.",
  },
  {
    title: "Grounding-safe output",
    body:
      "An empty field is left out, never sent as an empty string, and fields map onto Turing's title, abstract and text embedding roles, so a RAG answer cites real content.",
  },
];

/* ── Marketplace ─────────────────────────────────────────────────────── */

export interface Sample {
  slug: string;
  title: string;
  source: string;
  body: string;
}

/** One per `*-plugin-sample/turing-export/app`; the slug is its package name minus `turing-example-`. */
export const SAMPLES: Sample[] = [
  {
    slug: "wknd",
    title: "WKND",
    source: "AEM",
    body: "Adobe's adventure-travel reference site, indexed from AEM, with a search front end on Turing.",
  },
  {
    slug: "wknd-eds",
    title: "WKND EDS",
    source: "Edge Delivery Services",
    body: "The same site served from aem.live and read from its query index.",
  },
  {
    slug: "celestial-gallery",
    title: "Celestial Gallery",
    source: "Files",
    body: "A folder of astrophotography (FITS, TIFF and JPEG files) turned into a filterable archive.",
  },
  {
    slug: "retro-arcade",
    title: "Retro Arcade",
    source: "Database",
    body: "A SQL database of classic arcade games, mapped column by column into a searchable catalogue.",
  },
  {
    slug: "wikipedia",
    title: "Wikipedia Explorer",
    source: "Web crawler",
    body: "Wikipedia crawled politely from the Main Page, with Talk, User and File pages filtered out.",
  },
];

/* ── Suite ───────────────────────────────────────────────────────────── */

export const SUITE = [
  {
    id: "dumont",
    name: "Viglet Dumont",
    role: "Moves content into the index",
    body: "Connectors from AEM, EDS, databases, files, sites and JSON APIs, with audits and a safe finish.",
    href: sitePath("/"),
  },
  {
    id: "turing",
    name: "Viglet Turing ES",
    role: "Search and RAG over it",
    body: "Semantic Navigation, facets, spotlights and generative answers grounded in what Dumont indexed.",
    href: LINKS.turing,
  },
  {
    id: "shio",
    name: "Viglet Shio",
    role: "An agent-ready CMS",
    body: "A CMS with a console, an MCP surface for coding agents, files and a delivery API.",
    href: LINKS.shio,
  },
];

/* ── Where it does not fit ───────────────────────────────────────────── */

/** From docs/STRATEGY.md's non-goals, in the reader's words. */
export const NON_GOALS: { title: string; body: string }[] = [
  {
    title: "A crawler farm",
    body:
      "If the job is scraping thousands of hostile sites behind JavaScript, use Scrapy or Playwright. Dumont's crawler is for sites you are allowed to index, read politely.",
  },
  {
    title: "A warehouse ETL",
    body:
      "Airbyte and Fivetran move rows into warehouses. Dumont moves documents into a search index and watches that the index stays true to the source.",
  },
  {
    title: "AI that publishes on its own",
    body:
      "The LLM drafts sources and suggests labels. It never decides on its own what goes into your index or what comes out of it.",
  },
];

/* ── Resources ───────────────────────────────────────────────────────── */

export interface ResourceCard {
  icon: LucideIcon;
  title: string;
  description: string;
  href: string;
}

export const RESOURCES: ResourceCard[] = [
  {
    icon: BookOpen,
    title: "Documentation",
    description: "Installation, every connector, configuration reference and the REST API.",
    href: LINKS.docs,
  },
  {
    icon: ScrollText,
    title: "Strategy",
    description: "What Dumont is betting on, and what it deliberately is not trying to be.",
    href: LINKS.strategy,
  },
  {
    icon: FileStack,
    title: "Roadmap and changelog",
    description: "The open backlog and every shipped task, one line each, with its commit.",
    href: LINKS.roadmap,
  },
  {
    icon: Plug,
    title: "Source",
    description: "Apache 2.0, Spring Boot 4 on Java 21, a React 19 console. Issues welcome.",
    href: LINKS.repo,
  },
];
