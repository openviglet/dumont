import { useAemSourceImport } from "@/components/integration/use-aem-source-import";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { IconUpload } from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoEntityList, useEntityList } from "./bento-entity-list";

/**
 * The AEM source list, in bento chrome. Its console route is untouched.
 *
 * The one source list with an action of its own — importing a source from a JSON export. In the
 * console it hides in the grid's overflow menu; here it takes `headerAction`, the row the package
 * puts under the hero for list-scoped actions. Same behaviour either way: the import itself lives
 * in `useAemSourceImport`, which both pages call.
 */
export default function IntegrationInstanceSourceListBentoPage() {
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const { items, error, setItems } = useEntityList<TurIntegrationAemSource>(service);
  const { fileInputRef, open, onFileChange } = useAemSourceImport(service, setItems);

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        className="hidden"
        aria-label={t("forms.importExport.import")}
        title={t("forms.importExport.import")}
        onChange={onFileChange}
      />
      <BentoEntityList<TurIntegrationAemSource>
        surfaceId="source"
        i18nPrefix="integration.sources"
        items={items}
        error={error}
        itemKey={(source) => source.id}
        itemTitle={(source) => source.name}
        // Two AEM sources are told apart by the author instance they read, not by a description.
        itemDescription={(source) => source.endpoint}
        headerAction={
          <GradientButton variant="outline" size="sm" onClick={open}>
            <IconUpload className="size-4" aria-hidden />
            {t("forms.importExport.import")}
          </GradientButton>
        }
      />
    </>
  );
}
