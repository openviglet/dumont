import { ROUTES } from "@/app/routes.const";
import { AdminRoleForm } from "@/components/admin/admin.role.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { DumRole } from "@/models/auth/role";
import { DumRoleService } from "@/services/auth/role.service";
import { IconShieldCheck } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const roleService = new DumRoleService();

export default function AdminRolePage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = !id || id === "new";
  const [role, setRole] = useState<DumRole>();
  const [error, setError] = useState<string | null>(null);

  useSubPageBreadcrumb(isNew ? t("admin.roles.newRole") : role?.name ?? "");

  useEffect(() => {
    if (isNew) {
      setRole({ id: "", name: "", description: "", dumPrivileges: [] });
      return;
    }
    roleService
      .get(id!)
      .then(setRole)
      .catch(() => setError(t("admin.roles.loadFailed")));
  }, [id, isNew, t]);

  async function handleSave(values: DumRole) {
    try {
      if (isNew) {
        await roleService.create(values);
        toast.success(t("admin.roles.created"));
      } else {
        await roleService.update(values.id, values);
        toast.success(t("admin.roles.updated"));
      }
      navigate(ROUTES.ADMIN_ROLES);
    } catch {
      toast.error(t("admin.roles.saveFailed"));
    }
  }

  async function handleDelete() {
    if (!role || isNew) return;
    try {
      await roleService.delete(role.id);
      toast.success(t("admin.roles.deleted"));
      navigate(ROUTES.ADMIN_ROLES);
    } catch {
      toast.error(t("admin.roles.deleteFailed"));
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={role} error={error} tryAgainUrl={ROUTES.ADMIN_ROLES}>
      <SubPageHeader
        icon={IconShieldCheck}
        feature={t("admin.roles.title")}
        name={isNew ? t("admin.roles.newRole") : role?.name ?? ""}
        description={isNew ? t("admin.roles.createDescription") : t("admin.roles.editDescription")}
      />
      {role && (
        <AdminRoleForm
          role={role}
          isNew={isNew}
          onSave={handleSave}
          onDelete={!isNew ? handleDelete : undefined}
          onCancel={() => navigate(ROUTES.ADMIN_ROLES)}
        />
      )}
    </LoadProvider>
  );
}
