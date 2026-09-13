import { ROUTES } from "@/app/routes.const";
import { IntegrationEdsSourceForm } from "@/components/integration/integration.eds.source.form";
import type { TurIntegrationEdsSource } from "@/models/integration/integration-eds-source.model";
import { TurIntegrationEdsSourceService } from "@/services/integration/integration-eds-source.service";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceEdsSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as {
    id: string;
    sourceId: string;
    tab?: string;
  };
  const [edsSource, setEdsSource] = useState<TurIntegrationEdsSource>(
    {} as TurIntegrationEdsSource,
  );
  /*
   * D53 - derived, not state. `isNew` is a fact about the route, so a `useState` seeded `true`
   * and corrected inside an effect renders the wrong value first and the right one after, and
   * the correction is a `setState` in an effect body that React 19 refuses.
   */
  const isNew = sourceId === "new";
  const service = useMemo(() => new TurIntegrationEdsSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [dryScanning, setDryScanning] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (!isNew) {
      service.get(sourceId).then(setEdsSource);
    }
  }, [id, sourceId, isNew, service]);

  async function onDelete() {
    try {
      if (await service.delete(edsSource)) {
        toast.success(t("integration.edsSources.deleted", { name: edsSource.title }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/eds-source`);
      } else {
        toast.error(t("integration.edsSources.notDeleted", { name: edsSource.title }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.edsSources.notDeleted", { name: edsSource.title }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !edsSource.id;

  async function onIndex() {
    setIndexing(true);
    try {
      await service.index(edsSource);
      toast.success(t("integration.edsSources.indexStarted", { name: edsSource.title }));
    } catch (error) {
      // 409 → "already running"; let the global 422 handler do its thing.
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.edsSources.alreadyRunning", { name: edsSource.title }));
      } else {
        console.error("Index error", error);
        toast.error(t("integration.edsSources.indexFailed", { name: edsSource.title }));
      }
    } finally {
      setIndexing(false);
    }
  }

  async function onReindex() {
    setReindexing(true);
    try {
      await service.reindex(edsSource);
      toast.success(t("integration.edsSources.reindexStarted", { name: edsSource.title }));
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.edsSources.alreadyRunning", { name: edsSource.title }));
      } else {
        console.error("Reindex error", error);
        toast.error(t("integration.edsSources.reindexFailed", { name: edsSource.title }));
      }
    } finally {
      setReindexing(false);
    }
  }

  async function onDryScan() {
    setDryScanning(true);
    try {
      const result = await service.dryScan(edsSource);
      toast.success(
        t("integration.edsSources.dryScanDone", {
          name: edsSource.title,
          count: result.discovered,
        }),
      );
    } catch (error) {
      console.error("Dry scan error", error);
      toast.error(t("integration.edsSources.dryScanFailed", { name: edsSource.title }));
    } finally {
      setDryScanning(false);
    }
  }

  return (
    <IntegrationEdsSourceForm
      value={edsSource}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
      onDryScan={onDryScan}
      onReindex={onReindex}
      onIndex={onIndex}
      indexingDisabled={isActionDisabled}
      dryScanning={dryScanning}
      reindexing={reindexing}
      indexing={indexing}
    />
  );
}
