import {
  IconAdjustmentsSearch,
  IconChartBar,
  IconCloudComputing,
  IconDatabase,
  IconFolder,
  IconGitCommit,
  IconGlobe,
  IconGraph,
  IconInfoCircle,
  IconListCheck,
  IconPuzzle,
  IconSparkles,
  IconTools,
} from "@tabler/icons-react";
import type { Icon } from "@tabler/icons-react";

export interface DumontNavItem {
  titleKey: string;
  url: string;
  icon: string;
  order: number;
  /** If set, only show when the active connector provider matches. Omit for common items. */
  provider?: string;
}

export interface DumontManifest {
  version: number;
  navItems: DumontNavItem[];
}

/**
 * The actual icon components Dumont ships, keyed by the names used in the
 * manifest. This is the single source of truth for nav icons — consumed both
 * by the standalone sidebar and, via {@link resolveIcon}, by hosts that mount
 * Dumont as a Module Federation remote (e.g. Turing). Adding a new connector
 * icon here is the ONLY change needed; the host never has to be touched.
 */
const ICON_COMPONENTS: Record<string, Icon> = {
  IconGitCommit,
  IconDatabase,
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
};

/**
 * Resolves a manifest icon name to its component. Exposed through the Module
 * Federation `./manifest` remote so the host renders Dumont's own icons
 * without maintaining a parallel icon map. Falls back to {@link IconPuzzle}
 * so an unknown name still renders a sensible icon.
 */
export function resolveIcon(name: string): Icon {
  return ICON_COMPONENTS[name] ?? IconPuzzle;
}

const manifest: DumontManifest = {
  version: 1,
  navItems: [
    { titleKey: "integration.nav.sources", url: "/source", icon: "IconGitCommit", order: 10, provider: "AEM" },
    { titleKey: "integration.nav.dbSources", url: "/db-source", icon: "IconDatabase", order: 10, provider: "JDBC-DATABASE" },
    { titleKey: "integration.nav.wcSources", url: "/wc-source", icon: "IconGlobe", order: 10, provider: "WEB-CRAWLER" },
    { titleKey: "integration.nav.assetsSources", url: "/assets-source", icon: "IconFolder", order: 10, provider: "ASSETS" },
    { titleKey: "integration.nav.edsSources", url: "/eds-source", icon: "IconCloudComputing", order: 10, provider: "EDS" },
    { titleKey: "integration.nav.indexingRules", url: "/indexing-rule", icon: "IconTools", order: 20 },
    { titleKey: "integration.nav.indexingManager", url: "/indexing-manager", icon: "IconAdjustmentsSearch", order: 30 },
    { titleKey: "integration.nav.monitoring", url: "/monitoring", icon: "IconGraph", order: 40 },
    { titleKey: "integration.nav.indexingStats", url: "/indexing-stats", icon: "IconChartBar", order: 50 },
    { titleKey: "integration.nav.doubleCheck", url: "/double-check", icon: "IconListCheck", order: 60 },
    { titleKey: "integration.nav.insights", url: "/insights", icon: "IconSparkles", order: 90 },
    { titleKey: "integration.nav.plugins", url: "/plugins", icon: "IconPuzzle", order: 100 },
    { titleKey: "integration.nav.systemInfo", url: "/system-info", icon: "IconInfoCircle", order: 110 },
  ],
};

export default manifest;
