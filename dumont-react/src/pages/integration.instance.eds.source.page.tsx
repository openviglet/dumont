import { ROUTES } from "@/app/routes.const";
import { IntegrationEdsSourceForm } from "@/components/integration/integration.eds.source.form";
import { GradientButton } from "@/components/ui/gradient-button";
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
  const [isNew, setIsNew] = useState<boolean>(true);
  const service = useMemo(() => new TurIntegrationEdsSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") {
      service.get(sourceId).then(setEdsSource);
      setIsNew(false);
    }
  }, [id, sourceId, service]);

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
      headerActions={
        <GradientButton
          type="button"
          variant="outline"
          size="sm"
          onClick={onIndex}
          disabled={isActionDisabled}
          loading={indexing}
        >
          {t("integration.edsSources.indexAll")}
        </GradientButton>
      }
    />
  );
}
