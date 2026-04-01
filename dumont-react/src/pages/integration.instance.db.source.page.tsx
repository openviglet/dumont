import { ROUTES } from "@/app/routes.const";
import { IntegrationDbSourceForm } from "@/components/integration/integration.db.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";

import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { IconDatabase } from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

export default function IntegrationInstanceDbSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationDbSource, setIntegrationDbSource] = useState<TurIntegrationDbSource>({} as TurIntegrationDbSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationDbSourceService = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") {
      turIntegrationDbSourceService.get(sourceId).then((source) => {
        setIntegrationDbSource(source);
      });
      setIsNew(false);
    }
  }, [id, sourceId, turIntegrationDbSourceService]);

  async function onDelete() {
    try {
      if (await turIntegrationDbSourceService.delete(integrationDbSource)) {
        toast.success(t("integration.dbSources.deleted", { name: integrationDbSource.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source`);
      } else {
        toast.error(t("integration.dbSources.notDeleted", { name: integrationDbSource.name }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.dbSources.notDeleted", { name: integrationDbSource.name }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !integrationDbSource.id;

  async function onIndexAll() {
    try {
      await turIntegrationDbSourceService.indexAll(integrationDbSource);
      toast.success(t("integration.dbSources.indexingStarted", { name: integrationDbSource.name }));
    } catch (error) {
      console.error("Index all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.dbSources.alreadyRunning", { name: integrationDbSource.name }));
        return;
      }
      toast.error(t("integration.dbSources.indexingFailed", { name: integrationDbSource.name }));
    }
  }

  async function onReindexAll() {
    try {
      await turIntegrationDbSourceService.reindexAll(integrationDbSource);
      toast.success(t("integration.dbSources.reindexingStarted", { name: integrationDbSource.name }));
    } catch (error) {
      console.error("Reindex all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.dbSources.alreadyRunning", { name: integrationDbSource.name }));
        return;
      }
      toast.error(t("integration.dbSources.reindexingFailed", { name: integrationDbSource.name }));
    }
  }

  return (
    <>
      <SubPageHeader icon={IconDatabase} name={t("integration.dbSources.title")}
        feature={t("integration.dbSources.title")}
        description={t("integration.dbSources.description")}
        onDelete={onDelete}
        open={open}
        setOpen={setOpen} />

      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton
            type="button"
            variant="outline"
            onClick={onReindexAll}
            disabled={isActionDisabled}
          >
            {t("integration.dbSources.reindexAll")}
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onIndexAll}
            disabled={isActionDisabled}
          >
            {t("integration.dbSources.indexAll")}
          </GradientButton>
        </div>
      </div>
      <IntegrationDbSourceForm value={integrationDbSource} isNew={isNew} integrationId={id} sourceId={sourceId} tab={tab} />
    </>
  )
}
