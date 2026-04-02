# Dumont: Data Exchange Platform

## Development Guidelines

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

### Routing
- All routes defined in `DumontRoutes.tsx`
- Route structure: `/{feature}` and `/{feature}/:sourceId` and `/{feature}/:sourceId/:tab`
- No changes needed in turing-react when adding new dumont pages

### Sidebar (`app-sidebar.tsx`)
- Nav items grouped by connector type: `aemSourceItems`, `dbSourceItems`, `wcSourceItems`, `assetsSourceItems`, `commonItems`
- Visibility controlled by `usePlugin()` provider context
- Items use `SidebarNavItem` interface: `{ key, titleKey, url, icon }`

### i18n Structure
```
src/i18n/locales/
├── en/
│   ├── common.json      — Common UI strings, systemInfo keys
│   ├── dialog.json      — Dialog strings
│   ├── forms.json       — Form validation & submission
│   └── integration.json — Integration/connector-specific (sources, nav, insights, systemInfo)
└── pt/
    ├── common.json
    ├── dialog.json
    ├── forms.json
    └── integration.json
```
All keys use dot notation: `integration.sources.indexAll`, `common.apiKeyMismatch`, `systemInfo.overview`

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
