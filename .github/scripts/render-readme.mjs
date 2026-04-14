#!/usr/bin/env node
/**
 * render-readme.mjs
 *
 * Renders a README.md file into a styled index.html, matching the
 * Dumont GitHub Pages landing page look and feel.
 *
 * Usage:
 *   node render-readme.mjs <input-readme.md> <output-index.html> <project-slug> [zip-filename]
 */

import { readFileSync, writeFileSync, existsSync, readdirSync } from "node:fs";
import { dirname, basename, resolve } from "node:path";
import { marked } from "marked";

const [, , inputPath, outputPath, slug, zipArg] = process.argv;

if (!inputPath || !outputPath || !slug) {
  console.error(
    "Usage: node render-readme.mjs <input-readme.md> <output-index.html> <project-slug> [zip-filename]",
  );
  process.exit(1);
}

if (!existsSync(inputPath)) {
  console.error(`README not found: ${inputPath}`);
  process.exit(1);
}

// Auto-detect zip if not given: pick the first .zip sitting next to the output.
let zipFile = zipArg;
if (!zipFile) {
  const outDir = dirname(outputPath);
  if (existsSync(outDir)) {
    const zip = readdirSync(outDir).find((f) => f.endsWith(".zip"));
    if (zip) zipFile = zip;
  }
}

const markdown = readFileSync(inputPath, "utf8");

marked.setOptions({
  gfm: true,
  breaks: false,
  headerIds: true,
  mangle: false,
});

const bodyHtml = marked.parse(markdown);

