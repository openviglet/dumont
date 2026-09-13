import { ROUTES } from "@/app/routes.const";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { LoadProvider } from "@/components/loading-provider";
import { useCurrentUser } from "@/contexts/user.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconSettings, IconUser, IconWorld } from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";

/**
 * The account shell: a header, the two sub-pages it holds, and their outlet (D88).
 *
 * It was one `SubPage`, the console component that drew an entity sidebar beside the outlet. The
 * bento layer has no sidebar of that kind — a rail is the app's, not a page's — so the same two
 * entries are chips under the hero instead. The information is unchanged: the group is named once
 * at the top, its members sit beside each other, and the routed child names itself.
 */
export default function UserAccountPage() {
  const { t } = useTranslation();
  const { user } = useCurrentUser();

  useSubPageBreadcrumb({ label: t("account.title") });

  const sections = useMemo(
    () => [
      { title: t("account.nav.profile"), to: `${ROUTES.USER_ACCOUNT}/profile`, icon: IconUser },
      { title: t("account.nav.preferences"), to: `${ROUTES.USER_ACCOUNT}/preferences`, icon: IconWorld },
    ],
    [t],
  );

  return (
    <LoadProvider checkIsNotUndefined={user?.username ? user : undefined} tryAgainUrl={ROUTES.USER_ACCOUNT}>
      <AdaptivePageHeader
        icon={IconSettings}
        feature={t("account.feature")}
        name={t("account.title")}
        description={t("account.description")}
      >
        <nav aria-label={t("account.title")} className="flex flex-wrap items-center gap-2">
          {sections.map(({ title, to, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                "bento-tile bento-tile-clickable inline-flex items-center gap-2 rounded-full border border-border/60 px-3 py-1.5 text-sm backdrop-blur "
                + (isActive ? "bg-card text-foreground" : "bg-card/60 text-muted-foreground hover:text-foreground")
              }
            >
              <Icon size={16} aria-hidden />
              {title}
            </NavLink>
          ))}
        </nav>
      </AdaptivePageHeader>
      <Outlet />
    </LoadProvider>
  );
}
