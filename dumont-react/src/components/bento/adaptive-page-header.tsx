import { BentoHero } from "@viglet/viglet-design-system/bento";
import type { ReactNode } from "react";

export interface AdaptivePageHeaderProps {
  icon: React.ElementType;
  /** The surface's name — "AI Source Inference". Drawn as the eyebrow, beside `urlBase`. */
  feature: string;
  /** What this particular page is. Usually the same as `feature` on a singleton surface. */
  name: string;
  description: string;
  /** Where the eyebrow's back-link goes. Without it the hero draws no eyebrow. */
  urlBase?: string;
  /** Header-level actions — chips, buttons. Trailing. */
  children?: ReactNode;
}

/**
 * One page header for a surface that is not a form (D57).
 *
 * `AdaptiveFormHeader` is the same idea for the six source forms, and it carries a save bar
 * because a form has one. Most of what this console renders does not: a wizard, a panel, a table.
 *
 * It was written to pick between `SubPageHeader` and `BentoHero` off `useSectionChrome`, which is
 * what let a page serve on both route trees while only one of them had moved. D88 retired the
 * console-era half: every surface that reaches this now renders bento chrome on either tree, so
 * there is nothing left to pick between and the branch is gone rather than kept at one arm. What
 * survives is the mapping — Dumont says `icon`/`feature`/`name`/`description` where the hero says
 * `leading`/`backLabel`/`title`/`subtitle`, and twenty-odd call sites say it once here.
 */
export function AdaptivePageHeader({
  icon: Icon,
  feature,
  name,
  description,
  urlBase,
  children,
}: Readonly<AdaptivePageHeaderProps>) {
  return (
    <BentoHero
      backTo={urlBase}
      backLabel={feature}
      leading={<Icon size={28} aria-hidden />}
      title={name}
      subtitle={description}
      trailing={children}
    />
  );
}
