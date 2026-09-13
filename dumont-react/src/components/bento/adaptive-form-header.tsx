import { BentoActionsMenu, BentoFormHero } from "@viglet/viglet-design-system/bento";
import type { ComponentType, ReactNode } from "react";

/**
 * A secondary action — Export, Dry scan, Index all, Delete.
 *
 * Kept as a declaration rather than as markup because the hero collects these into its own `⋮`
 * menu: handing `BentoActionsMenu` a button a caller drew would render it outside the menu, or
 * not at all.
 */
export interface AdaptiveFormAction {
  label: string;
  icon: ComponentType<{ size?: number }>;
  onSelect: () => void;
  disabled?: boolean;
  /** Tints the item. */
  tone?: "default" | "destructive";
}

export interface AdaptiveFormHeaderProps {
  icon: React.ElementType;
  /** The surface's name — "Database Sources". Drawn as the eyebrow, beside `urlBase`. */
  feature: string;
  description: string;
  /** The entity's own name, watched from the form. The hero's headline. */
  title?: string;
  /** Where the eyebrow's back-link goes. Without it the hero draws no eyebrow. */
  urlBase?: string;
  /** Where Cancel goes. */
  onCancel: () => void;
  loading?: boolean;
  /** There is something to save. Save is disabled without it. */
  dirty?: boolean;
  /** The entity has no name yet — Save is refused, and the bar says why. */
  titleMissing?: boolean;
  /**
   * Everything that is not Save or Cancel: the delete dialog, Index all, Reindex all.
   *
   * Kept out of the save bar on purpose. The bar is Save and Cancel only — a destructive action
   * inside the fade-out group disappears as the reader scrolls, and a controlled delete dialog
   * rendered in both halves of the morph opens two modals at once.
   */
  actions?: ReactNode;
  /** Secondary actions, rendered behind a `⋮`. */
  menuActions?: AdaptiveFormAction[];
}

/**
 * One form header (D47).
 *
 * The six source forms are 500-line files of real field logic, and the header was the only part of
 * them that differed between the two chromes. Forking them to migrate the detail pages would have
 * meant two copies of that logic drifting for the length of the migration, which is the cost the
 * parallel tree exists to avoid — so the form was written once and the header asked, through
 * `useSectionChrome`, which era it was in.
 *
 * D88 retired the console-era answer. `BentoFormHero` is what every tree renders now, and this
 * stays because the two are not the same shape: the console header took Save and Cancel as
 * children among the rest, while `BentoFormHero` owns both halves of the save-bar morph itself and
 * must not be handed a second pair. Declaring the actions rather than passing them through is what
 * kept that invariant, and it is still what keeps it.
 */
export function AdaptiveFormHeader({
  icon: Icon,
  feature,
  description,
  title,
  urlBase,
  onCancel,
  loading,
  dirty,
  titleMissing,
  actions,
  menuActions = [],
}: Readonly<AdaptiveFormHeaderProps>) {
  return (
    <BentoFormHero
      backTo={urlBase}
      backLabel={feature}
      leading={<Icon size={28} aria-hidden />}
      title={title?.trim() ? title : feature}
      subtitle={description}
      trailing={
        <>
          {actions}
          {menuActions.length > 0 && <BentoActionsMenu actions={menuActions} />}
        </>
      }
      onCancel={onCancel}
      loading={loading}
      dirty={dirty}
      titleMissing={titleMissing}
      stickyTitle={title?.trim() ? title : feature}
    />
  );
}
