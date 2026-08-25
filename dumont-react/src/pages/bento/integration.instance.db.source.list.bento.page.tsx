import { ROUTES } from "@/app/routes.const";
import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";
import { IconDatabase } from "@tabler/icons-react";
import { BentoEntityTile, BentoListPage } from "@viglet/viglet-design-system/bento";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * The database source list, in bento chrome — the one surface D37 puts under the parallel tree.
 *
 * Its console original at `/admin/…/db-source` is untouched and still serves; this is an addition,
 * not a rewrite. That is the whole claim of the parallel tree: a page moves by appearing here, and
 * moves back by this route being deleted.
 *
 * The data path is deliberately identical — same service, same model, same translation keys. The
 * same entity rendered in two chromes is the same entity, so minting a parallel i18n namespace
 * would make the two eras disagree about what a database source is called. What differs is the
 * chrome: `SubPageHeader` + `GridList` + a separate `BlankSlate` branch become one `BentoListPage`
 * call, which owns the hero, the loading gate, the mosaic and the empty state together.
 */
export default function IntegrationInstanceDbSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const [sources, setSources] = useState<TurIntegrationDbSource[]>();
  const [error, setError] = useState<string | null>(null);
  const service = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const { t } = useTranslation();

  useEffect(() => {
    service
      .query()
      .then(setSources)
      .catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [service, t]);

  const bentoBase = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${id}`;
  /*
   * The detail page has not migrated, so a tile links to the console original. Named through
   * CONSOLE_INTEGRATION_INSTANCE rather than INTEGRATION_INSTANCE: the latter derives its prefix
   * from the live URL, which from inside this tree resolves to `/bento/...` and would send every
   * tile to a route that does not exist.
   */
  const consoleBase = `${ROUTES.CONSOLE_INTEGRATION_INSTANCE}/${id}`;

  return (
    <BentoListPage<TurIntegrationDbSource>
      items={sources}
      error={error}
      tryAgainUrl={`${bentoBase}/db-source`}
      heroIcon={IconDatabase}
      title={t("integration.dbSources.title")}
      subtitle={t("integration.dbSources.description")}
      newRoute={`${consoleBase}/db-source/new`}
      newLabel={t("integration.dbSources.newSource")}
      newSubtitle={t("integration.dbSources.feature")}
      itemKey={(source) => source.id}
      renderTile={(source, emphasis) => (
        <BentoEntityTile
          to={`${consoleBase}/db-source/${source.id}`}
          emphasis={emphasis}
          defaultIcon={IconDatabase}
          title={source.name}
          description={source.description}
        />
      )}
      emptyTitle={t("integration.dbSources.blankTitle")}
      emptyDescription={t("integration.dbSources.blankDescription")}
    />
  );
}
