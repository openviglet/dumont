import { DUMONT_NAV } from "@/app/nav.const";

/**
 * The Module Federation wire shape for one nav entry.
 *
 * Unchanged by D39 on purpose: a host (Turing) reads this over `./manifest`, and the declaration
 * moving does not give us licence to change what the host receives. It is now *derived* from
 * `DUMONT_NAV` rather than typed out beside it, so a renamed surface can no longer mean one thing
 * in the sidebar and another on the wire.
 */
export interface DumontNavItem {
  titleKey: string;
  url: string;
  icon: string;
  order: number;
  /** If set, only show when the active connector provider matches. Omit for common items. */
  provider?: string;
}

export interface DumontManifest {
  version: number;
  navItems: DumontNavItem[];
}

/**
 * Re-exported so `./manifest` keeps offering it: the host renders Dumont's own icons without
 * maintaining a parallel icon map. The map itself lives with the declaration that uses it.
 */
export { resolveIcon } from "@/app/nav.const";

const manifest: DumontManifest = {
  version: 1,
  navItems: DUMONT_NAV.map((surface) => ({
    titleKey: surface.titleKey,
    url: surface.path,
    icon: surface.iconName,
    order: surface.order,
    ...(surface.provider ? { provider: surface.provider } : {}),
  })),
};

export default manifest;
