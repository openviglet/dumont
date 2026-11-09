/**
 * Where the site's links go, with no React in the module: the /docs area
 * (docs/, Astro) imports this file to render the same nav and footer, so a link
 * changed here changes on both.
 */

/**
 * The site is served at the root of its own domain, dumont.viglet.org (a GitHub
 * Pages custom domain, so the old project-page address redirects here). The Vite
 * base, the Astro base, every canonical and every in-site href are built from
 * these two, so a move is an edit here.
 */
export const SITE_ORIGIN = "https://dumont.viglet.org";
export const BASE = "/";
export const SITE_URL = `${SITE_ORIGIN}${BASE}`;

/** A path of this site ("/aem/", "/#how") under the base it is served at. */
export const sitePath = (path: string) => BASE + path.replace(/^\//, "");

/**
 * The three products, named as viglet.org names them (hub STRATEGY I.7): every
 * visitor-facing mention uses the full name, including repeated ones. There is
 * deliberately no short display name to reach for; a layout that cannot fit the
 * full name wraps instead. Slugs, package names, commands and code identifiers
 * (`turing`, `dumont.indexing.provider`, ...) are technical and stay as they are.
 */
export const PRODUCTS = {
  // `category` is viglet.org's own label for each, shown beside the name and never
  // folded into it (the "DEP" that used to end the name now lives here).
  turing: { fullName: "Viglet Turing", category: "Enterprise Search", href: "https://turing.viglet.org" },
  dumont: { fullName: "Viglet Dumont", category: "Data Exchange Platform", href: SITE_URL },
  shio: { fullName: "Viglet Shio", category: "Agent-ready CMS", href: "https://shio.viglet.org" },
} as const;

export interface SiteLink {
  label: string;
  href: string;
}

/**
 * Where each step of a visitor's journey goes, named by its job (D352) and read by
 * the header, the footer, the calls to action, the docs' Astro overrides and the
 * Links section the prerender writes into llms.txt, so the steps cannot drift apart.
 *
 * Release notes and downloads are viglet.org's pages, fed by this site's
 * changelog.json: there is one release ledger and it is not the GitHub task ledger,
 * which is the development roadmap and is labelled as one.
 */
export const DESTINATIONS = {
  /** Try it now: the one-container run, on the home page. */
  explore: { label: "Run it", href: sitePath("/#self-host") },
  /** A supported setup path: prerequisites, install, and the check that it runs. */
  getStarted: { label: "Get started", href: sitePath("/docs/installation-guide/") },
  docs: { label: "Documentation", href: sitePath("/docs/") },
  download: { label: "Download", href: "https://www.viglet.org/dumont/download/" },
  releaseNotes: { label: "Release notes", href: "https://www.viglet.org/dumont/release-notes/" },
  source: { label: "Source", href: "https://github.com/openviglet/dumont" },
  roadmap: {
    label: "Development roadmap",
    href: "https://github.com/openviglet/dumont/blob/HEAD/docs/ROADMAP.md",
  },
  portfolio: { label: "Part of viglet.org", href: "https://www.viglet.org/" },
} as const satisfies Record<string, SiteLink>;

export const LINKS = {
  docs: DESTINATIONS.docs.href,
  install: DESTINATIONS.getStarted.href,
  rest: sitePath("/docs/rest-api/"),
  // The WordPress plugin is part of Viglet Turing: its page, docs and Playground demo live there.
  wordpress: "https://turing.viglet.org/wordpress/",
  wordpressDocs: "https://turing.viglet.org/docs/integrations/wordpress/",
  wordpressPlayground: "https://turing.viglet.org/playground/",
  repo: DESTINATIONS.source.href,
  license: "https://github.com/openviglet/dumont/blob/HEAD/LICENSE",
  roadmap: DESTINATIONS.roadmap.href,
  strategy: "https://github.com/openviglet/dumont/blob/HEAD/docs/STRATEGY.md",
  issues: "https://github.com/openviglet/dumont/issues",
  image: "https://github.com/orgs/openviglet/packages/container/package/dumont",
  turing: PRODUCTS.turing.href,
  shio: PRODUCTS.shio.href,
  viglet: DESTINATIONS.portfolio.href,
} as const;

export interface SiteLinkGroup {
  label: string;
  items: SiteLink[];
}

// Hrefs are anchored at the site's base ("/#…") so they keep working from
// any page: the browser navigates home and scrolls to the section.
export const NAV_LINKS: SiteLink[] = [{ label: "Docs", href: DESTINATIONS.docs.href }];

/** AEM is the deepest connector, so the nav gives it a pill of its own. */
export const NAV_AEM: SiteLink = { label: "For AEM", href: sitePath("/aem/") };

export const NAV_GROUPS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: sitePath("/aem/") },
      { label: "Edge Delivery Services", href: sitePath("/connectors/#eds") },
      { label: "Databases (JDBC)", href: sitePath("/connectors/#database") },
      { label: "Web crawler", href: sitePath("/connectors/#web-crawler") },
      { label: "Files and documents", href: sitePath("/connectors/#files") },
      { label: "Catalogs and JSON APIs", href: sitePath("/connectors/#catalog") },
      { label: "All connectors", href: sitePath("/connectors/") },
    ],
  },
  {
    label: "The product",
    items: [
      { label: "How it fits", href: sitePath("/#how") },
      { label: "An index you can trust", href: sitePath("/trust/") },
      { label: "AI, where it helps", href: sitePath("/#ai") },
      { label: "Marketplace samples", href: sitePath("/#marketplace") },
      {
        label: `With ${PRODUCTS.turing.fullName} and ${PRODUCTS.shio.fullName}`,
        href: sitePath("/#suite"),
      },
      { label: "Self-host", href: sitePath("/#self-host") },
    ],
  },
];

