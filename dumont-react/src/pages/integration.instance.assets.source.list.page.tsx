import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { useSourceExchange } from "@/components/integration/source-exchange";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model";
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service";
import { IconFolder } from "@tabler/icons-react";
import { useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceAssetsSourceListPage() {
  const { id } = useParams() as { id: string };
  const [sources, setSources] = useState<TurIntegrationAssetsSource[]>();
  const service = useMemo(() => new TurIntegrationAssetsSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    service.query().then(setSources)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [service, t]);

  const reload = () => service.query().then(setSources);
  const exchange = useSourceExchange(service, "assets-sources", reload);

  const gridItemList = useGridAdapter(sources, {
    name: "name", description: "sourceDir",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/assets-source/${item.id}`
  });

  return (
    <LoadProvider checkIsNotUndefined={sources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/assets-source`}>
      {exchange.input}
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconFolder} name={t("integration.assetsSources.title")} feature={t("integration.assetsSources.feature")} description={t("integration.assetsSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/assets-source/new`} label={t("integration.assetsSources.feature")} />
          <GridList.Action>{exchange.menuItems}</GridList.Action>
        </GridList>
      </>) : (<>
        <BlankSlate icon={IconFolder} title={t("integration.assetsSources.blankTitle")} description={t("integration.assetsSources.blankDescription")} buttonText={t("integration.assetsSources.newSource")} urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/assets-source/new`} />
        {exchange.emptyImport}
      </>)}
    </LoadProvider>
  )
}
