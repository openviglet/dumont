import type { TurIntegrationEdsSource } from "@/models/integration/integration-eds-source.model";
import { TurIntegrationEdsSourceService } from "@/services/integration/integration-eds-source.service";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { BentoSourceList, useSourceList } from "./bento-source-list";

/** The Edge Delivery Services source list, in bento chrome. Its console route is untouched. */
export default function IntegrationInstanceEdsSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationEdsSourceService(id), [id]);
  const { items, error } = useSourceList<TurIntegrationEdsSource>(service);

  return (
    <BentoSourceList<TurIntegrationEdsSource>
      surfaceId="eds-source"
      i18nPrefix="integration.edsSources"
      items={items}
      error={error}
      itemKey={(source) => source.id}
      itemTitle={(source) => source.title}
      itemDescription={(source) => source.baseUrl}
    />
  );
}
