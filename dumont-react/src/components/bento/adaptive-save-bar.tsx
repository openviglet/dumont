import { BentoSaveBar } from "@viglet/viglet-design-system/bento";
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
 * A form's own action bar (D59).
 *
 * The indexing manager is the one form that does not save an entity — it submits an operation, so
 * its bar is Reset plus the action's own verb rather than Save and Cancel, and `AdaptiveFormHeader`
 * (which owns both halves of the bento save-bar morph) is the wrong piece for it.
 *
 * It used to pick between `BentoSaveBar` and the console's `StickySaveBar` off
 * `useSectionChrome`; the design system removed that switch once every consumer had cut over
 * (VDS146). What it still drops is the bento bar's own extras: `dirty` and `titleMissing` are
 * markers a form saving an entity wants, and that form is what `AdaptiveFormHeader` is for.
 */
export function AdaptiveSaveBar({
  title,
  badges,
  actions,
  loading,
  disabled,
}: Readonly<AdaptiveSaveBarProps>) {
  return (
    <BentoSaveBar title={title} badges={badges} actions={actions} loading={loading} disabled={disabled} />
  );
}
