import axios from "axios";
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type PluginProvider = "AEM" | "JDBC-DATABASE" | "WEB-CRAWLER" | "ASSETS" | "EDS" | string;

interface PluginContextValue {
  /** All active connector providers (multiple plugins can run in one connector instance). */
  providers: PluginProvider[];
  /** First active provider, for backward compatibility. Null when none active. */
  provider: PluginProvider | null;
  loading: boolean;
}

const PluginContext = createContext<PluginContextValue>({
  providers: [],
  provider: null,
  loading: true,
});

export function PluginProvider({ children }: { children: ReactNode }) {
  const [providers, setProviders] = useState<PluginProvider[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios
      .get<{ status: string; provider?: string; providers?: string[] }>("/v2/connector/status")
      .then(({ data }) => {
        // Prefer the multi-provider list; fall back to the legacy single `provider`.
        const list = data.providers ?? (data.provider ? [data.provider] : []);
        setProviders(list);
      })
      .catch(() => setProviders([]))
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<PluginContextValue>(
    () => ({ providers, provider: providers[0] ?? null, loading }),
    [providers, loading]
  );

  return <PluginContext value={value}>{children}</PluginContext>;
}

export function usePlugin(): PluginContextValue {
  return useContext(PluginContext);
}
