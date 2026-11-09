/**
 * The hub contract as a browser sees it (D355). check-hub-contract.mjs reads dist/ as
 * files; this opens it, because clipping, a theme that does not apply, a menu that
 * swallows focus and a banner that loads analytics early are only visible rendered.
 *
 * Serves dist/ with `vite preview`, then in Chromium (playwright-core, pinned exactly):
 *
 * - every marketing route and three documentation templates, at 390, 768 and 1440
 *   pixels, light and dark, with reduced motion: no page error, no horizontal scroll,
 *   the theme actually applied, the full product name on screen, and a screenshot kept;
 * - keyboard: Get started is reachable by Tab, shows focus, and Enter lands on the
 *   installation guide;
 * - the phone menus (the site's and the docs'): open, Escape closes, focus returns to
 *   the button that opened them, and the way back to viglet.org is in them;
 * - consent fresh, declined and withdrawn: no request to Google before a grant, the
 *   choice survives a reload, and withdrawing stops the next page view loading GA;
 * - the WordPress video facade sends nothing to YouTube until it is pressed.
 *
 * Screenshots and report.md land in .checks/ (git-ignored). The screenshots are for a
 * person to look at: this script proves what it can assert, and says what it cannot.
 *
 *   pnpm run build && pnpm run check:rendered
 */
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { preview } from "vite";
import { chromium } from "playwright-core";

const root = process.cwd();
const out = resolve(root, ".checks");
rmSync(out, { recursive: true, force: true });
mkdirSync(resolve(out, "screenshots"), { recursive: true });

const { routes, DESTINATIONS, PRODUCTS } = await import(
  pathToFileURL(resolve(root, "dist-ssr", "entry-server.js")).href
);
const DOCS = ["/docs/", "/docs/installation-guide/", "/docs/connectors/aem/"];
const PAGES = [...routes.map((r) => (r.path === "/" ? "/" : `${r.path}/`)), ...DOCS];
const WIDTHS = [390, 768, 1440];
const THEMES = ["light", "dark"];
const GOOGLE = /googletagmanager\.com|google-analytics\.com/;
const CONSENT_KEY = "dumont-analytics-consent";

const server = await preview({ root, logLevel: "silent", preview: { port: 0, strictPort: false, open: false } });
const base = server.resolvedUrls.local[0].replace(/\/$/, "");
const browser = await chromium.launch();

