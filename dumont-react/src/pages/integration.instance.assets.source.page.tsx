import { ROUTES } from "@/app/routes.const";
import { IntegrationAssetsSourceForm } from "@/components/integration/integration.assets.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service";
import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model";
import { IconFolder } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@openviglet/viglet-design-system";

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
    <>
      <SubPageHeader icon={IconFolder} name={t("integration.assetsSources.title")} feature={t("integration.assetsSources.title")} description={t("integration.assetsSources.description")} onDelete={onDelete} open={open} setOpen={setOpen} />
      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton type="button" variant="outline" onClick={onIndexAll} disabled={isNew || !source.id} loading={indexingAll}>{t("integration.assetsSources.indexAll")}</GradientButton>
        </div>
      </div>
      <IntegrationAssetsSourceForm value={source} isNew={isNew} integrationId={id} sourceId={sourceId} tab={tab} />
    </>
  )
}
