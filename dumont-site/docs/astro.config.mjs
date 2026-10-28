// The documentation area, at https://dumont.viglet.org/docs.
//
// A second build beside the site rather than more routes in it: the site is a prerendered
// landing page with no sidebar, no per-page contents and no search, and Starlight is those
// three, indexed at build time so nothing here needs a service to be up.
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import mermaid from "astro-mermaid";
import starlightLinksValidator from "starlight-links-validator";

// The site's own consent rule (src/lib/analytics.ts): GA loads only once a visitor accepted
// it, and the choice is the site's localStorage key — same origin, so a choice made on the
// landing page holds here. The docs ask nothing themselves; a visitor who never chose stays
// uncounted.
const GA_ID = "G-SMSL7R3897";
const CONSENT_KEY = "dumont-analytics-consent";

export default defineConfig({
  site: "https://dumont.viglet.org",
  // Astro rewrites the links it generates, not ones typed by hand: an absolute href written
  // into a page must carry /docs itself or it 404s in production alone.
  base: "/docs",
  // Inside the site's dist/, because the deploy copies that one directory. `vite build` empties
  // dist/, so this build runs *last* in dumont-site's `build` — earlier, the step after it
  // deletes the area and the deploy goes green without it.
  outDir: "../dist/docs",

  integrations: [
    // Eight pages carry ```mermaid sequence and flow diagrams, as they did under Docusaurus.
    // Rendered in the browser, so the build needs no headless browser; before Starlight, which
    // would otherwise take the fence as code.
    mermaid({ autoTheme: true }),
    starlight({
      // A broken internal link or anchor fails the build, the way Docusaurus failed it.
      plugins: [starlightLinksValidator()],
      title: "Viglet Dumont DEP",
      description:
        "How to install, configure and run Dumont: the connectors, the indexing plugins and the REST API.",
      // The site's own icon, served at its root: Starlight's default would need a second copy
      // in a public/ here, which is a file that drifts from the one the site ships.
      favicon: "https://dumont.viglet.org/favicon.svg",
      pagefind: true,
      social: [{ icon: "github", label: "GitHub", href: "https://github.com/openviglet/dumont" }],
      // The site's own tokens first, then the mapping of Starlight's onto them: the area is
      // themed from the file the landing page is, so one edit there restyles both.
      customCss: ["../src/tokens.css", "./src/styles/area.css"],
      components: {
        SiteTitle: "./src/components/SiteTitle.astro",
        // The site's nav and footer, read from src/lib/site-links.ts as the site's are.
        SocialIcons: "./src/components/SocialIcons.astro",
        MobileMenuFooter: "./src/components/MobileMenuFooter.astro",
        Footer: "./src/components/Footer.astro",
        ThemeProvider: "./src/components/ThemeProvider.astro",
      },
      head: [
        // The site's type pair, from the same Google Fonts request its index.html makes.
        { tag: "link", attrs: { rel: "preconnect", href: "https://fonts.googleapis.com" } },
        {
          tag: "link",
          attrs: { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: true },
        },
        {
          tag: "link",
          attrs: {
            rel: "stylesheet",
            href: "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:ital,wght@0,400;0,500;0,600;0,700;0,800;1,400&family=JetBrains+Mono:wght@400;500;700&display=swap",
          },
        },
        { tag: "meta", attrs: { name: "theme-color", content: "#0f766e" } },
        {
          tag: "script",
          content: `
try {
  if (localStorage.getItem(${JSON.stringify(CONSENT_KEY)}) === "granted") {
    var s = document.createElement("script");
    s.async = true;
    s.src = "https://www.googletagmanager.com/gtag/js?id=${GA_ID}";
    document.head.appendChild(s);
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag("js", new Date());
    window.gtag("config", "${GA_ID}");
  }
} catch (e) {}
`,
        },
      ],
      // The order and groups of vigletdocs' sidebars-dumont.ts, which these pages came from.
      sidebar: [
        { label: "Overview", link: "/" },
        {
          label: "Getting Started",
          items: [{ slug: "getting-started/intro" }, { slug: "getting-started/core-concepts" }],
        },
        { slug: "installation-guide" },
        { slug: "configuration-reference" },
        { slug: "architecture" },
        { slug: "indexing-plugins" },
        {
          label: "Connectors",
          items: [
            { slug: "connectors/overview" },
            { slug: "connectors/web-crawler" },
            { slug: "connectors/database" },
            { slug: "connectors/filesystem" },
            { slug: "connectors/aem" },
            { slug: "aem-event-listener" },
            { slug: "connectors/catalog" },
            { slug: "connectors/wordpress" },
            { slug: "connectors/wordpress-turing" },
          ],
        },
        { slug: "developer-guide" },
        { slug: "rest-api" },
        {
          label: "Extending Connectors",
          items: [{ slug: "extending-aem" }, { slug: "extending-database" }],
        },
      ],
      lastUpdated: true,
    }),
  ],
});
