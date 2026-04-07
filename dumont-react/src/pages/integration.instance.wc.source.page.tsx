import { ROUTES } from "@/app/routes.const";
import { IntegrationWcSourceForm } from "@/components/integration/integration.wc.source.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { IconGlobe } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@openviglet/viglet-design-system";

export default function IntegrationInstanceWcSourcePage() {
  const navigate = useNavigate();
  const { id, sourceId, tab = "general" } = useParams() as { id: string, sourceId: string, tab?: string };
  const [wcSource, setWcSource] = useState<TurIntegrationWcSource>({} as TurIntegrationWcSource);
  const [isNew, setIsNew] = useState<boolean>(true);
  const service = useMemo(() => new TurIntegrationWcSourceService(id), [id]);
  const [open, setOpen] = useState(false);
  const [crawling, setCrawling] = useState(false);
  const { t } = useTranslation();

  useEffect(() => {
    if (sourceId !== "new") {
      service.get(sourceId).then(setWcSource);
      setIsNew(false);
    }
  }, [id, sourceId, service]);

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
    <>
      <SubPageHeader icon={IconGlobe} name={t("integration.wcSources.title")}
        feature={t("integration.wcSources.title")}
        description={t("integration.wcSources.description")}
        onDelete={onDelete} open={open} setOpen={setOpen} />

      <div className="flex justify-end pb-4 px-6">
        <div className="flex flex-wrap items-center gap-2 rounded-md border bg-muted/50 px-3 py-2">
          <GradientButton type="button" variant="outline" onClick={onCrawl} disabled={isActionDisabled} loading={crawling}>
            {t("integration.wcSources.crawl")}
          </GradientButton>
        </div>
      </div>
      <IntegrationWcSourceForm value={wcSource} isNew={isNew} integrationId={id} sourceId={sourceId} tab={tab} />
    </>
  )
}
