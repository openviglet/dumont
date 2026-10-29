import { renderToString } from "react-dom/server";
import App, { ROUTES } from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";

// The scripts after the build (prerender, docs-twins) read where the site is
// served from here, so the origin and base are declared once, in site-links.
export { BASE, SITE_URL } from "@/lib/site-links";

export interface PrerenderRoute {
  /** URL path (also the output directory under dist/). */
  path: string;
  title: string;
  description: string;
}

/**
 * Every route the build prerenders to its own static HTML file. The paths must
 * be the keys of `ROUTES` in App.tsx; `routesAreDeclared` asserts it, because a
 * route here with no page renders the landing page under another title, and a
 * page missing from here never gets a file at all.
 */
export const routes: PrerenderRoute[] = [
  {
    path: "/",
    title: "Viglet Dumont: open-source connectors that keep your search index correct",
    description:
      "Open-source, self-hosted connectors from Adobe Experience Manager, Edge Delivery Services, JDBC databases, file shares, web sites and JSON APIs into Turing ES, Solr or Elasticsearch. Checksummed incremental runs, a nightly audit against the index, deletions that wait for review, and field coverage and drift per source.",
  },
  {
    path: "/aem",
    title: "AEM search connector for on-prem, AMS and Cloud Service | Viglet Dumont",
    description:
      "Index Adobe Experience Manager pages, Content Fragments, Experience Fragments and assets into Turing ES, Solr or Elasticsearch. Reads AEM as a Cloud Service through delivery APIs with no author credential, reindexes every page that embeds a changed fragment, and hears about changes through Adobe I/O Events, an OSGi bundle or a journal poller.",
  },
  {
    path: "/connectors",
    title: "Connectors: AEM, EDS, JDBC, web crawler, files and JSON APIs | Viglet Dumont",
    description:
      "Every source Viglet Dumont reads, and the three engines it writes to. AEM, Edge Delivery Services, databases over JDBC, a polite web crawler, documents through Apache Tika, and paged JSON APIs under request budgets, all through one queue, one audit and one console.",
  },
  {
    path: "/trust",
    title: "An index you can trust: audit, safe finish and drift | Viglet Dumont",
    description:
      "What Viglet Dumont checks after it sends documents: a failed run never de-indexes, a nightly audit against the index, orphan reports a person applies, per-field coverage and drift, field provenance, and run outcomes as Prometheus metrics.",
  },
];

/** The two tables agree, in both directions. Thrown at import time. */
export function routesAreDeclared(): void {
  const declared = new Set(Object.keys(ROUTES));
  const prerendered = new Set(routes.map((r) => r.path));
  const missing = [...declared].filter((p) => !prerendered.has(p));
  const orphan = [...prerendered].filter((p) => !declared.has(p));
  if (missing.length || orphan.length) {
    const parts = [];
    if (missing.length) parts.push(`no prerender entry for ${missing.join(", ")}`);
    if (orphan.length) parts.push(`no page for ${orphan.join(", ")}`);
    throw new Error(`entry-server: routes disagree with App.tsx: ${parts.join("; ")}`);
  }
}

routesAreDeclared();

/** Render a route's app shell to an HTML string for the prerender step. */
export function render(path: string): string {
  return renderToString(
    <ThemeProvider defaultTheme="system" storageKey="dumont-site-theme">
      <App path={path} />
    </ThemeProvider>
  );
}
