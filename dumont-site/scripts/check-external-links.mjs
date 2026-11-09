/**
 * Every link this site sends a visitor off-site to, fetched (D355).
 *
 * Kept apart from check-hub-contract.mjs on purpose: that one reads dist/ and is
 * deterministic, so it gates the build. This one needs the network, and a network
 * reset is not a product regression. So each address gets three tries with a
 * backoff, and the verdict has three values:
 *
 * - broken: the destination answered 404 or 410. That is the site's fault, and fails;
 * - inconclusive: no answer, a timeout, a 5xx, or a 401/403/429 a bot-wary host
 *   gives a script and not a browser. Reported, never failed;
 * - ok: anything below 400, redirects followed.
 *
 *   node scripts/check-external-links.mjs      (after `pnpm run build`)
 *
 * Writes .checks/external-links.md and exits 1 only on a broken link.
 */
import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";

const dist = resolve(process.cwd(), "dist");
const out = resolve(process.cwd(), ".checks");
const SITE = "https://dumont.viglet.org";
const TRIES = 3;
const TIMEOUT_MS = 15_000;

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (name === "pagefind" || name === "_astro") return [];
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

const where = new Map();
for (const file of walk(dist).filter((f) => /\.(?:html|txt)$/.test(f))) {
  const text = readFileSync(file, "utf-8");
  const found = file.endsWith(".html")
    ? [...text.matchAll(/<a\s[^>]*href="(https?:\/\/[^"]+)"/g)].map((m) => m[1])
    : [...text.matchAll(/\]\((https?:\/\/[^)\s]+)\)/g)].map((m) => m[1]);
  for (const raw of found) {
    const url = raw.replace(/&amp;/g, "&").split("#")[0];
    if (url.startsWith(SITE)) continue;
    if (!where.has(url)) where.set(url, relative(dist, file));
  }
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

async function probe(url) {
  let last = "";
  for (let attempt = 1; attempt <= TRIES; attempt++) {
    try {
      let res = await fetch(url, { method: "HEAD", redirect: "follow", signal: AbortSignal.timeout(TIMEOUT_MS) });
      // Some hosts refuse HEAD; their answer to GET is the one a browser gets.
      if (res.status === 405 || res.status === 501) {
        res = await fetch(url, { redirect: "follow", signal: AbortSignal.timeout(TIMEOUT_MS) });
      }
      if (res.status < 400) return { verdict: "ok", detail: String(res.status) };
      if (res.status === 404 || res.status === 410) return { verdict: "broken", detail: String(res.status) };
      last = String(res.status);
      if (res.status < 500 && res.status !== 429) return { verdict: "inconclusive", detail: last };
    } catch (e) {
      last = e.name === "TimeoutError" ? "timeout" : e.cause?.code ?? e.message;
    }
    if (attempt < TRIES) await sleep(1000 * 3 ** (attempt - 1));
  }
  return { verdict: "inconclusive", detail: `${last} after ${TRIES} tries` };
}

const results = [];
const queue = [...where.keys()];
await Promise.all(
  Array.from({ length: 6 }, async () => {
    for (let url = queue.shift(); url; url = queue.shift()) {
      results.push({ url, from: where.get(url), ...(await probe(url)) });
    }
  })
);
results.sort((a, b) => a.verdict.localeCompare(b.verdict) || a.url.localeCompare(b.url));

const count = (v) => results.filter((r) => r.verdict === v).length;
mkdirSync(out, { recursive: true });
writeFileSync(
  resolve(out, "external-links.md"),
  [
    "# External links",
    "",
    `${results.length} destinations: ${count("ok")} ok, ${count("broken")} broken, ${count("inconclusive")} inconclusive.`,
    "Inconclusive is the network or a bot-wary host, not a finding about this site.",
    "",
    "| Verdict | Answer | URL | First seen in |",
    "|---|---|---|---|",
    ...results.map((r) => `| ${r.verdict} | ${r.detail} | ${r.url} | ${r.from} |`),
    "",
  ].join("\n")
);

for (const r of results.filter((r) => r.verdict === "inconclusive")) {
  console.warn(`inconclusive: ${r.url} (${r.detail}), first seen in ${r.from}`);
}
const broken = results.filter((r) => r.verdict === "broken");
if (broken.length) {
  process.stderr.write(
    `external links: ${broken.length} broken\n${broken.map((r) => `  ${r.detail} ${r.url} (in ${r.from})\n`).join("")}`
  );
  process.exit(1);
}
console.log(`external links: ${count("ok")} of ${results.length} ok, ${count("inconclusive")} inconclusive, none broken.`);
