import { ROUTES } from "@/app/routes.const";
import { IntegrationSourceForm } from "@/components/integration/integration.source.form";
import { AemSourceWizard } from "@/components/integration/wizard/aem-source-wizard";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { TurIntegrationConnectorService } from "@/services/integration/integration-connector.service";

import { exportSourceToJson } from "@/components/integration/source-import-export";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";


export default function IntegrationInstanceSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [integrationAemSource, setIntegrationAemSource] = useState<TurIntegrationAemSource>({
    id: "", name: "", endpoint: "", username: "", password: "",
    rootPath: "", contentType: "", subType: "", oncePattern: "",
    defaultLocale: "", localeClass: "", deltaClass: "",
    author: false, publish: false,
    authorSNSite: "", publishSNSite: "",
    authorURLPrefix: "", publishURLPrefix: "",
    localePaths: [], attributeSpecifications: [], models: [],
  });
  const [isNew, setIsNew] = useState<boolean>(true);
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const turIntegrationConnectorService = useMemo(() => new TurIntegrationConnectorService(id), [id]);
  const [open, setOpen] = useState(false);
  const [indexingAll, setIndexingAll] = useState(false);
  const [reindexingAll, setReindexingAll] = useState(false);
  const [dryScanning, setDryScanning] = useState(false);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem | undefined>(undefined);
  const { t } = useTranslation();

  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (sourceId !== "new") {
      turIntegrationAemSourceService.get(sourceId).then((source) => {
        setIntegrationAemSource(source);
        if (source.name) {
          setBreadcrumb({ label: source.name });
        }
      });
      setIsNew(false);
    } else {
      setBreadcrumb({ label: t("forms.wizard.newSource") });
    }
  }, [id, sourceId, turIntegrationAemSourceService, t]);

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

  function handleExport() {
    if (!integrationAemSource?.name) return;
    const json = exportSourceToJson(integrationAemSource);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${integrationAemSource.name}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success(t("forms.importExport.exportSuccess"));
  }

  if (isNew) {
    return <AemSourceWizard integrationId={id} />;
  }

  return (
    <IntegrationSourceForm
      value={integrationAemSource}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onSourceUpdated={setIntegrationAemSource}
      onDelete={onDelete}
      onExport={handleExport}
      open={open}
      setOpen={setOpen}
      onDryScan={onDryScan}
      onReindexAll={onReindexAll}
      onIndexAll={onIndexAll}
      indexingDisabled={isActionDisabled}
      dryScanning={dryScanning}
      reindexingAll={reindexingAll}
      indexingAll={indexingAll}
    />
  )
}
