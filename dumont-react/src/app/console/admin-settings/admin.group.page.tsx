import { ROUTES } from "@/app/routes.const";
import { AdminGroupForm } from "@/components/admin/admin.group.form";
import { LoadProvider } from "@/components/loading-provider";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { DumGroup } from "@/models/auth/group";
import { DumGroupService } from "@/services/auth/group.service";
import { IconUsersGroup } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const groupService = new DumGroupService();

/** What the form starts from on the `new` route. Frozen so no caller can edit the shared value. */
const EMPTY_GROUP: DumGroup = Object.freeze({
  id: "",
  name: "",
  description: "",
  dumRoles: [],
  dumUsers: [],
}) as DumGroup;

export default function AdminGroupPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = !id || id === "new";
  const [fetched, setFetched] = useState<DumGroup>();
  const [error, setError] = useState<string | null>(null);

  /*
   * D53 — a blank group is a value, not a fetch result.
   *
   * The effect used to write it with `setGroup`, which renders `undefined` first and the blank
   * second, and is a `setState` in an effect body. Nothing is being synchronised here: on the new
   * route there is no request to make and the shape is known at render.
   */
  const group = isNew ? EMPTY_GROUP : fetched;

  useSubPageBreadcrumb(isNew ? t("admin.groups.newGroup") : group?.name ?? "");

  useEffect(() => {
    if (isNew) return;
    groupService
      .get(id!)
      .then(setFetched)
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
      <AdaptivePageHeader
        icon={IconUsersGroup}
        feature={t("admin.groups.title")}
        name={isNew ? t("admin.groups.newGroup") : group?.name ?? ""}
        description={isNew ? t("admin.groups.createDescription") : t("admin.groups.editDescription")}
        urlBase={ROUTES.ADMIN_GROUPS}
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
