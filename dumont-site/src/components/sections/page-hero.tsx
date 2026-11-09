import type { ReactNode } from "react";
import { Container, Eyebrow } from "@/components/brand";
import { VigletLogo } from "@/components/viglet-logo";
import { PRODUCTS } from "@/lib/site-links";

/**
 * The hero every page that is not the landing page opens on, laid out like
 * viglet.org's product page hero: the product mark beside a plain label, one
 * title scale, a muted lead and the page's actions, on the page's own ground in
 * either theme. `children` is for the one artefact the page leads with (a
 * command to copy, a pair of buttons), and most pages pass nothing.
 */
export function PageHero({
  eyebrow,
  title,
  lead,
  children,
}: {
  eyebrow: string;
  title: ReactNode;
  lead: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section id="top" className="border-b border-border bg-background py-12 md:py-16">
      <Container className="max-w-5xl">
        <div className="flex items-center gap-4">
          <VigletLogo identifier="dumont" size={48} className="shrink-0" />
          <div className="min-w-0">
            <Eyebrow>{eyebrow}</Eyebrow>
            <p className="mt-1 text-sm text-muted-foreground">{PRODUCTS.dumont.category}</p>
          </div>
        </div>
        <h1 className="mt-6 max-w-4xl text-4xl font-extrabold leading-[1.1] tracking-tight md:text-5xl">
          {title}
        </h1>
        <p className="mt-5 max-w-3xl text-lg leading-relaxed text-muted-foreground">{lead}</p>
        {children && <div className="mt-7">{children}</div>}
      </Container>
    </section>
  );
}
