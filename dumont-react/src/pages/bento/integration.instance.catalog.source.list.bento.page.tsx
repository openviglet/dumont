import type { TurIntegrationCatalogSource } from "@/models/integration/integration-catalog-source.model";
import { TurIntegrationCatalogSourceService } from "@/services/integration/integration-catalog-source.service";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { BentoEntityList, useEntityList } from "./bento-entity-list";

/** The catalog bridge source list, in bento chrome. Its console route is untouched. */
export default function IntegrationInstanceCatalogSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationCatalogSourceService(id), [id]);
  const { items, error } = useEntityList<TurIntegrationCatalogSource>(service);

  return (
    <BentoEntityList<TurIntegrationCatalogSource>
      surfaceId="catalog-source"
      i18nPrefix="integration.catalogSources"
      items={items}
      error={error}
      itemKey={(source) => source.id}
      itemTitle={(source) => source.name}
      itemDescription={(source) => source.description}
    />
  );
}
