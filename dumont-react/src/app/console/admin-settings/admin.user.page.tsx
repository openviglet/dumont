import { ROUTES } from "@/app/routes.const";
import { AdminUserForm } from "@/components/admin/admin.user.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { DumUser } from "@/models/auth/user";
import { DumAdminUserService } from "@/services/auth/admin-user.service";
import { IconUser } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const adminUserService = new DumAdminUserService();

export default function AdminUserPage() {
  const { t } = useTranslation();
  const { username } = useParams<{ username: string }>();
  const navigate = useNavigate();
  const isNew = !username || username === "new";
  const [user, setUser] = useState<DumUser>();
  const [error, setError] = useState<string | null>(null);

  useSubPageBreadcrumb(isNew ? t("admin.users.newUser") : username ?? "");

  useEffect(() => {
    if (isNew) {
      setUser({ username: "", firstName: "", lastName: "", email: "", admin: false } as DumUser);
      return;
    }
    adminUserService
      .get(username!)
      .then(setUser)
      .catch(() => setError(t("admin.users.loadFailed")));
  }, [username, isNew, t]);

  async function handleSave(values: DumUser) {
    try {
      if (isNew) {
        await adminUserService.create(values);
        toast.success(t("admin.users.created"));
      } else {
        await adminUserService.update(values.username, values);
        toast.success(t("admin.users.updated"));
      }
      navigate(ROUTES.ADMIN_USERS);
    } catch {
      toast.error(t("admin.users.saveFailed"));
    }
  }

  async function handleDelete() {
    if (!user || isNew) return;
    try {
      await adminUserService.delete(user.username);
      toast.success(t("admin.users.deleted"));
      navigate(ROUTES.ADMIN_USERS);
    } catch {
      toast.error(t("admin.users.deleteFailed"));
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={user} error={error} tryAgainUrl={ROUTES.ADMIN_USERS}>
      <SubPageHeader
        icon={IconUser}
        feature={t("admin.users.title")}
        name={isNew ? t("admin.users.newUser") : user?.username ?? ""}
        description={isNew ? t("admin.users.createDescription") : t("admin.users.editDescription")}
      />
      {user && (
        <AdminUserForm
          user={user}
          isNew={isNew}
          onSave={handleSave}
          onDelete={!isNew ? handleDelete : undefined}
          onCancel={() => navigate(ROUTES.ADMIN_USERS)}
        />
      )}
    </LoadProvider>
  );
}
