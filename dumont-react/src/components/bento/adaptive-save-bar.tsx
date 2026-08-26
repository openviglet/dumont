import { StickySaveBar } from "@viglet/viglet-design-system";
import { BentoSaveBar, useSectionChrome } from "@viglet/viglet-design-system/bento";
import type { ReactNode } from "react";

export interface AdaptiveSaveBarProps {
  title: string;
  badges?: ReactNode;
  /** The bar's buttons. Both eras render whatever is passed instead of a Save/Cancel pair. */
  actions?: ReactNode;
  loading?: boolean;
  disabled?: boolean;
}

/**
 * A form's own action bar, in whichever era it is rendered (D59).
 *
 * The indexing manager is the one form that does not save an entity — it submits an operation, so
 * its bar is Reset plus the action's own verb rather than Save and Cancel, and `AdaptiveFormHeader`
 * (which owns both halves of the bento save-bar morph) is the wrong piece for it.
 *
 * The two bars take nearly the same props, so this is a thin pick rather than a translation. What
 * it deliberately drops is each era's own extras: `variant` tints only the console bar, and
 * `dirty` / `titleMissing` are markers only the bento one draws. A form needing either of those is
 * saving an entity, which is what `AdaptiveFormHeader` is for.
 *
 * `StickySaveBar` comes straight from the package here. It used to arrive through a one-line shim
 * at `components/ui/sticky-save-bar`, which existed only so this one call site could resolve —
 * and a shim whose last consumer is an adapter is a redirection with nothing to redirect.
 */
export function AdaptiveSaveBar({
  title,
  badges,
  actions,
  loading,
  disabled,
}: Readonly<AdaptiveSaveBarProps>) {
  const chrome = useSectionChrome();
  const Bar = chrome === "bento" ? BentoSaveBar : StickySaveBar;

  return <Bar title={title} badges={badges} actions={actions} loading={loading} disabled={disabled} />;
}