const results = [];
const failures = [];
const check = (scope, ok, message) => {
  results.push({ scope, ok, message });
  if (!ok) failures.push(`${scope}: ${message}`);
};
const slug = (path) => (path === "/" ? "home" : path.replace(/^\/|\/$/g, "").replace(/\//g, "-"));

/** A context with consent already declined, so the banner covers nothing a screenshot needs. */
async function context(options = {}, consent = "denied") {
  const ctx = await browser.newContext({ reducedMotion: "reduce", ...options });
  if (consent) {
    await ctx.addInitScript(
      ([key, value]) => {
        try {
          localStorage.setItem(key, value);
        } catch {
          /* storage blocked */
        }
      },
      [CONSENT_KEY, consent]
    );
  }
  return ctx;
}

try {
  // ── Routes × widths × themes ──────────────────────────────────────────────────
  const backgrounds = {};
  for (const theme of THEMES) {
    for (const width of WIDTHS) {
      const ctx = await context({ viewport: { width, height: 900 }, colorScheme: theme });
      const page = await ctx.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(e.message));
      for (const path of PAGES) {
        const scope = `${path} @${width} ${theme}`;
        errors.length = 0;
        const response = await page.goto(base + path, { waitUntil: "networkidle" });
        check(scope, response?.status() === 200, `status ${response?.status()}`);
        const m = await page.evaluate(() => ({
          overflow: document.documentElement.scrollWidth - window.innerWidth,
          background: getComputedStyle(document.body).backgroundColor,
          text: document.body.innerText,
        }));
        check(scope, m.overflow <= 0, `scrolls sideways by ${m.overflow}px`);
        check(scope, errors.length === 0, `page error: ${errors.join(" | ")}`);
        check(scope, m.text.includes(PRODUCTS.dumont.fullName), `${PRODUCTS.dumont.fullName} is not on screen`);
        (backgrounds[`${path}@${width}`] ??= {})[theme] = m.background;
        await page.screenshot({ path: resolve(out, "screenshots", `${slug(path)}-${width}-${theme}.png`) });
      }
      await ctx.close();
    }
  }
  for (const [where, by] of Object.entries(backgrounds)) {
    check(`${where} themes`, by.light !== by.dark, `light and dark render the same background (${by.light})`);
  }

  // ── Keyboard: Get started ────────────────────────────────────────────────────
  {
    const ctx = await context({ viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();
    await page.goto(`${base}/`, { waitUntil: "networkidle" });
    let found = null;
    for (let i = 0; i < 30 && !found; i++) {
      await page.keyboard.press("Tab");
      found = await page.evaluate((label) => {
        const el = document.activeElement;
        if (!el || el.textContent?.trim() !== label) return null;
        const s = getComputedStyle(el);
        return { outline: s.outlineStyle !== "none" && s.outlineWidth !== "0px", ring: s.boxShadow !== "none" };
      }, DESTINATIONS.getStarted.label);
    }
    check("keyboard", Boolean(found), `${DESTINATIONS.getStarted.label} is not reachable by Tab`);
    if (found) {
      check("keyboard", found.outline || found.ring, `${DESTINATIONS.getStarted.label} shows no focus indicator`);
      await Promise.all([page.waitForURL(`**${DESTINATIONS.getStarted.href}`), page.keyboard.press("Enter")]);
      check("keyboard", page.url().endsWith(DESTINATIONS.getStarted.href), `Enter went to ${page.url()}`);
    }
    await ctx.close();
  }

  // ── Phone menus: open, Escape, focus return, the way back ────────────────────
  for (const [where, path, opener, menu, back] of [
    ["site menu", "/", 'button[aria-label="Open menu"]', "header nav.lg\\:hidden", DESTINATIONS.portfolio.href],
    ["docs menu", "/docs/", "button.sl-menu-button", ".site-nav-mobile", DESTINATIONS.portfolio.href],
  ]) {
    const ctx = await context({ viewport: { width: 390, height: 844 } });
    const page = await ctx.newPage();
    await page.goto(base + path, { waitUntil: "networkidle" });
    const button = page.locator(opener).first();
    await button.focus();
    await page.keyboard.press("Enter");
    const panel = page.locator(menu).first();
    check(where, await panel.isVisible(), "does not open");
    const hasBack = await panel.locator(`a[href^="${back.replace(/\/$/, "")}"]`).count();
    check(where, hasBack > 0, "has no way back to viglet.org");
    await page.screenshot({ path: resolve(out, "screenshots", `${slug(path)}-menu-390.png`) });
    await page.keyboard.press("Tab");
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
    check(where, !(await panel.isVisible()), "Escape does not close it");
    const returned = await button.evaluate((el) => el === document.activeElement);
    check(where, returned, "focus does not return to the button that opened it");
    await ctx.close();
  }

  // ── Consent: fresh, declined, withdrawn ──────────────────────────────────────
  {
    const ctx = await context({ viewport: { width: 1440, height: 900 } }, null);
    const google = [];
    await ctx.route(GOOGLE, (route) => {
      google.push(route.request().url());
      return route.fulfill({ status: 204, body: "" });
    });
    const page = await ctx.newPage();
    await page.goto(`${base}/`, { waitUntil: "networkidle" });
    const banner = page.getByRole("dialog", { name: "Cookie consent" });
    check("consent fresh", await banner.isVisible(), "no banner on a first visit");
    check("consent fresh", google.length === 0, `Google was contacted before a choice: ${google[0]}`);
    await page.screenshot({ path: resolve(out, "screenshots", "consent-fresh-1440.png") });

    await page.getByRole("button", { name: "Decline", exact: true }).click();
    await page.reload({ waitUntil: "networkidle" });
    check("consent declined", !(await banner.isVisible()), "the banner asks again after Decline");
    check("consent declined", google.length === 0, "Google was contacted after Decline");

    await page.getByRole("button", { name: "Cookie settings" }).click();
    await page.getByRole("button", { name: "Accept analytics" }).click();
    await page.waitForTimeout(300);
    check("consent granted", google.length > 0, "Accept did not load analytics");

    const before = google.length;
    await page.getByRole("button", { name: "Cookie settings" }).click();
    await page.getByRole("button", { name: "Decline", exact: true }).click();
    await page.goto(`${base}/aem/`, { waitUntil: "networkidle" });
    check("consent withdrawn", google.length === before, "the next page view still loaded analytics");
    check(
      "consent withdrawn",
      (await page.evaluate((k) => localStorage.getItem(k), CONSENT_KEY)) === "denied",
      "the withdrawal was not stored"
    );
    await ctx.close();
  }

  // ── The WordPress video facade ───────────────────────────────────────────────
  {
    const ctx = await context({ viewport: { width: 1440, height: 900 } });
    const youtube = [];
    await ctx.route(/youtube(-nocookie)?\.com|ytimg\.com/, (route) => {
      youtube.push(route.request().url());
      return route.abort();
    });
    const page = await ctx.newPage();
    await page.goto(`${base}/connectors/`, { waitUntil: "networkidle" });
    check("video facade", youtube.length === 0, `YouTube was contacted before play: ${youtube[0]}`);
    await page.getByRole("button", { name: /^Play the video/ }).click();
    await page.waitForTimeout(300);
    check("video facade", youtube.length > 0, "pressing play requested nothing from YouTube");
    await ctx.close();
  }
} finally {
  await browser.close();
  await new Promise((done) => server.httpServer.close(done));
}

const passed = results.filter((r) => r.ok).length;
writeFileSync(
  resolve(out, "report.md"),
  [
    "# Rendered hub contract",
    "",
    `${passed} of ${results.length} checks passed across ${PAGES.length} routes at ${WIDTHS.join("/")} px, light and dark, reduced motion.`,
    "Screenshots are in `screenshots/`. They are evidence for a person to read: clipping, readability and",
    "decoration are judged there, not by this script. A local run proves the build, never the deployment.",
    "",
    "| Scope | Result | Check |",
    "|---|---|---|",
    ...results.map((r) => `| ${r.scope} | ${r.ok ? "pass" : "**fail**"} | ${r.message.replace(/\|/g, "\\|")} |`),
    "",
  ].join("\n")
);

if (failures.length) {
  process.stderr.write(`rendered checks: ${failures.length} failure(s)\n${failures.map((f) => `  ${f}\n`).join("")}`);
  process.exit(1);
}
console.log(`rendered checks: ${passed} passed; screenshots and report.md in .checks/`);
