# Dumont AEM Plugin Sample

A working, end-to-end example of a Dumont AEM connector plugin built on the
**structured-source architecture**. It shows how a plugin author declares a typed,
per-content-type schema and a set of composable extractors for Adobe Experience Manager
(AEM), packaged as a [PF4J](https://pf4j.org) plugin and indexed into Viglet Dumont.

## How it fits the structured-source architecture

Dumont indexes a *structured source* with one generic loop — **discover → extract → ground →
publish** — implemented once by the framework (`DumStructuredSourcePlugin`). For AEM that loop
is driven by the framework's `DumAemStructuredSource` adapter: it discovers content paths,
fetches each `infinity.json`, and turns it into a schema-bound `DumStructuredRecord` that the
loop grounds (mandatory-field check, no fabricated values) and publishes to Turing.

A plugin author **does not** re-implement that loop. Instead this sample supplies only the
AEM-specific pieces the adapter calls into:

- **The schema** — declared as typed fields, grouped into **content types**.
- **The extraction logic** — small **component extractors** that read AEM `model.json` and
  contribute attribute values, composed per content type.
- **Per-source hooks** — delta date (incremental indexing) and dependency selection.

Because the schema is declared as code, every published document carries its field spec, and
the framework enforces grounding for free.

## What this sample includes

### Content types & schema (`DumAemExtSampleModelJson`)

`DumAemExtSampleModelJson` extends `DumAemExtModelJsonBase<DumAemSampleModel>` and is the
entry point for `model.json` extraction. It declares two **content types** — each owning its
own schema (typed `DumAemContentTypeField`s) and its own extractor composition:

| Content type | Mandatory | Notable fields | Extractors |
|--------------|-----------|----------------|------------|
| `article`    | `pageTitle` | description, language, template, paragraphs, texts | Page, Paragraph, Text |
| `event`      | `pageTitle` | description, template, images, content-fragment titles/models/elements | Page, Image, ContentFragment |

AEM templates are routed to a content type through the alias map in `templateNameAliases()`
(`news`/`blog`/`article` → `article`, `event` → `event`). `extract(id)` runs only the matched
type's extractor composition and binds the record to that type's schema. A page whose template
maps to no declared type falls back to the flat `componentExtractors()` list.

### Component extractors (`DumAemComponentExtractor`)

Each extractor handles a single concern and contributes to the shared `DumAemAttrMap`. They
all walk the same nested `:items` tree via the `DumAemSampleItems` helper (recursion +
HTML-stripping), so each stays a small single-responsibility unit:

- `DumAemSamplePageExtractor` — page-level fields (title, description, language, template,
  last-modified) from the model root.
- `DumAemSampleParagraphExtractor` — HTML-stripped paragraphs from every item.
- `DumAemSampleTextExtractor` — HTML-stripped `text` from every item.
- `DumAemSampleImageExtractor` — non-blank image `src` values.
- `DumAemSampleContentFragmentExtractor` — titles, models, and element values from
  `contentfragment` items.

### Per-source hooks

- `DumAemExtSampleDeltaDate` (`DumAemExtDeltaDateInterface`) — supplies the delta date used for
  incremental (delta) indexing; here, the AEM last-modified timestamp.
- `DumAemExtSampleDependency` (`DumAemExtDependencyInterface`) — a business rule deciding whether
  a page's `/content` dependencies are collected and reverse-indexed; here, FAQ pages opt out.
- `DumAemExtSampleContentTitle` (`DumAemExtAttributeInterface`) — a per-attribute extractor
  wired by `className` in the source config, illustrating the attribute-level extension point.

### Data model (`DumAemSampleModel`)

A Jackson-mapped bean for AEM `model.json`: page-level properties plus the nested
`:items` / `:elements` tree the component extractors traverse.

### Configuration & scripts

A pre-configured WKND source (`scripts/wknd/export/wknd.json`) and build/run scripts
(`compile-and-run.cmd`, `run.cmd`, Ant `build.xml`) that package the plugin and launch the
connector.

## Prerequisites

1. **Java 21+** ☕
2. **Maven 3.6+** 📦
3. **Apache Ant** (for the build scripts) 🐜
4. **Adobe Experience Manager 6.5+** running locally or remotely
5. **Viglet Dumont** server running and accessible
6. **AEM WKND Sample Site** (recommended for testing)

## Quick start

### 1. Build and run

```bash
# From the sample directory
cd aem/aem-plugin-sample

# Clean, compile, package, copy JARs to dist/, and launch the connector
compile-and-run.cmd
```

### 2. Manual build

```bash
# Build the sample plugin
./mvnw clean install package

# Assemble the distribution (creates dist/wknd)
ant wknd

# Run the connector
cd dist/wknd
run.cmd
```

## Configuration

### AEM source configuration

The sample ships a pre-configured WKND source in `scripts/wknd/export/wknd.json`. Key fields:

| Field | Purpose | Sample value |
|-------|---------|--------------|
| `endpoint` | AEM instance URL | `http://localhost:4502` |
| `username` / `password` | AEM credentials | `admin` / `admin` |
| `rootPath` | Content root to discover | `/content/wknd` |
| `contentType` | JCR type to index | `cq:Page` |
| `authorSNSite` / `publishSNSite` | Target Turing SN sites | `wknd-author` / `wknd-publish` |
| `deltaClass` | Delta-date extension | `…sample.ext.DumAemExtSampleDeltaDate` |
| `dependencyClass` | Dependency-selection extension | `…sample.ext.DumAemExtSampleDependency` |
| `models[].className` | `model.json` extractor (content types) | `…sample.ext.DumAemExtSampleModelJson` |

The `attributes` block declares the source-level field specs (id, title, tags, type, dates,
site, url, …). The framework builds the published schema as the **union** of these specs and
the fields declared by the content types, so both contribute to the schema that travels with
every document.

## Creating your own plugin

Use this sample as the template:

1. **Model your `model.json`** as a Jackson bean (cf. `DumAemSampleModel`).
2. **Declare content types** by extending `DumAemExtModelJsonBase<YourModel>`: list each
   `DumAemContentType` with its `DumAemContentTypeField`s and its extractor composition, and
   map your AEM templates to those types in `templateNameAliases()`.
3. **Write component extractors** implementing `DumAemComponentExtractor<YourModel>`, one per
   concern, each contributing to the `DumAemAttrMap`.
4. **(Optional) add per-source hooks** — `DumAemExtDeltaDateInterface` for delta indexing,
   `DumAemExtDependencyInterface` for dependency selection, `DumAemExtAttributeInterface` for
   attribute-level extraction.
5. **Package as a PF4J plugin** — the `Plugin-Class` manifest entry points at your `Plugin`
   subclass (cf. `DumAemSamplePlugin`); `@Extension`-annotated classes are auto-registered.
6. **Reference your classes** from the source config (`deltaClass`, `dependencyClass`,
   `models[].className`, attribute `className`).

## Troubleshooting

**Connection refused to AEM** — verify AEM is running at the configured `endpoint` and the
credentials are correct.

**Dumont API key invalid** — ensure the API key is correct and the Dumont server is running.

**No content indexed** — check that `rootPath` exists in AEM and contains pages of the
configured `contentType`.

**A record is skipped** — the framework grounds every record: a record missing a content
type's **mandatory** field (e.g. `pageTitle`) is logged and skipped rather than published with
a fabricated value.

**Build fails** — ensure Java 21+ is installed and `JAVA_HOME` is set.

Enable debug logging with:

```bash
-Dlogging.level.com.viglet.dumont=DEBUG
```

## Support

- 📋 [GitHub Discussions](https://github.com/openviglet/dumont/discussions)
- 🐛 [Report Issues](https://github.com/openviglet/dumont/issues)
- 📖 [Main Documentation](../../README.adoc)