export const FOOTER_BLURB =
  `Open-source connectors from AEM, Edge Delivery Services, databases, files, web sites and JSON APIs into ${PRODUCTS.turing.fullName}, Solr or Elasticsearch, with an audit that keeps the index true to the source.`;

export const FOOTER_COLUMNS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: sitePath("/aem/") },
      { label: "Edge Delivery Services", href: sitePath("/connectors/#eds") },
      { label: "Databases (JDBC)", href: sitePath("/connectors/#database") },
      { label: "Web crawler", href: sitePath("/connectors/#web-crawler") },
      { label: "Files and documents", href: sitePath("/connectors/#files") },
      { label: "Catalogs and JSON APIs", href: sitePath("/connectors/#catalog") },
    ],
  },
  {
    label: "The product",
    items: [
      { label: "How it fits", href: sitePath("/#how") },
      { label: "An index you can trust", href: sitePath("/trust/") },
      { label: "AI, where it helps", href: sitePath("/#ai") },
      { label: "Marketplace", href: sitePath("/#marketplace") },
      { label: "Self-host", href: sitePath("/#self-host") },
    ],
  },
  {
    label: "Open and inspectable",
    items: [
      DESTINATIONS.getStarted,
      DESTINATIONS.docs,
      DESTINATIONS.download,
      DESTINATIONS.releaseNotes,
      DESTINATIONS.source,
      DESTINATIONS.roadmap,
    ],
  },
  {
    label: "The Viglet portfolio",
    items: [
      { label: PRODUCTS.turing.fullName, href: LINKS.turing },
      { label: PRODUCTS.shio.fullName, href: LINKS.shio },
      { label: "viglet.org", href: DESTINATIONS.portfolio.href },
    ],
  },
];

/**
 * The Links section of llms.txt, written by the prerender from the same map the
 * pages read (D352): an agent following it lands where a visitor would.
 */
export const LLMS_LINKS: { label: string; href: string; note?: string }[] = [
  {
    ...DESTINATIONS.docs,
    href: `${SITE_ORIGIN}${DESTINATIONS.docs.href}`,
    note: `every page has a Markdown twin; [the index of them](${SITE_ORIGIN}${DESTINATIONS.docs.href}llms.txt)`,
  },
  {
    label: "Installation guide",
    href: `${SITE_ORIGIN}${DESTINATIONS.getStarted.href.replace(/\/$/, ".md")}`,
    note: "prerequisites, install and the check that it runs",
  },
  { ...DESTINATIONS.download, note: "on viglet.org: the container image and release artefacts" },
  { ...DESTINATIONS.releaseNotes, note: "on viglet.org, from this site's changelog.json" },
  DESTINATIONS.source,
  { ...DESTINATIONS.roadmap, note: "the open backlog, not release notes" },
  { label: PRODUCTS.turing.fullName, href: PRODUCTS.turing.href },
  { label: PRODUCTS.shio.fullName, href: PRODUCTS.shio.href },
  { label: "viglet.org", href: DESTINATIONS.portfolio.href, note: "the Viglet portfolio" },
];
