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

/**
 * One resolved lookup, kept against the id that produced it (D66).
 *
 * Storing the answer with its question is what removes the two synchronous writes this effect
 * used to make. `loading` stops being state — it is "no answer for the id being asked about
 * yet" — so there is nothing to set on entry, and an answer for a previous id can never be
 * read as this one's, which is the stale render the old reset was there to prevent.
 */
interface ResolvedProvider {
  integrationId: string;
  provider: string | null;
}

export function useConnectorProvider(
  integrationId: string | undefined,
): ConnectorProviderState {
  const [resolved, setResolved] = useState<ResolvedProvider | null>(null);

  useEffect(() => {
    if (!integrationId) {
      return;
    }

    let active = true;

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
        if (active) setResolved({ integrationId, provider });
      })
      .catch((error) => {
        console.error("Failed to resolve connector provider:", error);
        // A failed lookup is still an answer for this id: the provider is unknown, and the
        // caller must stop waiting rather than spin.
        if (active) setResolved({ integrationId, provider: null });
      });

    return () => {
      active = false;
    };
  }, [integrationId]);

  // Derived, not stored: with no id there is nothing to wait for, and otherwise we are waiting
  // until the answer in hand is the answer to *this* id.
  if (!integrationId) {
    return { provider: null, loading: false };
  }
  return resolved?.integrationId === integrationId
    ? { provider: resolved.provider, loading: false }
    : { provider: null, loading: true };
}
