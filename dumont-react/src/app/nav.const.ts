import { ROUTES } from "@/app/routes.const";
import {
  IconActivityHeartbeat,
  IconAdjustmentsSearch,
  IconBuildingBank,
  IconChartBar,
  IconCloudComputing,
  IconDatabase,
  IconFolder,
  IconGitCommit,
  IconGlobe,
  IconGraph,
  IconInfoCircle,
  IconListCheck,
  IconPlugConnected,
  IconPuzzle,
  IconSparkles,
  IconTools,
  IconWand,
  type Icon,
} from "@tabler/icons-react";
import type {
  BentoNavGroup,
  BentoNavItem,
  BentoNavSection,
  BentoTone,
} from "@viglet/viglet-design-system/bento";

/*
 * D39 — Dumont's map of itself, written down once.
 *
 * Three things read what surfaces exist: the console sidebar today, and the bento rail and the
 * `⌘K` palette on the other tree. The package supplies the schema and takes the map as data,
 * deliberately — routes and entity names are product data, and a package holding them would offer
 * one console's surfaces inside another's.
 *
 * Dumont did have a declaration before this: `manifest.navItems`. But it was shaped for the
 * Module Federation wire — a string icon name, a `url`, an `order` — and nothing else, so a rail
 * or a palette would each have needed a second array with sections, tones and descriptions in it.
 * Three arrays drift the moment a surface is renamed, and the rename is exactly the change nobody
 * remembers to make three times.
 *
 * So the declaration is here, in the package's shape plus the two fields the console needs
 * (`provider`, `order`), and everything else is derived from it — including `manifest.navItems`,
 * which keeps its wire shape byte for byte so the host is unaffected.
 *
 * Type-only imports from the bento subpath: this module is reachable from `manifest`, which the
 * host loads, and a value import would pull the bento bundle along with it.
 */

/** Which band of the console a surface belongs to. */
export type DumontNavSectionId = "sources" | "integration";

/**
 * One surface, as every reader of the map sees it.
 *
 * Routes are stored as a `path` under the integration-instance base rather than absolute, because
 * Dumont's are per-instance (`/…/instance/:id/db-source`) and the id is only known at render.
 * Turing's equivalent can hold absolute routes; this one cannot, which is why the derivations
 * below take an `integrationId`.
 */
export interface DumontNavSurface {
  id: string;
  titleKey: string;
  descriptionKey: string;
  /** Icon name, resolved through {@link resolveIcon}. A name rather than a component so the
   *  Module Federation manifest can carry it to a host that has its own React. */
  iconName: string;
  section: DumontNavSectionId;
  tone: BentoTone;
  /** Path under the integration-instance base, e.g. `/db-source`. */
  path: string;
  /**
   * Whether this surface has a page in the bento tree (D37). Drives `bentoRoute`: a surface with
   * no bento page must send the palette to the console, not to a route nothing serves.
   */
  migrated?: boolean;
  /** Only shown while this connector provider is active. Omitted means always. */
  provider?: string;
  /** Sidebar ordering. */
  order: number;
}

/**
 * The icon components Dumont ships, keyed by the names the declaration uses.
 *
 * Lives here rather than in `manifest` because the declaration is what needs them; `manifest`
 * re-exports {@link resolveIcon} so the Module Federation contract is unchanged and a host still
 * renders Dumont's own icons without maintaining a parallel map.
 */
const ICON_COMPONENTS: Record<string, Icon> = {
  IconActivityHeartbeat,
  IconGitCommit,
  IconDatabase,
  IconBuildingBank,
  IconGlobe,
  IconFolder,
  IconCloudComputing,
  IconTools,
  IconAdjustmentsSearch,
  IconGraph,
  IconChartBar,
  IconListCheck,
  IconSparkles,
  IconPuzzle,
  IconInfoCircle,
  IconWand,
  IconPlugConnected,
};

/**
 * Resolve a declared icon name to its component. Falls back to {@link IconPuzzle} so an unknown
 * name still renders something sensible rather than crashing a nav.
 */
export function resolveIcon(name: string): Icon {
  return ICON_COMPONENTS[name] ?? IconPuzzle;
}

/**
 * One band of the console, plus where its hub lives.
 *
 * `areaRoute` is not stored: like a surface's route it is per-instance, so the declaration keeps
 * the `path` and {@link dumontNavGroups} builds the route once it knows the id.
 */
