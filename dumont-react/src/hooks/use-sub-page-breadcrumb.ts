import { type BreadcrumbItem, useBreadcrumb } from "@/contexts/breadcrumb.context";
import { useEffect, useRef } from "react";

/**
 * Manages breadcrumb items for a sub-page, automatically inserting on mount,
 * updating when items change, and removing on unmount.
 *
 * Accepts:
 * - A string: single static label → `useSubPageBreadcrumb("Settings")`
 * - A BreadcrumbItem: label + optional href → `useSubPageBreadcrumb({ label: "Settings", href: "/..." })`
 * - An array of BreadcrumbItem: multiple levels
 * - `undefined`: deferred — items are pushed once the value becomes defined
 */
export function useSubPageBreadcrumb(
  items: string | BreadcrumbItem | BreadcrumbItem[] | undefined,
) {
  const { setItems } = useBreadcrumb();
  const ownItemsRef = useRef<BreadcrumbItem[]>([]);

  useEffect(() => {
    if (items === undefined) return;

    let normalized: BreadcrumbItem[];
    if (typeof items === "string") {
      normalized = [{ label: items }];
    } else if (Array.isArray(items)) {
      normalized = items;
    } else {
      normalized = [items];
    }

    const prev = ownItemsRef.current;

    if (
      prev.length === normalized.length &&
      prev.every((p, i) => p.label === normalized[i].label && p.href === normalized[i].href)
    ) {
      return;
    }

    setItems((breadcrumb) => {
      if (prev.length > 0) {
        const idx = breadcrumb.indexOf(prev[0]);
        if (idx >= 0) {
          return [
            ...breadcrumb.slice(0, idx),
            ...normalized,
            ...breadcrumb.slice(idx + prev.length),
          ];
        }
      }
      if (
        breadcrumb.length > 0 &&
        breadcrumb[breadcrumb.length - 1].label === normalized[0]?.label
      ) {
        return breadcrumb;
      }
      return [...breadcrumb, ...normalized];
    });

    ownItemsRef.current = normalized;
  }, [items, setItems]);

  useEffect(() => {
    return () => {
      const own = ownItemsRef.current;
      if (own.length > 0) {
        setItems((breadcrumb) => {
          const idx = breadcrumb.indexOf(own[0]);
          if (idx >= 0) {
            return [
              ...breadcrumb.slice(0, idx),
              ...breadcrumb.slice(idx + own.length),
            ];
          }
          return breadcrumb;
        });
      }
      ownItemsRef.current = [];
    };
  }, [setItems]);
}
