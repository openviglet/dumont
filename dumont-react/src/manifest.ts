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

const manifest: DumontManifest = {
  version: 1,
  navItems: [
    { titleKey: "integration.nav.sources", url: "/source", icon: "IconGitCommit", order: 10, provider: "AEM" },
    { titleKey: "integration.nav.dbSources", url: "/db-source", icon: "IconDatabase", order: 10, provider: "JDBC-DATABASE" },
    { titleKey: "integration.nav.wcSources", url: "/wc-source", icon: "IconGlobe", order: 10, provider: "WEB-CRAWLER" },
    { titleKey: "integration.nav.assetsSources", url: "/assets-source", icon: "IconFolder", order: 10, provider: "ASSETS" },
    { titleKey: "integration.nav.indexingRules", url: "/indexing-rule", icon: "IconTools", order: 20 },
    { titleKey: "integration.nav.indexingManager", url: "/indexing-manager", icon: "IconAdjustmentsSearch", order: 30 },
    { titleKey: "integration.nav.monitoring", url: "/monitoring", icon: "IconGraph", order: 40 },
    { titleKey: "integration.nav.indexingStats", url: "/indexing-stats", icon: "IconChartBar", order: 50 },
    { titleKey: "integration.nav.doubleCheck", url: "/double-check", icon: "IconListCheck", order: 60 },
    { titleKey: "integration.nav.systemInfo", url: "/system-info", icon: "IconInfoCircle", order: 100 },
  ],
};

export default manifest;
