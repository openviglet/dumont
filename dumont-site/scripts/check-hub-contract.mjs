/**
 * The hub contract, checked on what the build wrote (D355).
 *
 * viglet.org and this site agree on a few things a later edit could quietly undo,
 * and every one of them is visible in dist/ without a browser:
 *
 * - every product is named in full (hub STRATEGY I.7): no bare Turing, Dumont or
 *   Shio in visitor text, metadata, JSON-LD, the Markdown twins or llms.txt;
 * - each page's title, canonical, og:url, og:title and JSON-LD agree;
 * - every in-site link reaches a file, and every #fragment an element with that id;
 * - the journey goes where src/lib/site-links.ts says: Get started, release notes,
 *   download and the way back to viglet.org, and llms.txt's Links are that map;
 * - no retired destination ships (docs.viglet.org, Viglet Turing's dead /playground/).
 *
 * It reads the maps from the SSR bundle instead of restating them, so the check and
 * the pages cannot disagree about what is expected. It runs last in `build`, which is
 * what the publish workflow's build:site runs. Nothing here touches the network: a
 * destination off this site is compared by address, never fetched.
 *
 *   node scripts/check-hub-contract.mjs      (after `pnpm run build`)
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const dist = resolve(process.cwd(), "dist");
const { DESTINATIONS, LLMS_LINKS, PRODUCTS, SITE_ORIGIN, routes } = await import(
  pathToFileURL(resolve(process.cwd(), "dist-ssr", "entry-server.js")).href
);

/**
 * Bare names that are deliberate, each with why. A technical identifier the visitor
 * has to type or find as written stays as written (hub STRATEGY I.7).
 */
const NAME_EXCEPTIONS = [
  // The OSGi configuration's own label in the AEM web console (aem-server's
  // DumAemIndexerConfig): a reader searches for it as spelled.
  "Dumont Indexer Configuration",
];

/**
 * Published next to this build by the Pages workflow, not by it: the marketplace
 * samples are separate apps, so a local build has no file to resolve them against.
 */
const PUBLISHED_ELSEWHERE = ["/marketplace/"];

const RETIRED = [
  [/\bdocs\.viglet\.org\b/, "docs.viglet.org is retired; Viglet Turing's docs are turing.viglet.org/docs/"],
  [/turing\.viglet\.org\/playground\//, "Viglet Turing's demo store is /wp-demo/; /playground/ answers 404"],
];

const failures = [];
const fail = (file, message) => failures.push(`${relative(dist, file) || file}: ${message}`);

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (name === "pagefind" || name === "_astro" || name === "assets") return [];
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

const files = walk(dist);
const htmlFiles = files.filter((f) => f.endsWith(".html"));
const textFiles = files.filter((f) => /\.(?:md|txt)$/.test(f));

// ── 1. Full product names ────────────────────────────────────────────────────────
const SHORT = Object.values(PRODUCTS).map((p) => p.fullName.replace(/^Viglet /, ""));
const BARE = new RegExp(`(?<!Viglet )(?<![\\w./@#:-])\\b(?:${SHORT.join("|")})\\b(?![\\w-])`, "g");

function decode(text) {
  return text
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&#x27;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
}

/** What a visitor or a crawler reads in a page: its text, its labels and its metadata. */
function visibleText(html) {
  const ldJson = [...html.matchAll(/<script type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)]
    .map((m) => m[1])
    .join(" ");
  const attributes = [...html.matchAll(/\s(?:content|alt|title|aria-label)="([^"]*)"/g)]
    .map((m) => m[1])
    .join(" ");
  const body = html
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<(script|style|pre|code)\b[\s\S]*?<\/\1>/g, " ")
    .replace(/<[^>]+>/g, " ");
  return decode(`${body} ${attributes} ${ldJson}`);
}

