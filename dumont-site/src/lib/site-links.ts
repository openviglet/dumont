/**
 * Where the site's links go, with no React in the module: the /docs area
 * (docs/, Astro) imports this file to render the same nav and footer, so a link
 * changed here changes on both.
 */

export const LINKS = {
  docs: "/docs/",
  install: "/docs/installation-guide/",
  rest: "/docs/rest-api/",
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

// Hrefs are root-anchored ("/#…") so they keep working from any page: the
// browser navigates home and scrolls to the section.
export const NAV_LINKS: SiteLink[] = [{ label: "Docs", href: LINKS.docs }];

/** AEM is the deepest connector, so the nav gives it a pill of its own. */
export const NAV_AEM: SiteLink = { label: "For AEM", href: "/aem/" };

export const NAV_GROUPS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: "/aem/" },
      { label: "Edge Delivery Services", href: "/connectors/#eds" },
      { label: "Databases (JDBC)", href: "/connectors/#database" },
      { label: "Web crawler", href: "/connectors/#web-crawler" },
      { label: "Files and documents", href: "/connectors/#files" },
      { label: "Catalogs and JSON APIs", href: "/connectors/#catalog" },
      { label: "All connectors", href: "/connectors/" },
    ],
  },
  {
    label: "The product",
    items: [
      { label: "How it fits", href: "/#how" },
      { label: "An index you can trust", href: "/trust/" },
      { label: "AI, where it helps", href: "/#ai" },
      { label: "Marketplace samples", href: "/#marketplace" },
      { label: "With Turing and Shio", href: "/#suite" },
      { label: "Self-host", href: "/#self-host" },
    ],
  },
];

export const FOOTER_BLURB =
  "Open-source connectors from AEM, Edge Delivery Services, databases, files, web sites and JSON APIs into Turing ES, Solr or Elasticsearch, with an audit that keeps the index true to the source.";

export const FOOTER_COLUMNS: SiteLinkGroup[] = [
  {
    label: "Connectors",
    items: [
      { label: "Adobe Experience Manager", href: "/aem/" },
      { label: "Edge Delivery Services", href: "/connectors/#eds" },
      { label: "Databases (JDBC)", href: "/connectors/#database" },
      { label: "Web crawler", href: "/connectors/#web-crawler" },
      { label: "Files and documents", href: "/connectors/#files" },
      { label: "Catalogs and JSON APIs", href: "/connectors/#catalog" },
    ],
  },
  {
    label: "The product",
    items: [
      { label: "How it fits", href: "/#how" },
      { label: "An index you can trust", href: "/trust/" },
      { label: "AI, where it helps", href: "/#ai" },
      { label: "Marketplace", href: "/#marketplace" },
      { label: "Self-host", href: "/#self-host" },
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
