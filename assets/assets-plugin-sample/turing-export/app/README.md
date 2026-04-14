# Celestial Gallery — A Telescope Pointed at the Cosmos

> *"Explore the wonders of the cosmos. From nebulae to black holes, search the cosmos… nebula, galaxy, Mars…"*

**Celestial Gallery** is the showcase SPA for the **Dumont Assets Connector**. It indexes a local filesystem full of astrophotography — FITS images from research telescopes, TIFF masters, JPEG previews — and serves them up as a gorgeous, filterable cosmic archive.

![Assets Connector](https://img.shields.io/badge/connector-Assets-8b5cf6?style=flat-square)
![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square)
![FITS Ready](https://img.shields.io/badge/format-FITS%20%7C%20TIFF%20%7C%20PNG%20%7C%20JPG-06b6d4?style=flat-square)

---

## What is this?

Imagine a Hubble-grade observatory archive — thousands of photographs of nebulae, galaxies, eclipses and planetary surfaces — sitting quietly on a file share. The Dumont Assets Connector walks the filesystem, extracts metadata, computes file size and extension, and ships it all to Turing. **This SPA** is the public face of that archive: a violet-and-cobalt gallery where every tile is a window into deep space.

## Highlights

- 🔭 **Cinematic hero** with an animated starfield and the tagline *"Explore the wonders of the cosmos."*
- 🖼️ **Asset cards with extension badges** — violet for TIFF, blue for PNG, cyan for FITS, amber for JPG — instantly readable at a glance.
- 📊 **Human-readable file sizes** (B / KB / MB / GB / TB) formatted client-side with zero dependencies.
- 🌌 **Facet navigation** ("Browse By…") lets users narrow by format, mission, celestial object, or any custom taxonomy your filesystem exposes.
- 🔎 **Smart search** with placeholder hints: *"nebula, galaxy, Mars…"*
- 🌓 **Dark-mode-first design** — because you don't browse the universe under fluorescent lights.

## What's indexed?

Whatever your telescope — or your `/opt/celestial-gallery` directory — can throw at it:

| File type | Typical payload |
|-----------|-----------------|
| `.fits`   | Raw telescope data cubes |
| `.tiff`   | High-dynamic-range masters |
| `.png` / `.jpg` | Web-ready previews |
| Sidecar metadata | EXIF, IPTC, custom XML |

The sample configuration (`scripts/sample/export/sample-assets.json`) maps the local path `/opt/celestial-gallery` to the public URL `https://gallery.example.com/celestial`, so your users get clickable links to the actual image files.

## Architecture at a glance

```
  Filesystem (/opt/celestial-gallery)
           │
           ▼
  fs-connector ──► Assets Plugin ──► Dumont Connector ──► Turing ES ──► Celestial Gallery SPA
                                                                              │
                                                                              ▼
                                                                      You, stargazing
                                                                      through your browser
```

## Deploy in three steps

1. Grab the zip from the **Dumont Marketplace**.
2. Upload it to a Turing SN Site called `celestial-gallery`.
3. Create an Assets source pointing to any folder of images and hit *Index All*.

Within seconds, your files become a fully searchable observatory.

## Tech Stack

React 19 · TypeScript · Vite · Tailwind CSS v4 · shadcn/ui · Tabler Icons (yes, there's a telescope icon) · Turing React SDK

---

**Transmission lost?** File an issue at [openviglet/dumont](https://github.com/openviglet/dumont/issues) — mission control is standing by.
