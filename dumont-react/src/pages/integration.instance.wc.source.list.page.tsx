import { ROUTES } from "@/app/routes.const";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { BentoEmptyState } from "@viglet/viglet-design-system/bento";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import { GridList } from "@/components/grid.list";
import { SourceArchiveImportButton, SourceArchiveInput, SourceArchiveMenuItems } from "@/components/integration/source-archive-actions";
import { useSourceArchive } from "@/components/integration/use-source-archive";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import { IconGlobe } from "@tabler/icons-react";
import { useCallback, useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceWcSourceListPage() {
  const { id } = useParams() as { id: string };
  const [sources, setSources] = useState<TurIntegrationWcSource[]>();
  const service = useMemo(() => new TurIntegrationWcSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  /*
   * D56 - the array lists what the effect reads. It used to say `[id]` while reading the service
   * and `t`: both derive from something that does change (the route, the language), so the array
   * was describing a narrower effect than the one written.
   */
  useEffect(() => {
    service.query().then(setSources)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id, service, t]);

  // D362: every source at once, as the zip the first-start import also reads.
  const reload = useCallback(async () => {
    setSources(await service.query());
  }, [service]);
  const archive = useSourceArchive(`/v2/integration/${id}/wc/source`, "wc-sources.zip", reload);
  const { fileInputRef: archiveInputRef, onFileChange: onArchiveFileChange } = archive;

  const gridItemList = useGridAdapter(sources, {
    name: "title",
    description: "url",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/${item.id}`
  });

  return (
    <LoadProvider checkIsNotUndefined={sources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source`}>
      <SourceArchiveInput inputRef={archiveInputRef} onChange={onArchiveFileChange} />
      {gridItemList.length > 0 ? (<>
        <AdaptivePageHeader icon={IconGlobe} name={t("integration.wcSources.title")}
          feature={t("integration.wcSources.feature")}
          description={t("integration.wcSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/new`} label={t("integration.wcSources.feature")} />
          <GridList.Action>
            <SourceArchiveMenuItems archive={archive} />
          </GridList.Action>
        </GridList>
      </>) : (
        <BentoEmptyState icon={IconGlobe}
          title={t("integration.wcSources.blankTitle")}
          description={t("integration.wcSources.blankDescription")}
          action={
            <div className="flex flex-wrap gap-2 justify-center">
              <GradientButtonLink to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/new`}>
                {t("integration.wcSources.newSource")}
              </GradientButtonLink>
              <SourceArchiveImportButton archive={archive} />
            </div>
          } />
      )}
    </LoadProvider>
  )
}
