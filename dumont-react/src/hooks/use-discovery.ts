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
    .get<DumDiscoveryAPI>("/v2/discovery")
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

  /*
   * D53 — one path, whether the cache is warm or cold.
   *
   * There used to be a branch that read the cache and wrote it straight into state, which is a
   * synchronous write in an effect body and depended on `data` to re-run — an effect that sets the
   * value it watches. `fetchDiscovery` already answers a warm cache with a resolved promise, so
   * going through it always defers the write by a microtask and the branch buys nothing.
   */
  useEffect(() => {
    let alive = true;
    fetchDiscovery()
      .then((d) => {
        if (alive) setData(d);
      })
      .catch(() => {
        // discovery failed — keep undefined, components fall back to defaults
      });
    return () => {
      alive = false;
    };
  }, []);

  return data;
}
