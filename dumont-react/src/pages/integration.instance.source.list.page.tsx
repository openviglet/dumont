import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { importSourceFromJson } from "@/components/integration/source-import-export";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { IconGitCommit, IconUpload } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationAemSources, setIntegrationAemSources] = useState<TurIntegrationAemSource[]>();
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { t } = useTranslation();

  useEffect(() => {
    turIntegrationAemSourceService.query().then((sources) => {
      setIntegrationAemSources(sources);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id]);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const stripIds = (payload: Partial<TurIntegrationAemSource>) => {
    delete (payload as { id?: string }).id;
    payload.localePaths?.forEach((lp) => delete (lp as { id?: string }).id);
    payload.attributeSpecifications?.forEach((spec) => delete (spec as { id?: string }).id);
    payload.models?.forEach((model) => {
      delete (model as { id?: string }).id;
      model.targetAttrs?.forEach((ta) => {
        delete (ta as { id?: string }).id;
        ta.sourceAttrs?.forEach((sa) => delete (sa as { id?: string }).id);
      });
    });
  };

  const createFromImport = async (payload: Partial<TurIntegrationAemSource>) => {
    try {
      const created = await turIntegrationAemSourceService.create(payload as TurIntegrationAemSource);
      if (created) {
        toast.success(t("forms.importExport.importSuccess"));
        const sources = await turIntegrationAemSourceService.query();
        setIntegrationAemSources(sources);
      } else {
        toast.error(t("forms.common.formSubmitFailed"));
      }
    } catch {
      toast.error(t("forms.common.formSubmitFailed"));
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    const json = await file.text();
    const parsed = importSourceFromJson(json);
    if (!parsed) {
      toast.error(t("forms.importExport.importFailed"));
      return;
    }
    const payload = { ...parsed } as Partial<TurIntegrationAemSource>;
    stripIds(payload);
    await createFromImport(payload);
  };

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
