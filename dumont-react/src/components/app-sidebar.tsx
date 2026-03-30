import {
  IconAdjustmentsSearch,
  IconChartBar,
  IconGitCommit,
  IconGraph,
  IconHome,
  IconInfoCircle,
  IconListCheck,
  IconTools,
} from "@tabler/icons-react"
import type { Icon } from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"

import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
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

const aemItems: SidebarNavItem[] = [
  { key: "sources", titleKey: "integration.nav.sources", url: `${ROUTES.CONSOLE}/source`, icon: IconGitCommit },
  { key: "indexingRules", titleKey: "integration.nav.indexingRules", url: `${ROUTES.CONSOLE}/indexing-rule`, icon: IconTools },
  { key: "indexingManager", titleKey: "integration.nav.indexingManager", url: `${ROUTES.CONSOLE}/indexing-manager`, icon: IconAdjustmentsSearch },
  { key: "monitoring", titleKey: "integration.nav.monitoring", url: `${ROUTES.CONSOLE}/monitoring`, icon: IconGraph },
  { key: "indexingStats", titleKey: "integration.nav.indexingStats", url: `${ROUTES.CONSOLE}/indexing-stats`, icon: IconChartBar },
  { key: "doubleCheck", titleKey: "integration.nav.doubleCheck", url: `${ROUTES.CONSOLE}/double-check`, icon: IconListCheck },
  { key: "systemInfo", titleKey: "integration.nav.systemInfo", url: `${ROUTES.CONSOLE}/system-info`, icon: IconInfoCircle },
]

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation();
  const { toggleSidebar, state, isMobile } = useSidebar();
  const isCollapsed = state === "collapsed";

  const toNavItems = React.useCallback(
    (items: SidebarNavItem[]) => items.map(({ titleKey, url, icon }) => ({ title: t(titleKey), url, icon })),
    [t]
  )

  const navGroups = React.useMemo(
    () => [
      { items: [{ title: t("home.title"), url: ROUTES.HOME, icon: IconHome }] },
      { label: "AEM", items: toNavItems(aemItems) },
    ],
    [t, toNavItems]
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
