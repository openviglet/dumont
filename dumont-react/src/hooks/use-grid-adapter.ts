import type { TurGridItem } from "@/models/ui/grid-item";
import { useMemo } from "react";
type FieldExtractor<T> = keyof T | ((item: T) => string);

interface GridAdapterConfig<T> {
  id?: keyof T | ((item: T) => string | number);
  name: FieldExtractor<T>;
  description: FieldExtractor<T>;
  url: (item: T) => string;
  icon?: FieldExtractor<T>;
}

// viglet-ds-allow-duplicate useGridAdapter -- adapts to Dumont's own TurGridItem model
export function useGridAdapter<T>(
  data: T[] | undefined | null,
  config: GridAdapterConfig<T>,
): TurGridItem[] {
  return useMemo(() => {
    if (!Array.isArray(data) || data.length === 0) return [];

    const resolveField = (item: T, extractor: FieldExtractor<T>) => {
      if (typeof extractor === "function") {
        return extractor(item);
      }
      const value = item[extractor];
      return value === null || value === undefined ? "" : String(value);
    };

    /*
     * D54 — the id, without `any`.
     *
     * `TurGridItem.id` is a string, and the three ways of producing one are: the caller's
     * function, the caller's key, or the conventional `id` field on an item whose type does not
     * declare one. The last is the reason this was `any` — it is a *lookup on an unknown shape*,
     * which `Record<string, unknown>` states honestly while `any` also switched off checking of
     * everything downstream of the call.
     */
    const resolveId = (item: T): string => {
      if (!config.id) {
        const value = (item as Record<string, unknown>).id;
        return value === null || value === undefined ? "" : String(value);
      }
      if (typeof config.id === "function") {
        return String(config.id(item));
      }
      return String(item[config.id]);
    };

    return data.map((item) => ({
      id: resolveId(item),
      name: resolveField(item, config.name),
      description: resolveField(item, config.description),
      url: config.url(item),
      icon: config.icon ? resolveField(item, config.icon) || null : null,
    }));
  }, [data]);
}
