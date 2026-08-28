# Dumont: Data Exchange Platform

## Development Guidelines

### Roadmap & docs maintenance

The roadmap lives in `docs/` and follows the same model as the sibling Turing docs.
Each file has one job — keep them in sync and never duplicate content:

| File | Single responsibility |
|------|-----------------------|
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | **Active backlog only** — pending `D##` tasks with deps + "done" criteria. The source of truth for what's *not yet* shipped. |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | **Shipped ledger** — one terse line per shipped task (what + where + commit). `git log` is the authoritative detail. |
| [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) | Design rationale (what & why) for *unshipped* tasks only. |
| [`docs/STRATEGY.md`](docs/STRATEGY.md) | Positioning, moat, bets, non-goals. |

**When a task ships (always do this on completion):**
1. **Move** its entry from `ROADMAP.md` → `CHANGELOG.md` as a one-liner under its
   track, referencing the commit. **Delete** the task from `ROADMAP.md` — shipped
   work is not left there, even checked off. Stable `D##` IDs are never reused, so
   `↳ Depends on:` lines on other tasks still resolve via the CHANGELOG.
2. **Remove** the task's design subsection from `IMPROVEMENTS.md` — do not leave a
   shipped implementation report there or in `ROADMAP.md` (`git log` is the history).
3. Keep entries terse: implementation detail belongs in code/commits, not the docs.

### Project Structure
- **connector/connector-app**: Spring Boot 4 backend (Java 21), main application — runs on port 30130
- **connector/connector-commons**: Connector API interfaces and common classes
- **dumont-react**: React + TypeScript frontend (Vite, Tailwind CSS v4, shadcn/ui, Module Federation)
- **commons**: Shared utilities (caching, logging, file handling, exceptions)
- **spring**: Spring utilities (UUID generation, persistence, JPA helpers)
- **aem**: Adobe Experience Manager plugin + sample
- **assets**: Assets/file management plugin
- **db**: Database (JDBC) connector plugin
- **web-crawler**: Web crawler plugin
- **filesystem**: Filesystem connector (fs-commons + fs-connector)
- Root Maven POM is multi-module

### Key Paths
- Backend source: `connector/connector-app/src/main/java/com/viglet/dumont/connector/`
- Backend config: `connector/connector-app/src/main/resources/application.yaml`
- Frontend source: `dumont-react/src/`
- Frontend pages: `dumont-react/src/pages/`
- Frontend components: `dumont-react/src/components/`
- Frontend i18n: `dumont-react/src/i18n/locales/{en,pt}/`
- Frontend routes: `dumont-react/src/DumontRoutes.tsx`
- Frontend sidebar: `dumont-react/src/components/app-sidebar.tsx`
- Sample configs: `scripts/config/dumont-connector.properties`, `aem/aem-plugin-sample/`

### Build & Test Commands
```bash
# Backend build
mvn clean install

# Backend build (skip tests)
mvn clean install -DskipTests

# Frontend compile
cd dumont-react && npm run compile

# Frontend dev server
cd dumont-react && npm run dev
```
> **Windows PowerShell**: quote `-D` flags, e.g. `"-DskipTests"`