export interface DumontNavSectionDecl extends Omit<BentoNavSection, "areaRoute"> {
  id: DumontNavSectionId;
  /*
   * The package leaves these three optional — a section may render unlabelled, and one reached
   * only through its items needs no icon. Dumont's always carry all three, because every one of
   * them appears on the rail and as a tile on the home mosaic, and both need something to draw.
   * Requiring them here is what lets the pages read the values instead of asserting them.
   */
  labelKey: string;
  descriptionKey: string;
  icon: Icon;
  /** Hub path under the bento integration-instance base. */
  path: string;
  /** Tone for this section's tile on the bento home. */
  tone: BentoTone;
}

/**
 * The bands the rail lists. The rail renders **sections**, never leaves — that is what keeps it a
 * fixed, small height however many surfaces exist, and it is the flat console sidebar's overflow
 * inverted.
 *
 * Each opens a hub that renders the section's surfaces as a mosaic (D45). One parameterised route
 * serves them all, so a new section is a line here and nothing else.
 */
export const DUMONT_NAV_SECTIONS: Record<DumontNavSectionId, DumontNavSectionDecl> = {
  sources: {
    id: "sources",
    labelKey: "sidebar.sources",
    descriptionKey: "bento.sections.sources.description",
    icon: IconPlugConnected,
    path: "/area/sources",
    tone: "indigo",
  },
  integration: {
    id: "integration",
    labelKey: "integration.title",
    descriptionKey: "bento.sections.integration.description",
    icon: IconAdjustmentsSearch,
    path: "/area/integration",
    tone: "blue",
  },
};

/** Every section, in the order the rail and the home mosaic list them. */
export const DUMONT_NAV_SECTION_IDS = Object.keys(
  DUMONT_NAV_SECTIONS,
) as DumontNavSectionId[];

/** Where a section's hub lives, for the given instance. */
export function sectionAreaRoute(id: DumontNavSectionId, integrationId: string): string {
  return `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}${DUMONT_NAV_SECTIONS[id].path}`;
}

/**
 * Every surface Dumont has. Order matches what `manifest.navItems` carried before D39, so the
 * derived wire shape is identical and the host sees no change.
 */
export const DUMONT_NAV: DumontNavSurface[] = [
  { id: "source", titleKey: "integration.nav.sources", descriptionKey: "integration.sources.description", iconName: "IconGitCommit", section: "sources", tone: "blue", path: "/source", order: 10, provider: "AEM" },
  { id: "db-source", titleKey: "integration.nav.dbSources", descriptionKey: "integration.dbSources.description", iconName: "IconDatabase", section: "sources", tone: "indigo", path: "/db-source", order: 10, provider: "JDBC-DATABASE", migrated: true },
  { id: "wc-source", titleKey: "integration.nav.wcSources", descriptionKey: "integration.wcSources.description", iconName: "IconGlobe", section: "sources", tone: "emerald", path: "/wc-source", order: 10, provider: "WEB-CRAWLER" },
  { id: "assets-source", titleKey: "integration.nav.assetsSources", descriptionKey: "integration.assetsSources.description", iconName: "IconFolder", section: "sources", tone: "amber", path: "/assets-source", order: 10, provider: "ASSETS" },
  { id: "catalog-source", titleKey: "integration.nav.catalogSources", descriptionKey: "integration.catalogSources.description", iconName: "IconBuildingBank", section: "sources", tone: "violet", path: "/catalog-source", order: 10, provider: "CATALOG-BRIDGE" },
  { id: "eds-source", titleKey: "integration.nav.edsSources", descriptionKey: "integration.edsSources.description", iconName: "IconCloudComputing", section: "sources", tone: "rose", path: "/eds-source", order: 10, provider: "EDS" },
  { id: "source-inference", titleKey: "integration.nav.sourceInference", descriptionKey: "integration.sourceInference.description", iconName: "IconWand", section: "sources", tone: "violet", path: "/source-inference", order: 15 },
  { id: "indexing-rule", titleKey: "integration.nav.indexingRules", descriptionKey: "integration.indexingRules.description", iconName: "IconTools", section: "integration", tone: "slate", path: "/indexing-rule", order: 20 },
  { id: "indexing-manager", titleKey: "integration.nav.indexingManager", descriptionKey: "integration.indexingManager.description", iconName: "IconAdjustmentsSearch", section: "integration", tone: "indigo", path: "/indexing-manager", order: 30 },
  { id: "monitoring", titleKey: "integration.nav.monitoring", descriptionKey: "integration.monitoring.description", iconName: "IconGraph", section: "integration", tone: "blue", path: "/monitoring", order: 40 },
  { id: "indexing-stats", titleKey: "integration.nav.indexingStats", descriptionKey: "integration.indexingStats.description", iconName: "IconChartBar", section: "integration", tone: "emerald", path: "/indexing-stats", order: 50 },
  { id: "coverage", titleKey: "integration.nav.coverage", descriptionKey: "integration.coverage.description", iconName: "IconActivityHeartbeat", section: "integration", tone: "amber", path: "/coverage", order: 55 },
  { id: "double-check", titleKey: "integration.nav.doubleCheck", descriptionKey: "integration.doubleCheck.description", iconName: "IconListCheck", section: "integration", tone: "rose", path: "/double-check", order: 60 },
  { id: "insights", titleKey: "integration.nav.insights", descriptionKey: "integration.insights.description", iconName: "IconSparkles", section: "integration", tone: "violet", path: "/insights", order: 90 },
  { id: "plugins", titleKey: "integration.nav.plugins", descriptionKey: "integration.plugins.description", iconName: "IconPuzzle", section: "integration", tone: "slate", path: "/plugins", order: 100 },
  { id: "system-info", titleKey: "integration.nav.systemInfo", descriptionKey: "integration.connectorSystemInfo.description", iconName: "IconInfoCircle", section: "integration", tone: "slate", path: "/system-info", order: 110 },
];