/** Markdown and plain text, minus code: a property name is not a product mention. */
function proseText(text) {
  return text.replace(/```[\s\S]*?```/g, " ").replace(/`[^`]*`/g, " ");
}

for (const file of [...htmlFiles, ...textFiles]) {
  if (file.endsWith("changelog.json")) continue;
  const raw = readFileSync(file, "utf-8");
  let text = file.endsWith(".html") ? visibleText(raw) : proseText(raw);
  for (const kept of NAME_EXCEPTIONS) text = text.split(kept).join(" ");
  for (const m of text.matchAll(BARE)) {
    const at = m.index ?? 0;
    fail(file, `bare product name: "…${text.slice(Math.max(0, at - 40), at + 30).replace(/\s+/g, " ")}…"`);
  }
}

// ── 2. Title, canonical, Open Graph and JSON-LD agree ────────────────────────────
const attr = (html, re) => (html.match(re) || [])[1];
const pageFile = (path) => join(dist, path.replace(/^\//, ""), "index.html");

const dumont = PRODUCTS.dumont.fullName;
const pages = [
  ...routes.map((r) => pageFile(r.path === "/" ? "/" : `${r.path}/`)),
  ...htmlFiles.filter((f) => f.includes(`${join("dist", "docs")}`) && f.endsWith("index.html")),
];
for (const file of pages) {
  if (!existsSync(file)) {
    fail(file, "a declared route has no file");
    continue;
  }
  const html = readFileSync(file, "utf-8");
  if (/<meta http-equiv="refresh"/.test(html)) continue; // a redirect page carries none of this
  const title = decode(attr(html, /<title>([^<]*)<\/title>/) ?? "");
  const canonical = attr(html, /<link rel="canonical" href="([^"]+)"/);
  const ogUrl = attr(html, /<meta property="og:url" content="([^"]+)"/);
  const ogTitle = attr(html, /<meta property="og:title" content="([^"]+)"/);
  if (!title.includes(dumont)) fail(file, `title does not name ${dumont}: "${title}"`);
  if (!canonical) fail(file, "no canonical link");
  else if (!canonical.startsWith(SITE_ORIGIN)) fail(file, `canonical is off this site: ${canonical}`);
  if (canonical && ogUrl && canonical !== ogUrl) fail(file, `canonical ${canonical} and og:url ${ogUrl} differ`);
  if (!ogTitle) fail(file, "no og:title");
  for (const m of html.matchAll(/<script type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)) {
    try {
      JSON.parse(m[1]);
    } catch (e) {
      fail(file, `JSON-LD does not parse: ${e.message}`);
    }
  }
}

// ── 3. In-site links resolve, fragments included ────────────────────────────────
const idsOf = new Map();
function ids(file) {
  if (!idsOf.has(file)) {
    idsOf.set(file, new Set([...readFileSync(file, "utf-8").matchAll(/\sid="([^"]+)"/g)].map((m) => m[1])));
  }
  return idsOf.get(file);
}
function target(pathname) {
  const clean = decodeURIComponent(pathname);
  const direct = join(dist, clean);
  if (clean.endsWith("/")) return join(direct, "index.html");
  if (existsSync(direct) && statSync(direct).isFile()) return direct;
  return join(direct, "index.html");
}

for (const file of htmlFiles) {
  const html = readFileSync(file, "utf-8");
  if (/<meta http-equiv="refresh"/.test(html)) continue;
  for (const m of html.matchAll(/<a\s[^>]*href="([^"]+)"/g)) {
    const href = decode(m[1]);
    let url;
    try {
      url = new URL(href, `${SITE_ORIGIN}/${relative(dist, file).replace(/\\/g, "/")}`);
    } catch {
      fail(file, `unparseable link ${href}`);
      continue;
    }
    if (url.origin !== SITE_ORIGIN) continue;
    if (PUBLISHED_ELSEWHERE.some((p) => url.pathname.startsWith(p))) continue;
    const dest = target(url.pathname);
    if (!existsSync(dest)) {
      fail(file, `link to ${url.pathname} reaches no file`);
      continue;
    }
    const fragment = decodeURIComponent(url.hash.slice(1));
    if (fragment && dest.endsWith(".html") && !ids(dest).has(fragment)) {
      fail(file, `link to ${url.pathname}#${fragment}: no element has that id`);
    }
  }
}

// ── 4. The journey goes where the map says ──────────────────────────────────────
const hrefsIn = (html) => new Set([...html.matchAll(/<a\s[^>]*href="([^"]+)"/g)].map((m) => decode(m[1])));
for (const file of pages.filter(existsSync)) {
  const html = readFileSync(file, "utf-8");
  if (/<meta http-equiv="refresh"/.test(html)) continue;
  const hrefs = hrefsIn(html);
  for (const key of ["releaseNotes", "download", "source", "docs"]) {
    if (!hrefs.has(DESTINATIONS[key].href)) fail(file, `no link to ${DESTINATIONS[key].label} (${DESTINATIONS[key].href})`);
  }
  const portfolio = DESTINATIONS.portfolio.href.replace(/\/$/, "");
  if (![...hrefs].some((h) => h.replace(/\/$/, "") === portfolio)) fail(file, "no way back to viglet.org");
}
for (const route of routes) {
  const file = pageFile(route.path === "/" ? "/" : `${route.path}/`);
  if (!existsSync(file)) continue;
  const header = (readFileSync(file, "utf-8").match(/<header[\s\S]*?<\/header>/) || [""])[0];
  const getStarted = [...header.matchAll(/<a\s[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g)].find((m) =>
    m[2].replace(/<[^>]+>/g, "").trim().startsWith(DESTINATIONS.getStarted.label)
  );
  if (!getStarted) fail(file, `the header has no ${DESTINATIONS.getStarted.label}`);
  else if (getStarted[1] !== DESTINATIONS.getStarted.href) {
    fail(file, `${DESTINATIONS.getStarted.label} goes to ${getStarted[1]}, not ${DESTINATIONS.getStarted.href}`);
  }
}
const llms = readFileSync(join(dist, "llms.txt"), "utf-8");
for (const link of LLMS_LINKS) {
  if (!llms.includes(`[${link.label}](${link.href})`)) fail(join(dist, "llms.txt"), `Links lacks ${link.label} → ${link.href}`);
}

// ── 5. Nothing retired ships ─────────────────────────────────────────────────────
for (const file of [...htmlFiles, ...textFiles]) {
  const raw = readFileSync(file, "utf-8");
  for (const [re, why] of RETIRED) if (re.test(raw)) fail(file, why);
}

if (failures.length) {
  process.stderr.write(`hub contract: ${failures.length} problem(s)\n${failures.map((f) => `  ${f}\n`).join("")}`);
  process.exit(1);
}
console.log(
  `hub contract: ${htmlFiles.length} page(s) and ${textFiles.length} text file(s) name every product in full, ` +
    `agree on title, canonical and Open Graph, resolve every in-site link and fragment, and follow the destination map.`
);
