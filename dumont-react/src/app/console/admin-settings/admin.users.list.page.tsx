import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { DumKeycloakUser } from "@/models/auth/keycloak-user";
import type { DumUser } from "@/models/auth/user";
import { DumAdminUserService } from "@/services/auth/admin-user.service";
import { DumAuthorizationService } from "@/services/auth/authorization.service";
import { DumKeycloakAdminService } from "@/services/auth/keycloak-admin.service";
import { IconUsers } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const adminUserService = new DumAdminUserService();
const keycloakAdminService = new DumKeycloakAdminService();
const authorizationService = new DumAuthorizationService();

type AdminUserItem = {
  username: string;
  email?: string;
  description?: string;
};

export default function AdminUsersListPage() {
  const { t } = useTranslation();
  const [users, setUsers] = useState<AdminUserItem[]>();
  const [error, setError] = useState<string | null>(null);
  const [keycloak, setKeycloak] = useState<boolean | null>(null);
  useSubPageBreadcrumb(t("admin.users.title"));

  useEffect(() => {
    authorizationService
      .discovery()
      .then((d) => setKeycloak(!!d.keycloak))
      .catch(() => setKeycloak(false));
  }, []);

  useEffect(() => {
    if (keycloak === null) return;
    if (keycloak) {
      keycloakAdminService
        .listUsers()
        .then((kcUsers: DumKeycloakUser[]) => {
          setUsers(
            kcUsers.map((u) => ({
              username: u.username,
              email: u.email,
              description: [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email,
            }))
          );
        })
        .catch(() => setError(t("admin.users.loadFailed")));
    } else {
      adminUserService
        .query()
        .then((list: DumUser[]) => {
          setUsers(
            list.map((u) => ({
              username: u.username,
              email: u.email,
              description: u.email,
            }))
          );
        })
        .catch(() => setError(t("admin.users.loadFailed")));
    }
  }, [keycloak, t]);

  const gridItemList = useGridAdapter(users, {
    id: "username",
    name: "username",
    description: "description",
    url: (item) => `${ROUTES.ADMIN_ROOT}/users/${item.username}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={users} error={error} tryAgainUrl={ROUTES.ADMIN_USERS}>
      <SubPageHeader
        icon={IconUsers}
        feature={t("admin.users.title")}
        name={t("admin.users.title")}
        description={keycloak ? t("admin.users.keycloakDescription") : t("admin.users.description")}
      />
      {users && (
        <GridList gridItemList={gridItemList}>
          {!keycloak && <GridList.NewButton to={`${ROUTES.ADMIN_ROOT}/users/new`} label="User" />}
        </GridList>
      )}
    </LoadProvider>
  );
}
