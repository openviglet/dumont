import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationEdsSource } from "@/models/integration/integration-eds-source.model";
import { TurIntegrationEdsSourceService } from "@/services/integration/integration-eds-source.service";
import { IconCloudComputing } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceEdsSourceListPage() {
  const { id } = useParams() as { id: string };
  const [sources, setSources] = useState<TurIntegrationEdsSource[]>();
  const service = useMemo(() => new TurIntegrationEdsSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();

  /*
   * D56 - the array lists what the effect reads. It used to say `[id]` while reading the service
   * and `t`: both derive from something that does change (the route, the language), so the array
   * was describing a narrower effect than the one written.
   */
  useEffect(() => {
    service
      .query()
      .then(setSources)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id, service, t]);

  const gridItemList = useGridAdapter(sources, {
    name: "title",
    description: "baseUrl",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/eds-source/${item.id}`,
  });

  return (
    <LoadProvider
      checkIsNotUndefined={sources}
      error={error}
      tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/eds-source`}
    >
      {gridItemList.length > 0 ? (
        <>
          <SubPageHeader
            icon={IconCloudComputing}
            name={t("integration.edsSources.title")}
            feature={t("integration.edsSources.feature")}
            description={t("integration.edsSources.description")}
          />
          <GridList gridItemList={gridItemList}>
            <GridList.NewButton
              to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/eds-source/new`}
              label={t("integration.edsSources.feature")}
            />
          </GridList>
        </>
      ) : (
        <BlankSlate
          icon={IconCloudComputing}
          title={t("integration.edsSources.blankTitle")}
          description={t("integration.edsSources.blankDescription")}
          buttonText={t("integration.edsSources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/eds-source/new`}
        />
      )}
    </LoadProvider>
  );
}
