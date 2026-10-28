/**
 * The /docs area joined to the site it is a section of, run after both builds.
 *
 * The site's rule is that every route has a Markdown twin at `<path>.md` and that
 * the sitemap and the manifest name every route. The area is a second build
 * (docs/, Starlight) that knows none of that, so this step holds the rule across
 * both, from what the area actually rendered:
 *
 * - a twin per docs page at `<path>.md` (`/docs/rest-api/` -> `/docs/rest-api.md`),
 *   converted from the built HTML and never written a second time;
 * - `/docs/llms.txt`, an index of those twins, which `public/llms.txt` must name;
 * - every docs page added to `dist/sitemap.xml` and to `dist/manifest.json`.
 *
 * Runs last: `vite build` empties dist/ and Astro empties dist/docs/.
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

import { parse } from "node-html-parser";

const SITE_ORIGIN = "https://dumont.viglet.org";
const dist = resolve(dirname(fileURLToPath(import.meta.url)), "..", "dist");
const built = join(dist, "docs");

const HEADINGS = { h1: "#", h2: "##", h3: "###", h4: "####", h5: "#####", h6: "######" };
// Chrome, not content: a twin carrying the sidebar would be a hundred links per page.
const SKIP = new Set(["nav", "footer", "script", "style", "noscript", "button", "svg"]);

// Starlight's heading permalink, whose only text is a screen-reader "Section titled ...".
const SKIP_CLASS = ["sl-anchor-link"];

const text = (node) => node.text.replaceAll(/\s+/g, " ").trim();

// node-html-parser keeps a <pre>'s content as raw text, and expressive-code renders each line
// of a code block as a `.ec-line` div inside it, so the lines are read from a second parse.
function code(pre) {
  const lines = parse(pre.innerHTML).querySelectorAll(".ec-line");
  const body = lines.length ? lines.map((line) => line.text).join("\n") : parse(pre.innerHTML).text;
  return body.trimEnd();
}

function row(tr) {
  const cells = tr.querySelectorAll("th, td").map(text);
  if (!cells.length) return [];
  const head = tr.querySelectorAll("th").length ? [`| ${cells.map(() => "---").join(" | ")} |`] : [];
  return [`| ${cells.join(" | ")} |`, ...head];
}

const when = (run, line) => (run ? [line] : []);
const FENCE = "```";

// One rule per block element; anything else is a container, read through.
const BLOCKS = {
  p: (el) => when(text(el), `${text(el)}\n`),
  li: (el) => when(text(el), `- ${text(el)}`),
  pre: (el) => [`${FENCE}\n${code(el)}\n${FENCE}\n`],
  tr: row,
  ...Object.fromEntries(
    Object.entries(HEADINGS).map(([tag, mark]) => [tag, (el) => [`\n${mark} ${text(el)}\n`]]),
  ),
};

const skipped = (el, tag) => SKIP.has(tag) || SKIP_CLASS.some((name) => el.classList?.contains(name));

function convert(node, out) {
  for (const child of node.childNodes) {
    const tag = child.rawTagName?.toLowerCase();
    if (!tag) out.push(...when(text(child), text(child)));
    else if (skipped(child, tag)) continue;
    else if (BLOCKS[tag]) out.push(...BLOCKS[tag](child));
    else convert(child, out);
  }
  return out;
}

function* pages(where) {
  for (const name of readdirSync(where)) {
    const path = join(where, name);
    if (statSync(path).isDirectory()) yield* pages(path);
    else if (name === "index.html") yield path;
  }
}

const written = [];
for (const file of pages(built)) {
  const html = readFileSync(file, "utf-8");
  const article = parse(html).querySelector(".sl-markdown-content");
  if (!article) continue; // the 404 page, which has no article
  const root = parse(html);
  const title = root.querySelector("h1")?.text.trim() ?? "";
  const description = root.querySelector('meta[name="description"]')?.getAttribute("content") ?? "";
  const body = convert(article, []).join("\n").replace(/\n{3,}/g, "\n\n").trim();
  const slug = relative(built, dirname(file)).split(sep).join("/");
  const path = slug ? `/docs/${slug}` : "/docs";
  const markdown = `# ${title}\n\n${body}\n`;
  writeFileSync(join(dist, `.${path}.md`), markdown, "utf-8");
  written.push({
    path: `${path}/`,
    markdown: `${path}.md`,
    title,
    description,
    bytes: { html: Buffer.byteLength(html), markdown: Buffer.byteLength(markdown) },
  });
}

if (!written.length) {
  throw new Error("docs-twins: no page carried .sl-markdown-content; the theme moved the article");
}
const empty = written.filter((one) => one.bytes.markdown < 200);
if (empty.length) {
  throw new Error(`docs-twins: ${empty.length} twin(s) came out empty: ${empty.map((one) => one.path).join(", ")}`);
}
written.sort((a, b) => a.path.localeCompare(b.path));

writeFileSync(
  join(built, "llms.txt"),
  [
    "# Viglet Dumont DEP documentation",
    "",
    "> Every page of the documentation has a Markdown twin at `<path>.md`, converted from the same render as the page.",
    "",
    "## Pages",
    "",
    ...written.map((one) => `- [${one.title}](${SITE_ORIGIN}${one.markdown}): ${one.description}`),
    "",
  ].join("\n"),
  "utf-8",
);

const llms = readFileSync(join(dist, "llms.txt"), "utf-8");
if (!llms.includes(`${SITE_ORIGIN}/docs/llms.txt`)) {
  throw new Error("docs-twins: public/llms.txt does not name /docs/llms.txt, so an agent at the root never finds the docs");
}

const sitemapPath = join(dist, "sitemap.xml");
const sitemap = readFileSync(sitemapPath, "utf-8");
if (!sitemap.includes("</urlset>")) throw new Error("docs-twins: dist/sitemap.xml has no </urlset>");
// Idempotent: a second run over the same dist/ adds nothing.
const urls = written
  .filter((one) => !sitemap.includes(`<loc>${SITE_ORIGIN}${one.path}</loc>`))
  .map((one) => `  <url>\n    <loc>${SITE_ORIGIN}${one.path}</loc>\n    <changefreq>monthly</changefreq>\n    <priority>0.5</priority>\n  </url>\n`)
  .join("");
writeFileSync(sitemapPath, sitemap.replace("</urlset>", `${urls}</urlset>`), "utf-8");

const manifestPath = join(dist, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf-8"));
manifest.routes = [...manifest.routes.filter((one) => !one.path.startsWith("/docs/")), ...written];
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf-8");

console.log(`docs-twins: ${written.length} docs page(s), each with a twin, in the sitemap and the manifest`);
