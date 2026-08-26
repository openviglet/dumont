import { TurIntegrationConnectorService } from "@/services/integration/integration-connector.service";
import { useEffect, useState } from "react";

/**
 * Active connector provider of an integration instance ("AEM", "JDBC-DATABASE",
 * "WEB-CRAWLER", "ASSETS"), read from `/v2/integration/{id}/connector/status`.
 *
 * A Dumont instance runs a single connector plugin, so pages must not call the
 * source APIs of the other plugins — those endpoints simply do not exist on the
 * running instance. This mirrors how the Turing host resolves the provider to
 * filter the Dumont nav manifest.
 *
 * The in-flight promise is shared per integration id so several components can
 * ask for the provider without duplicating the request.
 */
const providerCache = new Map<string, Promise<string | null>>();

export interface ConnectorProviderState {
  provider: string | null;
  loading: boolean;
}

export function useConnectorProvider(
  integrationId: string | undefined,
): ConnectorProviderState {
  const [state, setState] = useState<ConnectorProviderState>({
    provider: null,
    loading: true,
  });

  useEffect(() => {
    if (!integrationId) {
      setState({ provider: null, loading: false });
      return;
    }

    let active = true;
    setState({ provider: null, loading: true });

    let request = providerCache.get(integrationId);
    if (!request) {
      request = new TurIntegrationConnectorService(integrationId)
        .status()
        .then(({ provider }) => provider ?? null);
      // A failed lookup must not be cached, otherwise the page stays broken
      // until a full reload.
      request.catch(() => providerCache.delete(integrationId));
      providerCache.set(integrationId, request);
    }

    request
      .then((provider) => {
        if (active) setState({ provider, loading: false });
      })
      .catch((error) => {
        console.error("Failed to resolve connector provider:", error);
        if (active) setState({ provider: null, loading: false });
      });

    return () => {
      active = false;
    };
  }, [integrationId]);

  return state;
}
