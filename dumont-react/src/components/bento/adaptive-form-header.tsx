import { GradientButton } from "@/components/ui/gradient-button";
import { IconDeviceFloppy, IconX } from "@tabler/icons-react";
import { StickyPageHeader } from "@viglet/viglet-design-system/router";
import {
  BentoActionsMenu,
  BentoFormHero,
  useSectionChrome,
} from "@viglet/viglet-design-system/bento";
import type { ComponentType, ReactNode } from "react";
import { useTranslation } from "react-i18next";

/**
 * A secondary action — Export, Dry scan, Index all, Delete.
 *
 * Both eras hide these behind a `⋮` menu, so the two headers agree on the shape and differ only
 * in which component draws it: `StickyPageHeader.Action` is a marker the console header collects
 * into its dropdown, and `BentoActionsMenu` is the bento equivalent. Passing the console markers
 * straight through as bento `trailing` would render them as nothing.
 */
export interface AdaptiveFormAction {
  label: string;
  icon: ComponentType<{ size?: number }>;
  onSelect: () => void;
  disabled?: boolean;
  /** Tints the item in bento chrome; the console dropdown has no tone of its own. */
  tone?: "default" | "destructive";
}

export interface AdaptiveFormHeaderProps {
  icon: React.ElementType;
  /** The surface's name — "Database Sources". The console header's headline. */
  feature: string;
  description: string;
  /** The entity's own name, watched from the form. Becomes the bento hero's headline. */
  title?: string;
  /** Console only: wraps the headline in a link back to the list. */
  urlBase?: string;
  /** Where Cancel goes. */
  onCancel: () => void;
  loading?: boolean;
  /** There is something to save. Bento disables Save without it; the console never did. */
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
  /** Secondary actions, rendered behind a `⋮` in both eras. */
  menuActions?: AdaptiveFormAction[];
}

/**
 * One form header, in whichever era it is rendered (D47).
 *
 * The six source forms are 500-line files of real field logic, and the only part of them that
 * differs between the two chromes is this header. Forking them to migrate the detail pages would
 * mean two copies of that logic drifting for the length of the migration, which is the cost the
 * parallel tree exists to avoid — so instead the form is written once and the header asks which
 * era it is in.
 *
 * `useSectionChrome` answers from the nearest `SectionCardChromeProvider`, and the default is
 * `console`, so a form that knows nothing about this behaves exactly as it always did.
 *
 * The two headers are not the same shape, which is why this is a declarative component rather
 * than a passthrough: the console header takes Save and Cancel as children among the rest, and
 * `BentoFormHero` owns both halves of the save-bar morph itself and must not be handed a second
 * pair.
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
  const { t } = useTranslation();
  const chrome = useSectionChrome();

  if (chrome === "bento") {
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

  return (
    <StickyPageHeader>
      <StickyPageHeader.Title
        icon={Icon}
        feature={feature}
        description={description}
        urlBase={urlBase}
      />
      <StickyPageHeader.Actions>
        {menuActions.map((action) => (
          <StickyPageHeader.Action
            key={action.label}
            label={action.label}
            icon={action.icon}
            onClick={action.onSelect}
            disabled={action.disabled}
          />
        ))}
        {actions}
        <GradientButton type="submit" size="sm" loading={loading}>
          <IconDeviceFloppy className="size-4" />
          {t("forms.formActions.saveChanges")}
        </GradientButton>
        <GradientButton type="button" variant="outline" size="sm" onClick={onCancel}>
          <IconX className="size-4" />
          {t("forms.formActions.cancel")}
        </GradientButton>
      </StickyPageHeader.Actions>
    </StickyPageHeader>
  );
}