/**
 * One surface in the package's render contract, for the given instance.
 *
 * `fallbackRoute` always names the console tree explicitly — never `ROUTES.INTEGRATION_INSTANCE`,
 * whose prefix is read from the live URL and so resolves to `/bento/…` from inside the bento
 * shell, which is where the palette renders. `bentoRoute` is set only for a surface that has
 * actually moved, which is how the palette knows to jump inside the shell rather than out of it.
 */
export function toBentoNavItem(surface: DumontNavSurface, integrationId: string): BentoNavItem {
  return {
    id: surface.id,
    titleKey: surface.titleKey,
    descriptionKey: surface.descriptionKey,
    icon: resolveIcon(surface.iconName),
    section: surface.section,
    tone: surface.tone,
    bentoRoute: surface.migrated
      ? `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}${surface.path}`
      : undefined,
    fallbackRoute: `${ROUTES.CONSOLE_INTEGRATION_INSTANCE}/${integrationId}${surface.path}`,
  };
}

/** The flat list the `⌘K` palette renders. */
export function dumontNavItems(integrationId: string, surfaces: DumontNavSurface[] = DUMONT_NAV): BentoNavItem[] {
  return surfaces.map((surface) => toBentoNavItem(surface, integrationId));
}

/**
 * The same list grouped into sections, as the rail renders it.
 *
 * One filter, two renderings: the palette and the rail cannot disagree about which surfaces
 * exist, because neither of them holds a list.
 */
export function dumontNavGroups(integrationId: string, surfaces: DumontNavSurface[] = DUMONT_NAV): BentoNavGroup[] {
  return DUMONT_NAV_SECTION_IDS.map((id) => {
    const declared = DUMONT_NAV_SECTIONS[id];
    return {
      // The hub is what puts this section on the rail; without an `areaRoute` the rail skips it.
      section: {
        id: declared.id,
        labelKey: declared.labelKey,
        descriptionKey: declared.descriptionKey,
        icon: declared.icon,
        areaRoute: sectionAreaRoute(id, integrationId),
      },
      items: surfaces.filter((s) => s.section === id).map((s) => toBentoNavItem(s, integrationId)),
    };
  }).filter((group) => group.items.length > 0);
}

/**
 * The surfaces a reader may see, given which connector providers are active.
 *
 * Visibility is the product's question, not the package's: an entry appears when the product says
 * it may. Sorted by `order` here so every reader — sidebar, rail, palette — gets one answer.
 */
export function visibleNavSurfaces(providers: readonly string[]): DumontNavSurface[] {
  return DUMONT_NAV.filter((s) => !s.provider || providers.includes(s.provider)).sort(
    (a, b) => a.order - b.order,
  );
}
