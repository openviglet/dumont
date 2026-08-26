import { ROUTES } from "@/app/routes.const";
import { IntegrationWcSourceForm } from "@/components/integration/integration.wc.source.form";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceWcSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [wcSource, setWcSource] = useState<TurIntegrationWcSource>({} as TurIntegrationWcSource);
  /*
   * D53 - derived, not state. `isNew` is a fact about the route, so a `useState` seeded `true`
   * and corrected inside an effect renders the wrong value first and the right one after, and
   * the correction is a `setState` in an effect body that React 19 refuses.
   */
  const isNew = sourceId === "new";
  const service = useMemo(() => new TurIntegrationWcSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [crawling, setCrawling] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (!isNew) {
      service.get(sourceId).then(setWcSource);
    }
  }, [id, sourceId, isNew, service]);

  async function onDelete() {
    try {
      if (await service.delete(wcSource)) {
        toast.success(t("integration.wcSources.deleted", { name: wcSource.title }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source`);
      } else {
        toast.error(t("integration.wcSources.notDeleted", { name: wcSource.title }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.wcSources.notDeleted", { name: wcSource.title }));
    }
    setOpen(false);
  }

  const isActionDisabled = isNew || !wcSource.id;

  async function onCrawl() {
    setCrawling(true);
    try {
      await service.crawl(wcSource);
      toast.success(t("integration.wcSources.crawlStarted", { name: wcSource.title }));
    } catch (error) {
      console.error("Crawl error", error);
      toast.error(t("integration.wcSources.crawlFailed", { name: wcSource.title }));
    } finally {
      setCrawling(false);
    }
  }

  return (
    <IntegrationWcSourceForm
      value={wcSource}
      isNew={isNew}
      integrationId={id}
      sourceId={sourceId}
      tab={tab}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
      headerActions={
        <GradientButton type="button" variant="outline" size="sm" onClick={onCrawl} disabled={isActionDisabled} loading={crawling}>
          {t("integration.wcSources.crawl")}
        </GradientButton>
      }
    />
  )
}
