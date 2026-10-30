/**
 * Where the site's links go, with no React in the module: the /docs area
 * (docs/, Astro) imports this file to render the same nav and footer, so a link
 * changed here changes on both.
 */

/**
 * The site is a GitHub project page, served under /dumont/ on openviglet.github.io
 * rather than at the root of a domain. The Vite base, the Astro base, every
 * canonical and every in-site href are built from these two, so a move is an edit
 * here.
 */
export const SITE_ORIGIN = "https://openviglet.github.io";
export const BASE = "/dumont/";
export const SITE_URL = `${SITE_ORIGIN}${BASE}`;

/** A path of this site ("/aem/", "/#how") under the base it is served at. */
export const sitePath = (path: string) => BASE + path.replace(/^\//, "");

export const LINKS = {
  docs: sitePath("/docs/"),
  install: sitePath("/docs/installation-guide/"),
  rest: sitePath("/docs/rest-api/"),
  wordpressDocs: sitePath("/docs/connectors/wordpress/"),
  wordpressDemo: sitePath("/docs/connectors/wordpress/#try-it-locally"),
  repo: "https://github.com/openviglet/dumont",
  license: "https://github.com/openviglet/dumont/blob/HEAD/LICENSE",
  roadmap: "https://github.com/openviglet/dumont/blob/HEAD/docs/ROADMAP.md",
  changelog: "https://github.com/openviglet/dumont/blob/HEAD/docs/CHANGELOG.md",
  strategy: "https://github.com/openviglet/dumont/blob/HEAD/docs/STRATEGY.md",
  issues: "https://github.com/openviglet/dumont/issues",
  image: "https://github.com/orgs/openviglet/packages/container/package/dumont-ce",
  turing: "https://turing.viglet.org",
  shio: "https://shio.viglet.org",
  viglet: "https://www.viglet.org",
} as const;

export interface SiteLink {
  label: string;
  href: string;
}

export interface SiteLinkGroup {
  label: string;
  items: SiteLink[];
}

// Hrefs are anchored at the site's base ("/dumont/#…") so they keep working from
// any page: the browser navigates home and scrolls to the section.
export const NAV_LINKS: SiteLink[] = [{ label: "Docs", href: LINKS.docs }];

/** AEM is the deepest connector, so the nav gives it a pill of its own. */
export const NAV_AEM: SiteLink = { label: "For AEM", href: sitePath("/aem/") };

export const NAV_GROUPS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: sitePath("/aem/") },
      { label: "WordPress and WooCommerce", href: sitePath("/wordpress/") },
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
      { label: "With Turing and Shio", href: sitePath("/#suite") },
      { label: "Self-host", href: sitePath("/#self-host") },
    ],
  },
];

export const FOOTER_BLURB =
  "Open-source connectors from AEM, Edge Delivery Services, databases, files, web sites and JSON APIs into Turing ES, Solr or Elasticsearch, with an audit that keeps the index true to the source.";

export const FOOTER_COLUMNS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: sitePath("/aem/") },
      { label: "WordPress and WooCommerce", href: sitePath("/wordpress/") },
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
      { label: "Documentation", href: LINKS.docs },
      { label: "Source", href: LINKS.repo },
      { label: "Roadmap", href: LINKS.roadmap },
      { label: "Changelog", href: LINKS.changelog },
      { label: "Viglet Turing", href: LINKS.turing },
      { label: "Viglet Shio", href: LINKS.shio },
    ],
  },
];
