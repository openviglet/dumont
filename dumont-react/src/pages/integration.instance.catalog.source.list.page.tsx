import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model";
import { TurIntegrationCatalogSourceService } from "@/services/integration/integration-catalog-source.service";
import { IconBuildingBank } from "@tabler/icons-react";
import { useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceCatalogSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationCatalogSources, setIntegrationCatalogSources] = useState<TurIntegrationCatalogSource[]>();
  const turIntegrationCatalogSourceService = useMemo(() => new TurIntegrationCatalogSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    turIntegrationCatalogSourceService.query().then((sources) => {
      setIntegrationCatalogSources(sources);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id]);

  const gridItemList = useGridAdapter(integrationCatalogSources, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/catalog-source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationCatalogSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/catalog-source`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconBuildingBank} name={t("integration.catalogSources.title")}
          feature={t("integration.catalogSources.feature")}
          description={t("integration.catalogSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/catalog-source/new`} label={t("integration.catalogSources.feature")} />
        </GridList>

      </>) : (
        <BlankSlate
          icon={IconBuildingBank}
          title={t("integration.catalogSources.blankTitle")}
          description={t("integration.catalogSources.blankDescription")}
          buttonText={t("integration.catalogSources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/catalog-source/new`} />
      )}
    </LoadProvider>
  )
}
