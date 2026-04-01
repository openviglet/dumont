import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import { IconGlobe } from "@tabler/icons-react";
import { useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceWcSourceListPage() {
  const { id } = useParams() as { id: string };
  const [sources, setSources] = useState<TurIntegrationWcSource[]>();
  const service = useMemo(() => new TurIntegrationWcSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    service.query().then(setSources)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id]);

  const gridItemList = useGridAdapter(sources, {
    name: "title",
    description: "url",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/${item.id}`
  });

  return (
    <LoadProvider checkIsNotUndefined={sources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconGlobe} name={t("integration.wcSources.title")}
          feature={t("integration.wcSources.feature")}
          description={t("integration.wcSources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/new`} label={t("integration.wcSources.feature")} />
        </GridList>
      </>) : (
        <BlankSlate icon={IconGlobe}
          title={t("integration.wcSources.blankTitle")}
          description={t("integration.wcSources.blankDescription")}
          buttonText={t("integration.wcSources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/wc-source/new`} />
      )}
    </LoadProvider>
  )
}
