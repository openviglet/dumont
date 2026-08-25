import type { TurIntegrationWcSource } from "@/models/integration/integration-wc-source.model";
import { TurIntegrationWcSourceService } from "@/services/integration/integration-wc-source.service";
import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { BentoSourceList, useSourceList } from "./bento-source-list";

/** The web crawler source list, in bento chrome. Its console route is untouched. */
export default function IntegrationInstanceWcSourceListBentoPage() {
  const { id } = useParams() as { id: string };
  const service = useMemo(() => new TurIntegrationWcSourceService(id), [id]);
  const { items, error } = useSourceList<TurIntegrationWcSource>(service);

  return (
    <BentoSourceList<TurIntegrationWcSource>
      surfaceId="wc-source"
      i18nPrefix="integration.wcSources"
      items={items}
      error={error}
      itemKey={(source) => source.id}
      // A crawler is named by its `title` and identified by the URL it starts from — the console
      // card shows the same two fields.
      itemTitle={(source) => source.title}
      itemDescription={(source) => source.url}
    />
  );
}
