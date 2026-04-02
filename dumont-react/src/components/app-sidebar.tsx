import {
  IconAdjustmentsSearch,
  IconChartBar,
  IconDatabase,
  IconFolder,
  IconGitCommit,
  IconGlobe,
  IconGraph,
  IconHome,
  IconInfoCircle,
  IconListCheck,
  IconSparkles,
  IconTools,
} from "@tabler/icons-react"
import type { Icon } from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"

import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
import { usePlugin } from "@/contexts/plugin.context"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { DumontLogo } from "./logo/dumont-logo"
import { ModeToggleSidebar } from "./mode-toggle"

interface SidebarNavItem {
  key: string
  titleKey: string
  url: string
  icon: Icon
}

/** Fixed id used for standalone routes — the axios interceptor strips it from API calls. */
const STANDALONE_ID = "local"
const BASE = `${ROUTES.INTEGRATION_INSTANCE}/${STANDALONE_ID}`

const aemSourceItems: SidebarNavItem[] = [
  { key: "sources", titleKey: "integration.nav.sources", url: `${BASE}/source`, icon: IconGitCommit },
]

const dbSourceItems: SidebarNavItem[] = [
  { key: "dbSources", titleKey: "integration.nav.dbSources", url: `${BASE}/db-source`, icon: IconDatabase },
]

const wcSourceItems: SidebarNavItem[] = [
  { key: "wcSources", titleKey: "integration.nav.wcSources", url: `${BASE}/wc-source`, icon: IconGlobe },
]

const assetsSourceItems: SidebarNavItem[] = [
  { key: "assetsSources", titleKey: "integration.nav.assetsSources", url: `${BASE}/assets-source`, icon: IconFolder },
]

const commonItems: SidebarNavItem[] = [
  { key: "indexingRules", titleKey: "integration.nav.indexingRules", url: `${BASE}/indexing-rule`, icon: IconTools },
  { key: "indexingManager", titleKey: "integration.nav.indexingManager", url: `${BASE}/indexing-manager`, icon: IconAdjustmentsSearch },
  { key: "monitoring", titleKey: "integration.nav.monitoring", url: `${BASE}/monitoring`, icon: IconGraph },
  { key: "indexingStats", titleKey: "integration.nav.indexingStats", url: `${BASE}/indexing-stats`, icon: IconChartBar },
  { key: "doubleCheck", titleKey: "integration.nav.doubleCheck", url: `${BASE}/double-check`, icon: IconListCheck },
  { key: "insights", titleKey: "integration.nav.insights", url: `${BASE}/insights`, icon: IconSparkles },
  { key: "systemInfo", titleKey: "integration.nav.systemInfo", url: `${BASE}/system-info`, icon: IconInfoCircle },
]

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation();
  const { toggleSidebar, state, isMobile } = useSidebar();
  const { provider } = usePlugin();
  const isCollapsed = state === "collapsed";

  const toNavItems = React.useCallback(
    (items: SidebarNavItem[]) => items.map(({ titleKey, url, icon }) => ({ title: t(titleKey), url, icon })),
    [t]
  )

  const isAem = provider === "AEM";
  const isDb = provider === "JDBC-DATABASE";
  const isWc = provider === "WEB-CRAWLER";
  const isAssets = provider === "ASSETS";

  const navGroups = React.useMemo(
    () => {
      const groups: { label?: string; items: { title: string; url: string; icon: Icon }[] }[] = [
        { items: [{ title: t("home.title"), url: ROUTES.HOME, icon: IconHome }] },
      ];

      if (isAem) {
        groups.push({ label: "AEM", items: toNavItems(aemSourceItems) });
      }

      if (isDb) {
        groups.push({ label: "Database", items: toNavItems(dbSourceItems) });
      }

      if (isWc) {
        groups.push({ label: "Web Crawler", items: toNavItems(wcSourceItems) });
      }

      if (isAssets) {
        groups.push({ label: "Assets", items: toNavItems(assetsSourceItems) });
      }

      groups.push({ label: t("integration.title"), items: toNavItems(commonItems) });

      return groups;
    },
    [t, toNavItems, isAem, isDb, isWc, isAssets]
  )

  return (
    <Sidebar collapsible="icon" side={isMobile ? "right" : "left"} {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              onClick={toggleSidebar}
              className="data-[slot=sidebar-menu-button]:p-1.5!">

              <DumontLogo className="size-6!" />

              {!isCollapsed && (
                <div className="grid flex-1 text-left leading-tight">
                  <span className="text-sm font-bold tracking-tight">{t("sidebar.brandName")}</span>
                  <span className="text-[10px] text-muted-foreground">{t("sidebar.brandTagline")}</span>
                </div>
              )}
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain groups={navGroups} />
      </SidebarContent>
      <SidebarFooter>
        <ModeToggleSidebar />
      </SidebarFooter>
    </Sidebar>
  )
}