// Extract the first H1 for <title>.
const h1Match = markdown.match(/^#\s+(.+?)\s*$/m);
const title = h1Match ? h1Match[1].replace(/[*_`]/g, "") : slug;

const zipLink = zipFile
  ? `<a class="download" href="./${zipFile}" download>
       <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
         <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
         <polyline points="7 10 12 15 17 10"/>
         <line x1="12" y1="15" x2="12" y2="3"/>
       </svg>
       Download&nbsp;<code>${zipFile}</code>
     </a>`
  : "";

const manifestLink = existsSync(resolve(dirname(outputPath), "turing-manifest.json"))
  ? `<a class="secondary" href="./turing-manifest.json">turing-manifest.json</a>`
  : "";

const html = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${escapeHtml(title)} — Dumont Marketplace</title>
    <meta
      name="description"
      content="${escapeHtml(title)} — a ready-to-use connector sample from the Viglet Dumont Marketplace."
    />
    <meta name="theme-color" content="#0f766e" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:ital,wght@0,400;0,500;0,600;0,700;0,800;1,400&family=JetBrains+Mono:wght@500;700&display=swap"
      rel="stylesheet"
    />
    <link
      rel="stylesheet"
      href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/styles/github-dark.min.css"
    />
    <style>
      *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

      :root {
        --bg: #ffffff;
        --bg-muted: #f8f8fa;
        --fg: #1a1a1f;
        --fg-muted: #6b7280;
        --card-bg: #ffffff;
        --card-border: #e5e7eb;
        --teal: #0f766e;
        --teal-dark: #115e59;
        --teal-light: #14b8a6;
        --accent: #ccfbf1;
        --code-bg: #f4f4f6;
        --radius: 16px;
        --radius-sm: 10px;
        --font: "Plus Jakarta Sans", system-ui, sans-serif;
        --font-mono: "JetBrains Mono", monospace;
      }

      @media (prefers-color-scheme: dark) {
        :root {
          --bg: #0f172a;
          --bg-muted: #1e293b;
          --fg: #f1f5f9;
          --fg-muted: #94a3b8;
          --card-bg: #1e293b;
          --card-border: #334155;
          --teal: #14b8a6;
          --teal-dark: #0f766e;
          --teal-light: #2dd4bf;
          --code-bg: #0b1220;
        }
      }

      body {
        font-family: var(--font);
        background: var(--bg);
        color: var(--fg);
        min-height: 100vh;
        -webkit-font-smoothing: antialiased;
        line-height: 1.7;
      }

      .hero {
        position: relative;
        overflow: hidden;
        padding: 48px 24px 40px;
        background:
          radial-gradient(1200px 400px at 15% -10%, rgba(20, 184, 166, 0.18), transparent 60%),
          radial-gradient(900px 320px at 95% 0%, rgba(15, 118, 110, 0.12), transparent 60%);
        border-bottom: 1px solid var(--card-border);
      }

      .hero-inner {
        max-width: 960px;
        margin: 0 auto;
      }

      .crumbs {
        font-size: 13px;
        color: var(--fg-muted);
        margin-bottom: 18px;
      }
      .crumbs a {
        color: var(--teal);
        text-decoration: none;
        font-weight: 500;
      }
      .crumbs a:hover { text-decoration: underline; }
      .crumbs .sep { margin: 0 8px; opacity: 0.6; }

      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 20px;
      }

      .download, .secondary {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        padding: 10px 16px;
        border-radius: var(--radius-sm);
        font-weight: 600;
        font-size: 14px;
        text-decoration: none;
        transition: transform 0.15s ease, box-shadow 0.15s ease;
      }
      .download {
        background: linear-gradient(135deg, var(--teal) 0%, var(--teal-dark) 100%);
        color: #fff;
        box-shadow: 0 6px 18px rgba(15, 118, 110, 0.25);
      }
      .download:hover {
        transform: translateY(-1px);
        box-shadow: 0 10px 24px rgba(15, 118, 110, 0.32);
      }
      .download code {
        font-family: var(--font-mono);
        font-size: 12px;
        background: rgba(255,255,255,0.15);
        padding: 2px 6px;
        border-radius: 6px;
      }
      .secondary {
        border: 1px solid var(--card-border);
        color: var(--fg);
        background: var(--card-bg);
      }
      .secondary:hover { border-color: var(--teal); color: var(--teal); }

      main {
        max-width: 880px;
        margin: 0 auto;
        padding: 48px 24px 96px;
      }

      article h1 {
        font-size: clamp(28px, 4vw, 40px);
        font-weight: 800;
        letter-spacing: -0.02em;
        margin-bottom: 12px;
        line-height: 1.15;
      }
      article h2 {
        font-size: 24px;
        font-weight: 700;
        margin-top: 44px;
        margin-bottom: 16px;
        padding-bottom: 10px;
        border-bottom: 1px solid var(--card-border);
      }
      article h3 {
        font-size: 18px;
        font-weight: 700;
        margin-top: 28px;
        margin-bottom: 10px;
      }
      article p { margin: 0 0 16px; color: var(--fg); }
      article p:has(+ p) { margin-bottom: 14px; }

      article ul, article ol {
        margin: 0 0 18px 24px;
        padding-left: 4px;
      }
      article li { margin-bottom: 6px; }

      article a {
        color: var(--teal);
        text-decoration: none;
        border-bottom: 1px solid transparent;
        transition: border-color 0.15s ease;
      }
      article a:hover { border-bottom-color: var(--teal); }

      article blockquote {
        margin: 20px 0;
        padding: 14px 20px;
        border-left: 4px solid var(--teal);
        background: var(--bg-muted);
        color: var(--fg-muted);
        font-style: italic;
        border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
      }

      article code {
        font-family: var(--font-mono);
        font-size: 0.88em;
        background: var(--code-bg);
        padding: 2px 6px;
        border-radius: 6px;
      }
      article pre {
        background: #0b1220;
        color: #e2e8f0;
        padding: 16px 20px;
        border-radius: var(--radius-sm);
        overflow-x: auto;
        margin: 20px 0;
        font-size: 13px;
        line-height: 1.55;
      }
      article pre code {
        background: transparent;
        padding: 0;
        border-radius: 0;
        color: inherit;
      }

      article table {
        width: 100%;
        border-collapse: collapse;
        margin: 20px 0;
        font-size: 14px;
      }
      article th, article td {
        padding: 10px 14px;
        text-align: left;
        border-bottom: 1px solid var(--card-border);
      }
      article th {
        background: var(--bg-muted);
        font-weight: 600;
      }

      article img {
        max-width: 100%;
        height: auto;
        vertical-align: middle;
        margin: 2px 4px 2px 0;
      }

      article hr {
        border: 0;
        height: 1px;
        background: var(--card-border);
        margin: 36px 0;
      }

      footer {
        max-width: 880px;
        margin: 0 auto;
        padding: 24px 24px 48px;
        border-top: 1px solid var(--card-border);
        color: var(--fg-muted);
        font-size: 13px;
        display: flex;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 12px;
      }
      footer a { color: var(--teal); text-decoration: none; }
      footer a:hover { text-decoration: underline; }
    </style>
  </head>
  <body>
    <section class="hero">
      <div class="hero-inner">
        <nav class="crumbs">
          <a href="/">Dumont</a>
          <span class="sep">/</span>
          <a href="/#marketplace">Marketplace</a>
          <span class="sep">/</span>
          <span>${escapeHtml(slug)}</span>
        </nav>
        <div class="actions">
          ${zipLink}
          ${manifestLink}
          <a class="secondary" href="https://github.com/openviglet/dumont" target="_blank" rel="noopener">
            GitHub
          </a>
        </div>
      </div>
    </section>
    <main>
      <article class="markdown-body">
${bodyHtml}
      </article>
    </main>
    <footer>
      <span>Viglet Dumont DEP — Data Exchange Platform</span>
      <span>
        <a href="https://github.com/openviglet/dumont/issues">Report an issue</a>
        &nbsp;·&nbsp;
        <a href="https://github.com/openviglet/dumont/discussions">Discussions</a>
      </span>
    </footer>
    <script src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/highlight.min.js"></script>
    <script>hljs.highlightAll();</script>
  </body>
</html>
`;

writeFileSync(outputPath, html, "utf8");
console.log(`Rendered: ${inputPath} → ${outputPath}`);

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
