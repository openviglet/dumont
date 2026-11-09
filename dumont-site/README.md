# dumont-site

The public site for **Viglet Dumont**, published to
[dumont.viglet.org](https://dumont.viglet.org/) by
`.github/workflows/publish-github-pages.yml`.

Stack: **Vite + React 19 + TypeScript + Tailwind CSS v4 + shadcn/ui**, the same
conventions as `shio-site` in the Viglet Shio repository. It is standalone: no
backend, no runtime data. Everything on the page is static and prerendered.

## Develop

```bash
pnpm run dev:site      # vite dev server on :5173
pnpm run build:site    # → dumont-site/dist/
```

The build runs `tsc -b`, the client build, the SSR build of
`src/entry-server.tsx`, then `scripts/prerender.mjs` (one static `index.html`
plus a Markdown twin per route) and `scripts/og-image.mjs` (`public/og.svg` →
`dist/og.png`).

The documentation area at `/docs` is a second project, `docs/` (Astro +
Starlight, Pagefind search), built **last** into `dist/docs/`: `vite build`
empties `dist/`, so built any earlier it would be deleted. `pnpm --dir
dumont-site run dev:docs` serves it on its own. Its colours are the site's:
`src/tokens.css` holds the tokens both read, and `docs/src/styles/area.css`
maps Starlight's onto them, so a colour changes in that one file. Its nav and
footer are the site's too: `src/lib/site-links.ts` declares them with no React,
and both the site's components and the area's Starlight overrides read it. A
card on a docs page is `docs/src/components/NavCard.astro` and its siblings,
never inline styles.

The pages are Markdown under `docs/src/content/docs/`, moved from vigletdocs'
`docs-dumont` with their URL tails kept (`/dumont/rest-api` is now
`/docs/rest-api/`). Adding one means adding its `slug` to the `sidebar` in
`docs/astro.config.mjs`. Write an internal link as `/docs/<page>/`, with
the base: `starlight-links-validator` fails the build on a broken link or anchor.
Images live in `docs/public/img/`; ```` ```mermaid ```` fences render as diagrams.

`scripts/docs-twins.mjs` runs after both builds and holds the site's rules
across them: every docs page gets a Markdown twin at `<path>.md` converted from
its render, `/docs/llms.txt` indexes them (and fails the build if
`public/llms.txt` stops naming it), and the docs pages join `sitemap.xml` and
`manifest.json`. Site links to the docs come from `LINKS` in
`src/lib/site-links.ts` and stay on this site.

`scripts/check-hub-contract.mjs` runs last and holds what this site agrees on with
viglet.org, read from the built `dist/`: every product named in full (`PRODUCTS`;
the deliberate exceptions are listed in the script, each with its reason), title,
canonical, Open Graph and JSON-LD agreeing per page, every in-site link and
`#fragment` resolving, the header's Get started, release notes, download and the
way back to viglet.org going where `DESTINATIONS` says, `llms.txt`'s Links being
that map, and no retired destination (docs.viglet.org) shipping. It never touches
the network. `scripts/check-readme-links.mjs` at the repository root checks the
same retired domains in the sources, on every push.

Two checks run after a build rather than in it, and the Pages workflow runs both:

- `pnpm run check:rendered` serves `dist/` and opens it in Chromium
  (`playwright-core`, pinned exactly; `pnpm exec playwright-core install chromium` once):
  every route and three docs templates at 390/768/1440 px in both themes with reduced
  motion, Get started by keyboard, both phone menus (Escape closes, focus returns),
  consent fresh, declined and withdrawn, and the WordPress video facade. Screenshots
  and `report.md` land in `.checks/`; look at them, the script cannot judge a layout.
- `pnpm run check:external-links` fetches every off-site link three times with a
  backoff. A 404 or 410 fails; a reset, a timeout or a bot-wary 403 is reported as
  inconclusive, because the network is not the site.

The site is served at the root of its own domain, dumont.viglet.org (a GitHub
Pages custom domain; `public/CNAME` names it). `SITE_ORIGIN` and `BASE` in
`src/lib/site-links.ts` are the one declaration of that: the Vite base, the
Astro base, every canonical and every in-site href (`sitePath("/aem/")`) are built from them. A hand-typed `href="/…"`
in a component points outside the site.

**Adding a page is two rows**: a `ROUTES` entry in `src/App.tsx` and a `routes`
entry, with title and description, in `src/entry-server.tsx`. The prerender
throws if the two tables disagree, and every `<head>` patch is a
`replaceOrThrow`. Add the page to `public/sitemap.xml` and `public/llms.txt` too.

`scripts/render-readme.mjs` is not part of the site build: the Pages workflow
uses it to render each marketplace sample's README into
`/marketplace/<slug>/index.html`.

## What the site argues

Connectors are table stakes, so the page does not lead with them. It leads with
the outcome (an index that stays correct after launch) and the four ways an
index drifts, then shows how Viglet Dumont answers each: checksummed runs, a safe
finish that never de-indexes on a bad run, a nightly audit, orphan reports a
person applies, and per-field coverage and drift. AEM has its own page because
it is the deepest connector and the audience most likely to be looking for one.

## The rule for copy

Every claim is something the 2026.3 line does today. `src/lib/site-content.ts`
holds the copy as data; when a capability changes, change it there. If the code
cannot back a sentence, the sentence goes, or the gap is filed on the roadmap.
