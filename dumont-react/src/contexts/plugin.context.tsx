import axios from "axios";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

export type PluginProvider = "AEM" | "JDBC-DATABASE" | "WEB-CRAWLER" | string;

interface PluginContextValue {
  provider: PluginProvider | null;
  loading: boolean;
}

const PluginContext = createContext<PluginContextValue>({ provider: null, loading: true });

export function PluginProvider({ children }: { children: ReactNode }) {
  const [provider, setProvider] = useState<PluginProvider | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axios
      .get<{ status: string; provider: string }>("/api/v2/connector/status")
      .then(({ data }) => setProvider(data.provider))
      .catch(() => setProvider(null))
      .finally(() => setLoading(false));
  }, []);

  return (
    <PluginContext value={{ provider, loading }}>
      {children}
    </PluginContext>
  );
}

export function usePlugin(): PluginContextValue {
  return useContext(PluginContext);
}
