import { BentoPanel } from "@viglet/viglet-design-system/bento";
import type { ReactNode } from "react";

export interface AdaptivePanelProps {
  /** Applied to the container itself — margins, a width, `overflow-hidden`. */
  className?: string;
  /**
   * Applied to the inner wrapper, which carries **no padding of its own** — `p-6` for a
   * message, `p-0` for a table whose rows reach the edge. Naming the inset at the call site
   * is what kept the two eras rendering the same, and it is still how a panel gets one.
   */
  contentClassName?: string;
  children: ReactNode;
}

/**
 * A container with no heading (D58).
 *
 * The seven read-only Integration surfaces are panels, tables and counts under a header that
 * already names the page, so a heading on the container would be noise — which is exactly what
 * `BentoPanel` is for, and what a console `Card` with no `CardHeader` was already doing.
 *
 * It used to pick between the two off `useSectionChrome`, which is what let the seven move
 * without being rewritten. The design system removed that switch once every consumer had cut
 * over (VDS146), so only the bento arm was ever reachable and this is what is left of it.
 *
 * Pairs with {@link AdaptivePageHeader}: hero plus panels is the whole shape of a read-only page.
 */
export function AdaptivePanel({
  className,
  contentClassName,
  children,
}: Readonly<AdaptivePanelProps>) {
  return (
    <BentoPanel className={className} contentClassName={contentClassName}>
      {children}
    </BentoPanel>
  );
}
