import type { TurIntegrationDbSource } from "@/models/integration/integration-db-source.model";
import { TurIntegrationDbSourceService } from "@/services/integration/integration-db-source.service";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { BentoSourceList, useSourceList } from "./bento-source-list";

/**
 * The database source list, in bento chrome.
 *
 * Its console original at `/admin/…/db-source` is untouched and still serves; this is an addition,
 * not a rewrite. The data path is identical — same service, same model, same translation keys —
 * and only the chrome differs.
 */
export default function IntegrationInstanceDbSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationDbSourceService(id), [id]);
  const { items, error } = useSourceList<TurIntegrationDbSource>(service);

  return (
    <BentoSourceList<TurIntegrationDbSource>
      surfaceId="db-source"
      i18nPrefix="integration.dbSources"
      items={items}
      error={error}
      itemKey={(source) => source.id}
      itemTitle={(source) => source.name}
      itemDescription={(source) => source.description}
    />
  );
}
