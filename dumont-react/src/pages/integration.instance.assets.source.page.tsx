import { ROUTES } from "@/app/routes.const";
import { IntegrationAssetsSourceForm } from "@/components/integration/integration.assets.source.form";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model";
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceAssetsSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [source, setSource] = useState<TurIntegrationAssetsSource>({} as TurIntegrationAssetsSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const service = useMemo(() => new TurIntegrationAssetsSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexingAll, setIndexingAll] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") { service.get(sourceId).then(setSource); setIsNew(false); }
  }, [id, sourceId, service]);

  async function onDelete() {
    try {
      if (await service.delete(source)) { toast.success(t("integration.assetsSources.deleted", { name: source.name })); navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/assets-source`); }
      else { toast.error(t("integration.assetsSources.notDeleted", { name: source.name })); }
    } catch (error) { console.error(error); toast.error(t("integration.assetsSources.notDeleted", { name: source.name })); }
    setOpen(false);
  }

  async function onIndexAll() {
    setIndexingAll(true);
    try { await service.indexAll(source); toast.success(t("integration.assetsSources.indexingStarted", { name: source.name })); }
    catch (error) { console.error(error); toast.error(t("integration.assetsSources.indexingFailed", { name: source.name })); }
    finally { setIndexingAll(false); }
  }

  return (
    <IntegrationAssetsSourceForm
      value={source}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
      headerActions={
        <GradientButton type="button" variant="outline" size="sm" onClick={onIndexAll} disabled={isNew || !source.id} loading={indexingAll}>
          {t("integration.assetsSources.indexAll")}
        </GradientButton>
      }
    />
  )
}
