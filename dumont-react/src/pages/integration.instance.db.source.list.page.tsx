import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";
import { IconDatabase } from "@tabler/icons-react";
import { useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceDbSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationDbSources, setIntegrationDbSources] = useState<TurIntegrationDbSource[]>();
  const turIntegrationDbSourceService = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    turIntegrationDbSourceService.query().then((sources) => {
      setIntegrationDbSources(sources);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id]);

  const gridItemList = useGridAdapter(integrationDbSources, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationDbSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconDatabase} name={t("integration.dbSources.title")}
          feature={t("integration.dbSources.feature")}
          description={t("integration.dbSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/new`} label={t("integration.dbSources.feature")} />
        </GridList>

      </>) : (
        <BlankSlate
          icon={IconDatabase}
          title={t("integration.dbSources.blankTitle")}
          description={t("integration.dbSources.blankDescription")}
          buttonText={t("integration.dbSources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/db-source/new`} />
      )}
    </LoadProvider>
  )
}
