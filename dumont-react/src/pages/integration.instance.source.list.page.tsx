import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useAemSourceImport } from "@/components/integration/use-aem-source-import";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { IconGitCommit, IconUpload } from "@tabler/icons-react";
import { useMemo, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationAemSources, setIntegrationAemSources] = useState<TurIntegrationAemSource[]>();
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  // Shared with the bento list (D46): the two pages differ in what the reader clicks, not in what
  // importing does.
  const { fileInputRef, open: handleImportClick, onFileChange: handleFileChange } =
    useAemSourceImport(turIntegrationAemSourceService, setIntegrationAemSources);

  /*
   * D56 - the array lists what the effect reads. It used to say `[id]` while reading the service
   * and `t`: both derive from something that does change (the route, the language), so the array
   * was describing a narrower effect than the one written.
   */
  useEffect(() => {
    turIntegrationAemSourceService.query().then((sources) => {
      setIntegrationAemSources(sources);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id, turIntegrationAemSourceService, t]);

  const gridItemList = useGridAdapter(integrationAemSources, {
    name: "name",
    description: "endpoint",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationAemSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`}>
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        className="hidden"
        aria-label={t("forms.importExport.import")}
        title={t("forms.importExport.import")}
        onChange={handleFileChange}
      />
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconGitCommit} name={t("integration.sources.title")}
          feature={t("integration.sources.feature")}
          description={t("integration.sources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} label={t("integration.sources.feature")} />
          <GridList.Action>
            <DropdownMenuItem onClick={handleImportClick}>
              <IconUpload className="size-4 mr-2" />
              {t("forms.importExport.import")}
            </DropdownMenuItem>
          </GridList.Action>
        </GridList>

      </>) : (
        <BlankSlate
          icon={IconGitCommit}
          title={t("integration.sources.blankTitle")}
          description={t("integration.sources.blankDescription")}
          buttonText={t("integration.sources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} />
      )}
    </LoadProvider>
  )
}
