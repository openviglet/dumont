import { IconHome, IconInbox } from "@tabler/icons-react"
import type { Icon } from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"

import { ROUTES } from "@/app/routes.const"
import manifest, { resolveIcon, type DumontNavItem } from "@/manifest"
import { NavMain } from "@/components/nav-main"
import { usePlugin } from "@/contexts/plugin.context"
import { TurIntegrationQueueService } from "@/services/integration/integration-queue.service"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { DumontLogo } from "./logo/dumont-logo"
import { ModeToggleSidebar } from "./mode-toggle"

const QUEUE_POLL_INTERVAL_MS = 10_000
const formatCount = (value?: number) => (value ?? 0).toLocaleString()

/** Fixed id used for standalone routes — the axios interceptor strips it from API calls. */
const STANDALONE_ID = "local"
const BASE = `${ROUTES.INTEGRATION_INSTANCE}/${STANDALONE_ID}`

/** Sidebar group label shown for each connector provider's source items. */
const PROVIDER_LABELS: Record<string, string> = {
  AEM: "AEM",
  "JDBC-DATABASE": "Database",
  "WEB-CRAWLER": "Web Crawler",
  ASSETS: "Assets",
  EDS: "EDS (aem.live)",
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation();
  const { toggleSidebar, state, isMobile } = useSidebar();
  const { providers } = usePlugin();
  const isCollapsed = state === "collapsed";

  const [queueSize, setQueueSize] = React.useState<number | undefined>(undefined);

  React.useEffect(() => {
    const service = new TurIntegrationQueueService(STANDALONE_ID);
    let cancelled = false;

    const fetchQueue = () => {
      service
        .getStatus()
        .then((status) => {
          if (!cancelled) setQueueSize(status.queue);
        })
        .catch(() => {
          // Silent: 422 (api key mismatch) is handled globally by axios
          // interceptor; transient errors should not break the sidebar.
        });
    };

    fetchQueue();
    const id = window.setInterval(fetchQueue, QUEUE_POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  const toNav = React.useCallback(
    (item: DumontNavItem) => ({
      title: t(item.titleKey),
      url: `${BASE}${item.url}`,
      icon: resolveIcon(item.icon),
    }),
    [t]
  )

  const navGroups = React.useMemo(
    () => {
      const sorted = [...manifest.navItems].sort((a, b) => a.order - b.order);
      const groups: { label?: string; items: { title: string; url: string; icon: Icon }[] }[] = [
        { items: [{ title: t("home.title"), url: ROUTES.HOME, icon: IconHome }] },
      ];

      // Source items (those with a `provider`) show one group per active connector.
      for (const provider of providers) {
        const activeSources = sorted.filter((item) => item.provider && item.provider === provider);
        if (activeSources.length > 0) {
          groups.push({
            label: PROVIDER_LABELS[provider] ?? provider,
            items: activeSources.map(toNav),
          });
        }
      }

      // Common items (no `provider`) are always shown.
      const commonNavItems = sorted.filter((item) => !item.provider);
      groups.push({ label: t("integration.title"), items: commonNavItems.map(toNav) });

      return groups;
    },
    [t, toNav, providers]
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
        <SidebarGroup>
          <SidebarGroupLabel>{t("sidebar.indexing")}</SidebarGroupLabel>
          <SidebarGroupContent className="flex flex-col gap-2 pt-4">
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  tooltip={`${t("integration.nav.queue")}: ${formatCount(queueSize)}`}
                  variant="outline"
                >
                  <IconInbox className="size-5!" />
                  <span>{t("integration.nav.queue")}</span>
                </SidebarMenuButton>
                {!isCollapsed && (
                  <SidebarMenuBadge>{formatCount(queueSize)}</SidebarMenuBadge>
                )}
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        <NavMain groups={navGroups} />
      </SidebarContent>
      <SidebarFooter>
        <ModeToggleSidebar />
      </SidebarFooter>
    </Sidebar>
  )
}
