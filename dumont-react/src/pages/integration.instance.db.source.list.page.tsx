import { ROUTES } from "@/app/routes.const";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { BentoEmptyState } from "@viglet/viglet-design-system/bento";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import { GridList } from "@/components/grid.list";
import { SourceArchiveImportButton, SourceArchiveInput, SourceArchiveMenuItems } from "@/components/integration/source-archive-actions";
import { useSourceArchive } from "@/components/integration/use-source-archive";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";
import { IconDatabase } from "@tabler/icons-react";
import { useCallback, useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceDbSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationDbSources, setIntegrationDbSources] = useState<TurIntegrationDbSource[]>();
  const turIntegrationDbSourceService = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  /*
   * D56 - the array lists what the effect reads. It used to say `[id]` while reading the service
   * and `t`: both derive from something that does change (the route, the language), so the array
   * was describing a narrower effect than the one written.
   */
  useEffect(() => {
    turIntegrationDbSourceService.query().then((sources) => {
      setIntegrationDbSources(sources);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id, turIntegrationDbSourceService, t]);

  // D362: every source at once, as the zip the first-start import also reads.
  const reload = useCallback(async () => {
    setIntegrationDbSources(await turIntegrationDbSourceService.query());
  }, [turIntegrationDbSourceService]);
  const archive = useSourceArchive(`/v2/integration/${id}/db/source`, "db-sources.zip", reload);
  const { fileInputRef: archiveInputRef, onFileChange: onArchiveFileChange } = archive;

  const gridItemList = useGridAdapter(integrationDbSources, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationDbSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source`}>
      <SourceArchiveInput inputRef={archiveInputRef} onChange={onArchiveFileChange} />
      {gridItemList.length > 0 ? (<>
        <AdaptivePageHeader icon={IconDatabase} name={t("integration.dbSources.title")}
          feature={t("integration.dbSources.feature")}
          description={t("integration.dbSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/new`} label={t("integration.dbSources.feature")} />
          <GridList.Action>
            <SourceArchiveMenuItems archive={archive} />
          </GridList.Action>
        </GridList>

      </>) : (
        <BentoEmptyState
          icon={IconDatabase}
          title={t("integration.dbSources.blankTitle")}
          description={t("integration.dbSources.blankDescription")}
          action={
            <div className="flex flex-wrap gap-2 justify-center">
              <GradientButtonLink to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/new`}>
                {t("integration.dbSources.newSource")}
              </GradientButtonLink>
              <SourceArchiveImportButton archive={archive} />
            </div>
          } />
      )}
    </LoadProvider>
  )
}
