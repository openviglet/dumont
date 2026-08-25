import type { TurIntegrationAssetsSource } from "@/models/integration/integration-assets-source.model";
import { TurIntegrationAssetsSourceService } from "@/services/integration/integration-assets-source.service";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { BentoSourceList, useSourceList } from "./bento-source-list";

/** The filesystem assets source list, in bento chrome. Its console route is untouched. */
export default function IntegrationInstanceAssetsSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationAssetsSourceService(id), [id]);
  const { items, error } = useSourceList<TurIntegrationAssetsSource>(service);

  return (
    <BentoSourceList<TurIntegrationAssetsSource>
      surfaceId="assets-source"
      i18nPrefix="integration.assetsSources"
      items={items}
      error={error}
      itemKey={(source) => source.id}
      itemTitle={(source) => source.name}
      // The directory it reads is what distinguishes two asset sources, so it is the second line.
      itemDescription={(source) => source.sourceDir}
    />
  );
}