### Code Quality (SonarQube)
Dumont **reuses the SonarQube Docker stack that lives in the Turing repo** — there is
no separate stack here. Local analysis runs against `http://localhost:9100` under the
project key `viglet_dumont` (distinct from Turing's `viglet_turing`).

- **Premise**: the SonarQube instance is assumed to be **already up**. If not, start it
  once from the Turing repo: `docker compose -f containers/sonarqube/docker-compose.yaml up -d`.
- `scripts/sonar/sonar-scan.sh` — bootstraps an analysis token (cached in
  `scripts/sonar/.sonar-token`, git-ignored) and runs `mvn compile sonar:sonar` from the
  reactor root (all backend modules, frontend build skipped). Env: `SONAR_URL`,
  `SONAR_ADMIN_PASSWORD` (default `TuringSonar@2026`), `SONAR_GOAL` (`verify` for coverage).
- `scripts/sonar/sonar-issues.sh` — lists open issues via the Web API (rule breakdown by
  default; `SONAR_RULE=<rule>` or `SONAR_LIST=1` for detail).
- Root `pom.xml` carries `sonar.projectKey=viglet_dumont` and CPD exclusions for
  `**/persistence/{dto,model}/**`. See `scripts/sonar/README.md`.

### Backend Conventions
- **Spring Boot 4.0.5** with **Java 21**
- Persistence: JPA/Hibernate with H2 (default), PostgreSQL optional
- REST controllers in `api/` package with `@CrossOrigin(origins = "*")`
- Use constructor injection (no `@Autowired` on fields)
- Records for DTOs/API response types
- Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`) for model classes
- All API endpoints under `/api/v2/connector/` prefix
- Spring profiles: `production` profile enables security configuration

### API Authentication (Key Filter)
- `DumApiKeyFilter` intercepts all `/api/**` requests
- Checks `Key` header (or `Key` query parameter as fallback) against `turing.apiKey` property
- Returns HTTP **422** (not 401) on mismatch to avoid Turing redirecting to login page
- Registered in `DumWebConfiguration` Spring Security filter chain

### API Endpoints
| Prefix | Controller | Description |
|--------|-----------|-------------|
| `/api/v2/connector/` | `DumConnectorApi` | Index, reindex, audit operations |
| `/api/v2/connector/monitoring/indexing` | `DumConnectorMonitoringApi` | Monitoring, stats, search |
| `/api/v2/connector/indexing-rule` | `DumConnectorIndexingRuleAPI` | CRUD for indexing rules |
| `/api/v2/connector/system-info` | `DumConnectorSystemInfoApi` | System info + variables |
| `/api/v2/connector/summary` | `DumConnectorSummaryApi` | AI-powered connector insights |
| `/api/v2/aem/` | `DumAemApi` | AEM-specific operations |
| `/api/v2/db/source` | `DumDbSourceApi` | Database source CRUD + index |
| `/api/v2/wc/source` | `DumWCSourceApi` | Web crawler source CRUD + crawl |
| `/api/v2/assets/source` | `DumAssetsSourceApi` | Assets source CRUD + index |

### Indexing Plugins (Conditional Loading)
Three indexing backends, selected via `dumont.indexing.provider` property:
- **turing** (default): `DumTuringIndexingPlugin` — uses Turing Java SDK, sends API Key via RestClient
- **solr**: `DumSolrIndexingPlugin` — direct Solr calls
- **elasticsearch**: `DumElasticsearchIndexingPlugin` — direct ES calls

All implement `DumIndexingPlugin` interface, loaded via `@ConditionalOnProperty`.

### RestClient Configuration
- `DumRestClientConfig` creates a `RestClient` bean configured with:
  - Base URL: `turing.url` property
  - Default header: `Key: {turing.apiKey}`
- Used for all outbound calls to Turing (indexing, summary generation)

### Key Properties (`application.yaml`)
| Property | Default | Purpose |
|----------|---------|---------|
| `server.port` | 30130 | Application port |
| `turing.url` | http://localhost:2700 | Turing ES URL |
| `turing.apiKey` | (required) | API Key for Turing + incoming request auth |
| `dumont.indexing.provider` | turing | Search backend: turing, solr, elasticsearch |
| `dumont.allowedOrigins` | localhost:5173,localhost:2700 | CORS origins |
| `dumont.job.size` | 50 | Items per batch |
| `dumont.cron` | - | Scheduled job cron |
| `dumont.scheduler.enabled` | false | Enable scheduler |
| `dumont.reactive.indexing` | false | Enable reactive indexing |

### System Info Endpoint
- `GET /api/v2/connector/system-info` — returns app version, Java, OS, memory, disk, indexing config
- `GET /api/v2/connector/system-info/variables` — returns Java system properties + env vars (prefixed `env.`)
- Indexing config uses `PropertyEntry` format: `{ "value": "...", "property": "turing.url" }` for frontend display

### AI Summary / Insights Integration
- `GET /api/v2/connector/summary` — collects connector data (version, memory, disk, sources, indexing stats) and proxies to Turing's `POST /api/v2/summary` endpoint
- Turing's `TurLlmSummaryService` handles LLM call, caching (respects Global Settings: `llmCacheEnabled`, `llmCacheTtlMs`, `llmCacheRegenerate`)
- Response: `{ success, error, content (markdown), canRegenerate }`
- `?regenerate=true` param evicts cache and forces fresh LLM call

### Message Queue
- Apache ActiveMQ Artemis (embedded mode)
- Queue: `connector-indexing.queue`
- Used for async indexing operations

---

## Frontend Conventions

### Stack
- **React 19** + **TypeScript** + **Vite**
- **Tailwind CSS v4** with `@tailwindcss/typography`
- **shadcn/ui** components in `src/components/ui/`
- **Module Federation** — dumont-react is mounted inside turing-react as a micro-frontend
- Icons: `@tabler/icons-react`
- Routing: `react-router-dom` — all routes in `DumontRoutes.tsx`
- i18n: `i18next` + `react-i18next` (EN + PT)
- Toast notifications: `sonner` library
- HTTP client: `axios`

### Axios Interceptors (`main.tsx`)
- **Request interceptor**: Rewrites service URLs for standalone mode
  - `/v2/integration/{id}/aem/source` → `/api/v2/aem/source`
  - Adds `/api` prefix to all relative URLs
- **Response interceptor**: Catches HTTP 422 globally and shows API Key mismatch toast

### Standalone Mode
The dumont-react app runs both inside Turing (via Module Federation) and standalone:
- Standalone uses a fixed `STANDALONE_ID = "local"` for the integration ID
- The axios request interceptor strips the integration path segment

### Routing — two trees, both serving
Console surfaces are migrating to bento chrome one page at a time, so there are two route trees
and both answer. A page moves by being added to the second; its console route is untouched, which
is what lets it move back.

| Tree | Router | Mounted at | Chrome |
|---|---|---|---|
| Console | `DumontRoutes.tsx` | `/admin/integration/instance/:id/*` | `ConsoleRootPage` — `SidebarProvider` + collapsible sidebar |
| Bento | `BentoRoutes.tsx` | `/bento/integration/instance/:id/*` | `BentoRootPage` — fixed nav rail, `⌘K` palette, no provider |

- A route belongs in `BentoRoutes.tsx` only once its page has actually moved — the tree must never
  advertise a surface it does not serve.
- **Every surface in `DUMONT_NAV` has moved** — all seventeen, across both sections, so a reader who
  enters the shell stays in it whatever they click. What is still console-only is the surrounding
  app — login, setup, console home, the six admin-settings pages, the three user ones.
  `pnpm run census` prints which page is on which era, and the console tree is untouched — every
  migrated surface still serves on it, which is what keeps each step reversible.
- Both are exposed over Module Federation (`./DumontRoutes`, `./BentoRoutes`); no turing-react
  change is needed when adding a page to either.
- **`ROUTES.INTEGRATION_INSTANCE` derives its prefix from the live URL.** From inside the bento
  shell it resolves to `/bento/…`, so a link to a not-yet-migrated page must use
  `ROUTES.CONSOLE_INTEGRATION_INSTANCE` instead, or it points at a route nothing serves.
- **A link is only as migrated as the route that answers it**, and this was the migration's one
  recurring defect — four instances (D59 twice, D60 twice). Two shapes: a route built with the
  console prefix spelled out by hand, which leaves the shell; and a route resolved correctly into
  a tree that declares nothing matching it, which renders a blank page. Neither the census nor the
  nav tests read links, so the second kind fails silently — the AEM model and specification
  editors were dead for three tasks. **When a page moves, walk every route it can construct**,
  including the ones no nav entry declares.

### Navigation (`src/app/nav.const.ts`)
One declaration, read by three surfaces — the console sidebar, the bento rail and the `⌘K`
palette. Written in the design system's `BentoNavItem` schema plus the two fields the console
needs, so no reader keeps a second list that drifts on a rename.

- `DUMONT_NAV` — every surface: `id`, `titleKey`, `descriptionKey`, `iconName`, `section`, `tone`,
  `path`, plus `provider` (visibility) and `order` (sidebar).
- `visibleNavSurfaces(providers)` — the shared filter *and* sort. Every reader calls it, so they
  cannot disagree. Providers come from `usePlugin()`.
- `dumontNavItems(id)` / `dumontNavGroups(id)` — the flat and grouped derivations for palette and
  rail. `migrated: true` is what gives a surface a `bentoRoute`; everything else falls back to the
  console.
- `manifest.navItems` is **derived** from `DUMONT_NAV` and keeps its wire shape — the Turing host
  reads it over `./manifest`, so that shape is a contract.
- The rail renders only sections carrying an `areaRoute`. Dumont has no hub pages yet, so it shows
  Home alone; the palette needs no hub and reaches everything today.

### i18n Structure
```
src/i18n/locales/
├── en/
│   ├── common.json      — Common UI strings, systemInfo keys
│   ├── dialog.json      — Dialog strings
│   ├── forms.json       — Form validation & submission
│   ├── bento.json       — Bento chrome this shell owns (skip link, main landmark, migration home)
│   └── integration.json — Integration/connector-specific (sources, nav, insights, systemInfo)
└── pt/
    ├── common.json
    ├── dialog.json
    ├── forms.json
    ├── bento.json
    └── integration.json
```
All keys use dot notation: `integration.sources.indexAll`, `common.apiKeyMismatch`, `systemInfo.overview`

**The package is the base; Dumont merges over it.** `src/i18n/index.ts` initialises i18next with
`vigDesignSystemTranslations` as the resources and then `addResourceBundle(..., deep, overwrite)`
for Dumont's own. Do **not** go back to `registerVigTranslations`: it skips any namespace whose
*top-level* key the app already declares, which silently dropped five of the package's nine
bundles (`common`, `forms`, `dialog`, `sidebar`, `theme`) in both languages. Nothing failed —
every string in the layer has a `defaultValue`, so the chrome just rendered English inside a
Portuguese console.

- Dumont's wording still wins any conflict; the package only fills gaps.
- `bento.json` carries only this shell's own keys and must be **merged**, never spread into a base
  bundle — the other ~20 `bento.*` keys belong to the package.
- `test/bento-i18n.test.ts` is the gate. It asserts the Portuguese **differs** from the English,
  because a `defaultValue` always resolves and "the key resolves" would prove nothing.

### Design System (`@viglet/viglet-design-system`)

Shared with Turing and Shio. Two entry points matter here:

- **root / `./router`** — console-era chrome. Most of `components/ui/*` are one-line re-export
  shims over it.
- **`./bento`** — the newer layer, plus `./bento.css` (imported once in `src/index.css`, after the
  console stylesheet so a migrated surface gets the bento value of a shared property).

**Never re-declare a component the package exports.** `pnpm run lint:ds`
(`viglet-ds-check-duplicates`) reads `exports.json` from the installed version and fails the
build, naming the import that replaces the copy; it runs on push and PR via
`.github/workflows/design-system.yml`. A deliberate collision is annotated at its declaration with
the reason:

```ts
// viglet-ds-allow-duplicate AppFooter -- renders Dumont's own logo, connector version and links
```

Eleven names are annotated today — all bound to something only Dumont has (its auth service,
`TurGridItem`, its breadcrumb context, `__DUMONT_STANDALONE__`). The gate exists to catch the
*next* one.

Bento notes: `BentoUserMenu` reads the **package's** user context, not `@/contexts/user.context` —
`BentoRootPage` wraps its shell in the package's `UserProvider` (a component never sees a context
it mounts itself). `BentoListPage` takes its layout as a prop rather than fetching one.
`docs/BENTO-AUTHORING.md` in the design system repo is the contract for writing a bento page.

### Migrating a page (D46, D47)

**A list page** is one `BentoEntityList` call (`pages/bento/bento-entity-list.tsx`): a service,
two field accessors, a translation prefix. Path, icon and tone come from `DUMONT_NAV` by id.

**A detail page is never rewritten.** Its form is ~500 lines of field logic whose only
era-specific parts are the header and the section cards, so the form is *shared* and those two
read the chrome. Forking it would leave two copies drifting for the length of the migration.

```tsx
// The bento page — the console page under a chrome declaration.
<SectionCardChromeProvider chrome="bento"><IntegrationInstanceDbSourcePage /></SectionCardChromeProvider>
```

**A page that is not a form** — a wizard, a panel, a table — takes the same shape with two other
adapters, both in `components/bento/`:

- `AdaptivePageHeader` → `SubPageHeader` or `BentoHero`. No save bar, because there is nothing to
  save; the package's own deprecation notice on `SubPageHeader` names `BentoHero` as its
  replacement, which is what this picks between.
- `AdaptivePanel` → `Card` + `CardContent` or `BentoPanel` — a container with no heading, because
  the header above already names the page. **Always pass `contentClassName` explicitly**: the two
  eras disagree on the default inset (`BentoPanel`'s content wrapper has none, `CardContent`'s is
  `px-6`), so naming it is what makes them render the same.

- `AdaptiveFormHeader` (`components/bento/`) → `StickyPageHeader` or `BentoFormHero`. Declarative,
  not a passthrough: the console header takes Save/Cancel as children, while `BentoFormHero` owns
  **both halves of the save-bar morph** and must never be handed a second pair. Secondary actions
  go through `menuActions` (→ `StickyPageHeader.Action` or `BentoActionsMenu`); destructive ones
  belong there, never in the save bar.
- `AdaptiveSectionCard as SectionCard` → collapsible card or frosted section. Same markup.
- Both default to `console`, so a form that knows nothing about this is unchanged.
- Then set `migrated: true` in `nav.const.ts` — that is what mints the `bentoRoute` the palette
  and hubs use to stay inside the shell.

**The census reads a page through its local imports**, both for era and for the console chrome it
renders — a page renders most of its chrome through components, and reading the page file alone
under-counted `Card` by 17 and reported a live `StickySaveBar` as dead (D51). Three rules keep the
era half honest: a page is bento-era if it reaches a bento import through its own local modules; a
**type-only** import never counts (it is erased and renders nothing); and a module reading
`useSectionChrome` is an *adapter* the walk does not follow, because it renders whichever era it
is placed in. `chrome="bento"` is itself the strongest era statement. Adding a bento page means
adding it to `ON_BENTO` in `test/console-census.test.ts`.

### Build and lint gates (Track K)

| Command | Gate |
|---|---|
| `pnpm run lint` | ESLint, **no exemptions**. It arrived on an unlinted console with 105 problems; three rules carried the debt as named files in `eslint.config.js` until D53, D54 and D56 emptied each list, which was then deleted rather than kept at zero. If a rule needs that treatment again: name the files and say why at each one, never a ceiling — a ceiling lets one change pay for another. One genuine one-off is an `eslint-disable-next-line` with its reason, in `use-grid-adapter.ts`. |
| `pnpm run lint:ds` | No component re-declares a design system export (D41). |
| `pnpm run census` | The migration count. Exits non-zero on a broken read; warns on stderr when a chrome probe matches nothing. |
| `pnpm run compile:build` | `tsc -b`, the Vite build, then `scripts/check-mf-types.mjs`. |

**Test timeouts (D77).** `vitest.config.ts` sets `testTimeout: 10_000` for a reason — a page test
whose service call never resolves should fail rather than hang the run. A file that spawns real
tools cannot live inside that budget, and `eslint-gate.test.ts` is the one that does: six ESLint
processes, the first of which measured 18.7s cold and failed the gate on the clock rather than on
an assertion, which reads as "the lint gate is broken". **Override in the file, never in the
config** — `vi.setConfig({ testTimeout, hookTimeout })` scopes it, where raising the global bound
would spend every other test's hang detection to pay for one file. And pay a known cold start in a
`beforeAll` rather than leaving it on whichever case runs first, so the per-case timings stay
meaningful and a case that blows its budget is one that earned it.

**Federated type declarations (D49).** They emit to `dumont-react/mf-types/`, deliberately outside
`build.outDir` — the Vite output directory is the connector's `public/` and `emptyOutDir` wipes it
*after* the generator writes there. The DTS build extends `tsconfig.app.json`, not the root
`tsconfig.json`, which is a solution file carrying no `jsx`. Do not trust the generator's exit
code: it has printed `#TYPE-001` and exited 0, and written the wrong layout and exited 0. The
check inspects the artefact.

CI: `validate.yml` (Maven build every push and PR, Sonar on pushes) and `design-system.yml`
(duplicates + ESLint, no build — the Maven one already covers it).

**Effects.** `react-hooks/set-state-in-effect` is an error, and the rule cannot see through a
`useCallback` — it reads any call reaching `setState` as synchronous. Two shapes satisfy it
honestly, and both were used to clear the twenty-five this console had:

- **Derive it.** Most of them were not state at all: `isNew` is a fact about the route, a blank
  entity is a value the render computes, a tab is what the URL says.
- **Store the answer against the question.** A result keyed by the query that produced it makes
  "loading" derivable and the resets unnecessary — an answer to another query is not this one's.
  This also removes stale-render bugs, which is how it found two.

`void (async () => { await load() })()` is the accepted way to call a loader from an effect. Do
**not** wrap a synchronous write in an IIFE to quiet the rule: the write is still synchronous, and
that is a loophole rather than a fix.

**Dependency arrays.** `react-hooks/exhaustive-deps` is an error too. When completing one is
impossible, the array is not what is wrong: a function rebuilt every render needs `useCallback`, a
value built inline needs `useMemo`, and a helper closing over nothing belongs at module scope.
D56 found a list page constructing its service inline on every render that way — an honest array
would have refetched forever, and the warning had been standing in front of it.

### Shared Components

#### `GradientButton` (`components/ui/gradient-button.tsx`)
- Variants: `default`, `secondary`, `destructive`, `success`, `outline`, `ghost`
- Sizes: `default`, `sm`, `lg`, `icon`, `icon-sm`, `icon-lg`
- Props: `loading` (shows spinner + disables), `to` (NavLink), `asChild`
- Blue→indigo gradient for default variant

#### `AiSummaryPanel` (`components/ai-summary-panel.tsx`)
**Shared abstraction** — identical component exists in both Turing and Dumont React.
- Props: `endpoint` (API URL), `i18nPrefix` (translation key prefix)
- Manages: loading skeleton, error display, markdown content rendering, regenerate button
- Uses: `react-markdown` + `remark-gfm` + `rehype-highlight`
- Regenerate button only shown when `canRegenerate = true` in API response

#### `SectionCard` (`components/ui/section-card.tsx`)
- Color variants: `blue`, `violet`, `emerald`, `amber`, `rose`, `cyan`, `orange`, `slate`
- Sub-components: `SectionCard.Header` (collapsible), `SectionCard.StaticHeader`, `SectionCard.Content`

### Page Patterns

#### Source Pages (AEM, DB, Assets, WC)
- Action buttons (Index All, Reindex All, Crawl, Dry Scan) use `loading` state
- Loading state set before async call, cleared in `finally` block
- 409 Conflict → warning toast ("already running")
- 422 → global interceptor shows API Key mismatch

#### System Info Page (`integration.instance.system-info.page.tsx`)
- Two tabs: **Overview** and **System Variables**
- Overview: Application card, Indexing Provider card (with property keys), Physical RAM, JVM Heap, Disk
- Variables: searchable table of Java system properties + env vars
- Fetches both `/system-info` and `/system-info/variables` in parallel

#### AI Insights Page (`integration.instance.insights.page.tsx`)
- Uses `AiSummaryPanel` component with connector summary endpoint
- Sidebar entry with `IconSparkles` icon

### Visual Identity
- **Product name**: Viglet Dumont DEP (Data Exchange Platform)
- **Primary gradient**: Same as Turing — `blue-600 → indigo-600`
- **Logo**: `DumontLogo` component
- **Icons**: `@tabler/icons-react`

---

## Integration with Turing

### Communication Flow
```
Dumont Connector ──RestClient──▶ Turing ES (/api/v2/*)
     │                              │
     │ Key: {turing.apiKey}         │ Validates Key header
     │                              │
     ▼                              ▼
 Indexing Jobs               Search Engine (Solr)
 AI Summary                  LLM (via Spring AI)
 Global Settings             Cache Service
```

### Shared Backend Abstractions (Turing side)

#### `TurLlmSummaryService` (`turing-app/.../system/TurLlmSummaryService.java`)
Extracted service that encapsulates LLM summary generation with cache:
- Validates default LLM configuration
- Checks/uses `TurLlmCacheService` (in-memory ConcurrentHashMap with TTL)
- Calls LLM via Spring AI `ChatModel`
- Returns `SummaryResult { success, error, content, canRegenerate }`
- Used by: `TurSNSiteSummaryAPI` (SN Insights) and `TurSummaryAPI` (generic endpoint)

#### `TurSummaryAPI` (`turing-app/.../api/system/TurSummaryAPI.java`)
- `POST /api/v2/summary` — generic endpoint accepting `{ cacheKey, data, systemPrompt }`
- Called by Dumont's `DumConnectorSummaryApi` via RestClient
- Any external system can use this for LLM-powered summaries

### Dumont React as Module Federation Remote
- Mounted inside turing-react via `@module-federation/vite`
- All routes scoped under integration instance path
- Host (turing-react) mounts via single wildcard route — no changes needed when adding Dumont pages

---

## Common Pitfalls
- HTTP 401 from Dumont causes Turing to redirect to login — use **422** for API Key mismatch
- `@CrossOrigin(origins = "*")` on controllers allows broad access; CORS for `/api/**` is also configured in `DumStaticResourceConfiguration`
- Frontend npm builds may fail with EBUSY on Windows if IDE locks files — use `-DskipTests` for backend-only testing
- The `Key` header name is literal (not `Authorization`) — matches `DumRestClientConfig.KEY` constant
- System info `PropertyEntry` format `{ value, property }` only applies to indexing config fields, not to regular `InfoRow` calls
- `GradientButton` `loading` prop automatically disables the button — don't pass both `loading={true}` and `disabled={true}`

---

## Deployment

### Default Configuration
- Port: 30130
- Database: H2 embedded (`./store/db/dumontDB`)
- Queue: Artemis embedded (`store/queue/`)
- Logs: `store/logs/dum-connector.log`

### External Configuration
Override via `dumont-connector.properties` file or environment variables. Sample at `scripts/config/dumont-connector.properties`.
