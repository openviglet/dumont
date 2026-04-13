import { ROUTES } from "@/app/routes.const";
import { IntegrationDbSourceForm } from "@/components/integration/integration.db.source.form";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";

import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceDbSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationDbSource, setIntegrationDbSource] = useState<TurIntegrationDbSource>({} as TurIntegrationDbSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationDbSourceService = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexingAll, setIndexingAll] = useState(false);
  const [reindexingAll, setReindexingAll] = useState(false);
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
    setIndexingAll(true);
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
    } finally {
      setIndexingAll(false);
    }
  }

  async function onReindexAll() {
    setReindexingAll(true);
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
    } finally {
      setReindexingAll(false);
    }
  }

  return (
    <IntegrationDbSourceForm
      value={integrationDbSource}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
      headerActions={
        <>
          <GradientButton type="button" variant="outline" size="sm" onClick={onReindexAll} disabled={isActionDisabled} loading={reindexingAll}>
            {t("integration.dbSources.reindexAll")}
          </GradientButton>
          <GradientButton type="button" variant="outline" size="sm" onClick={onIndexAll} disabled={isActionDisabled} loading={indexingAll}>
            {t("integration.dbSources.indexAll")}
          </GradientButton>
        </>
      }
    />
  )
}
