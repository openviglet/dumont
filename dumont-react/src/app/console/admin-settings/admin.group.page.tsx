import { ROUTES } from "@/app/routes.const";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { DumGroup } from "@/models/auth/group";
import { DumGroupService } from "@/services/auth/group.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const groupService = new DumGroupService();

export default function AdminGroupPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = !id || id === "new";
  const [group, setGroup] = useState<DumGroup>();
  const [error, setError] = useState<string | null>(null);

  useSubPageBreadcrumb(isNew ? t("admin.groups.newGroup") : group?.name ?? "");

  useEffect(() => {
    if (isNew) {
      setGroup({ id: "", name: "", description: "", dumRoles: [], dumUsers: [] });
      return;
    }
    groupService
      .get(id!)
      .then(setGroup)
      .catch(() => setError(t("admin.groups.loadFailed")));
  }, [id, isNew, t]);

  async function handleSave(values: DumGroup) {
    try {
      if (isNew) {
        await groupService.create(values);
        toast.success(t("admin.groups.created"));
      } else {
        await groupService.update(values.id, values);
        toast.success(t("admin.groups.updated"));
      }
      navigate(ROUTES.ADMIN_GROUPS);
    } catch {
      toast.error(t("admin.groups.saveFailed"));
    }
  }

  async function handleDelete() {
    if (!group || isNew) return;
    try {
      await groupService.delete(group.id);
      toast.success(t("admin.groups.deleted"));
      navigate(ROUTES.ADMIN_GROUPS);
    } catch {
      toast.error(t("admin.groups.deleteFailed"));
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={group} error={error} tryAgainUrl={ROUTES.ADMIN_GROUPS}>
      <SubPageHeader
        icon={IconUsersGroup}
        feature={t("admin.groups.title")}
        name={isNew ? t("admin.groups.newGroup") : group?.name ?? ""}
        description={isNew ? t("admin.groups.createDescription") : t("admin.groups.editDescription")}
      />
      {group && (
        <AdminGroupForm
          group={group}
          isNew={isNew}
          onSave={handleSave}
          onDelete={!isNew ? handleDelete : undefined}
          onCancel={() => navigate(ROUTES.ADMIN_GROUPS)}
        />
      )}
    </LoadProvider>
  );
}
