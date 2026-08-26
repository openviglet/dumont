import { Card, CardContent } from "@/components/ui/card";
import { BentoPanel, useSectionChrome } from "@viglet/viglet-design-system/bento";
import type { ReactNode } from "react";

export interface AdaptivePanelProps {
  /** Applied to the container itself — margins, a width, `overflow-hidden`. */
  className?: string;
  /**
   * Applied to the inner wrapper. **Always pass the inset explicitly** — `p-6` for a message,
   * `p-0` for a table whose rows reach the edge. The two eras disagree on the default:
   * `BentoPanel`'s content wrapper has none, and `CardContent`'s is `px-6`. Naming it is what
   * makes the two render the same, and `tailwind-merge` lets it beat the console default.
   */
  contentClassName?: string;
  children: ReactNode;
}

/**
 * A container with no heading, in whichever era it is rendered (D58).
 *
 * The seven read-only Integration surfaces are panels, tables and counts under a header that
 * already names the page, so a heading on the container would be noise — which is exactly what
 * `BentoPanel` is for, and what a console `Card` with no `CardHeader` was already doing.
 *
 * Console keeps `Card` + `CardContent` rather than `Card` alone because the two carry the inset
 * between them: `Card` has `py-6` and `CardContent` has `px-6`, so the pair is what a page's
 * `<Card className="p-6">` actually looked like. Every call site still names its own inset —
 * see `contentClassName`.
 *
 * Pairs with {@link AdaptivePageHeader}: hero plus panels is the whole shape of a read-only page,
 * which is why neither of the seven had to be rewritten to move.
 */
export function AdaptivePanel({
  className,
  contentClassName,
  children,
}: Readonly<AdaptivePanelProps>) {
  const chrome = useSectionChrome();

  if (chrome === "bento") {
    return (
      <BentoPanel className={className} contentClassName={contentClassName}>
        {children}
      </BentoPanel>
    );
  }

  return (
    <Card className={className}>
      <CardContent className={contentClassName}>{children}</CardContent>
    </Card>
  );
}
