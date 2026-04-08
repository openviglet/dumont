import { ROUTES } from "@/app/routes.const";
import { IntegrationSourceForm } from "@/components/integration/integration.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { TurIntegrationConnectorService } from "@/services/integration/integration-connector.service";

import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { IconGitCommit } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";


export default function IntegrationInstanceSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationAemSource, setIntegrationAemSource] = useState<TurIntegrationAemSource>({} as TurIntegrationAemSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const turIntegrationConnectorService = useMemo(() => new TurIntegrationConnectorService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexingAll, setIndexingAll] = useState(false);
  const [reindexingAll, setReindexingAll] = useState(false);
  const [dryScanning, setDryScanning] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") {
      turIntegrationAemSourceService.get(sourceId).then((source) => {
        setIntegrationAemSource(source);
        if (source.name) {
        }
      });
      setIsNew(false);
    } else {
    }
  }, [id, sourceId, turIntegrationAemSourceService]);

  async function onDelete() {
    try {
      if (await turIntegrationAemSourceService.delete(integrationAemSource)) {
        toast.success(t("integration.sources.deleted", { name: integrationAemSource.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`);
      } else {
        toast.error(t("integration.sources.notDeleted", { name: integrationAemSource.name }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.sources.notDeleted", { name: integrationAemSource.name }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !integrationAemSource.id;

  async function onIndexAll() {
    setIndexingAll(true);
    try {
      const result = await turIntegrationConnectorService.indexAll(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.indexingStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.indexingFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Index all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.sources.alreadyRunning", { name: integrationAemSource.name }));
        return;
      }
      toast.error(t("integration.sources.indexingFailed", { name: integrationAemSource.name }));
    } finally {
      setIndexingAll(false);
    }
  }

  async function onReindexAll() {
    setReindexingAll(true);
    try {
      const result = await turIntegrationConnectorService.reindexAll(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.reindexingStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.reindexingFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Reindex all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.sources.alreadyRunning", { name: integrationAemSource.name }));
        return;
      }
      toast.error(t("integration.sources.reindexingFailed", { name: integrationAemSource.name }));
    } finally {
      setReindexingAll(false);
    }
  }

  async function onDryScan() {
    setDryScanning(true);
    try {
      const result = await turIntegrationConnectorService.auditSource(integrationAemSource.name);
      if (result) {
        toast.success(t("integration.sources.dryScanStarted", { name: integrationAemSource.name }));
      } else {
        toast.error(t("integration.sources.dryScanFailed", { name: integrationAemSource.name }));
      }
    } catch (error) {
      console.error("Dry scan error", error);
      toast.error(t("integration.sources.dryScanFailed", { name: integrationAemSource.name }));
    } finally {
      setDryScanning(false);
    }
  }
  return (
    <>
      <SubPageHeader icon={IconGitCommit} name={t("integration.sources.title")}
        feature={t("integration.sources.title")}
        description={t("integration.sources.description")}
        onDelete={onDelete}
        open={open}
        setOpen={setOpen} />

      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton
            type="button"
            variant="outline"
            onClick={onDryScan}
            disabled={isActionDisabled}
            loading={dryScanning}
          >
            {t("integration.sources.dryScan")}
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onReindexAll}
            disabled={isActionDisabled}
            loading={reindexingAll}
          >
            {t("integration.sources.reindexAll")}
          </GradientButton>
          <GradientButton
            type="button"
            variant="outline"
            onClick={onIndexAll}
            disabled={isActionDisabled}
            loading={indexingAll}
          >
            {t("integration.sources.indexAll")}
          </GradientButton>
        </div>
      </div>
      <IntegrationSourceForm value={integrationAemSource} isNew={isNew} integrationId={id} sourceId={sourceId} tab={tab} onSourceUpdated={setIntegrationAemSource} />

    </>
  )
}
