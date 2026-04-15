import { useEffect, useState } from "react";
import axios from "axios";
import type { DumDiscoveryAPI } from "@/models/discovery";

/**
 * Module-level cache so multiple components (header, sidebar, admin settings,
 * …) don't each issue their own request to /discovery.
 */
let cached: DumDiscoveryAPI | undefined;
let inflight: Promise<DumDiscoveryAPI> | undefined;

function fetchDiscovery(): Promise<DumDiscoveryAPI> {
  if (cached) return Promise.resolve(cached);
  inflight ??= axios
    .get<DumDiscoveryAPI>("/api/v2/discovery")
    .then(({ data }) => {
      cached = data;
      inflight = undefined;
      return data;
    })
    .catch((err) => {
      inflight = undefined;
      throw err;
    });
  return inflight;
}

/**
 * Read the cached discovery response. Fetches once on first use.
 * Returns `undefined` while loading, so callers can render defaults.
 */
export function useDiscovery(): DumDiscoveryAPI | undefined {
  const [data, setData] = useState<DumDiscoveryAPI | undefined>(cached);

  useEffect(() => {
    if (cached) {
      if (data !== cached) setData(cached);
      return;
    }
    let alive = true;
    fetchDiscovery()
      .then((d) => {
        if (alive) setData(d);
      })
      .catch(() => {
        // discovery failed — keep undefined, components fall back to defaults
      });
  }, [data]);

  return data;
}
