import { ROUTES } from "@/app/routes.const";
import { IntegrationCatalogSourceForm } from "@/components/integration/integration.catalog.source.form";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationCatalogSourceService } from "@/services/integration/integration-catalog-source.service";

import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceCatalogSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationCatalogSource, setIntegrationCatalogSource] = useState<TurIntegrationCatalogSource>({} as TurIntegrationCatalogSource);
  /*
   * D53 - derived, not state. `isNew` is a fact about the route, so a `useState` seeded `true`
   * and corrected inside an effect renders the wrong value first and the right one after, and
   * the correction is a `setState` in an effect body that React 19 refuses.
   */
  const isNew = sourceId === "new";
  const turIntegrationCatalogSourceService = useMemo(() => new TurIntegrationCatalogSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexingAll, setIndexingAll] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (!isNew) {
      turIntegrationCatalogSourceService.get(sourceId).then((source) => {
        setIntegrationCatalogSource(source);
      });
    }
  }, [id, sourceId, isNew, turIntegrationCatalogSourceService]);

  async function onDelete() {
    try {
      if (await turIntegrationCatalogSourceService.delete(integrationCatalogSource)) {
        toast.success(t("integration.catalogSources.deleted", { name: integrationCatalogSource.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/catalog-source`);
      } else {
        toast.error(t("integration.catalogSources.notDeleted", { name: integrationCatalogSource.name }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.catalogSources.notDeleted", { name: integrationCatalogSource.name }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !integrationCatalogSource.id;

  async function onIndexAll() {
    setIndexingAll(true);
    try {
      await turIntegrationCatalogSourceService.indexAll(integrationCatalogSource);
      toast.success(t("integration.catalogSources.indexingStarted", { name: integrationCatalogSource.name }));
    } catch (error) {
      console.error("Index all error", error);
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.catalogSources.alreadyRunning", { name: integrationCatalogSource.name }));
        return;
      }
      toast.error(t("integration.catalogSources.indexingFailed", { name: integrationCatalogSource.name }));
    } finally {
      setIndexingAll(false);
    }
  }

  return (
    <IntegrationCatalogSourceForm
      value={integrationCatalogSource}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
      headerActions={
        <GradientButton type="button" variant="outline" size="sm" onClick={onIndexAll} disabled={isActionDisabled} loading={indexingAll}>
          {t("integration.catalogSources.indexAll")}
        </GradientButton>
      }
    />
  )
}
