import { SubPageHeader } from "@/components/sub.page.header";
import { BentoHero, useSectionChrome } from "@viglet/viglet-design-system/bento";
import type { ReactNode } from "react";

export interface AdaptivePageHeaderProps {
  icon: React.ElementType;
  /** The surface's name — "AI Source Inference". The console header's headline. */
  feature: string;
  /** What this particular page is. Usually the same as `feature` on a singleton surface. */
  name: string;
  description: string;
  /** Where the breadcrumb goes back to. The console links its headline; bento draws an eyebrow. */
  urlBase?: string;
  /** Header-level actions — chips, buttons. Trailing in both eras. */
  children?: ReactNode;
}

/**
 * One page header for a surface that is not a form, in whichever era it is rendered (D57).
 *
 * `AdaptiveFormHeader` is the same idea for the six source forms, and it carries a save bar
 * because a form has one. Most of what is left to migrate does not: a wizard, a panel, a table.
 * Those pages differ between the eras only in the header and the section cards, so they take the
 * D47 shape too — the console page under a `chrome="bento"` declaration — and this is the header
 * half of it.
 *
 * The package's own deprecation notice on `SubPageHeader` names `BentoHero` as the replacement,
 * which is what this picks between. `useSectionChrome` answers from the nearest
 * `SectionCardChromeProvider` and defaults to `console`, so a page that knows nothing about this
 * renders exactly what it always did.
 */
export function AdaptivePageHeader({
  icon: Icon,
  feature,
  name,
  description,
  urlBase,
  children,
}: Readonly<AdaptivePageHeaderProps>) {
  const chrome = useSectionChrome();

  if (chrome === "bento") {
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

  return (
    <SubPageHeader icon={Icon} feature={feature} name={name} description={description} urlBase={urlBase}>
      {children}
    </SubPageHeader>
  );
}
