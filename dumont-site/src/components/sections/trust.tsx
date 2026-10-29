import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { TRUST } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

/** The operational half. `detailed` shows each feature's second line, for /trust/. */
export function Trust({ detailed = false }: { detailed?: boolean }) {
  return (
    <section id="trust" className="py-20">
      <Container>
        {!detailed && (
          <SectionHeader
            eyebrow="After the run"
            title={
              <>
                An index you can leave running{" "}
                <span className="text-primary">and still trust on Monday</span>
              </>
            }
            description="Most connectors stop when the documents are sent. Dumont keeps a record of what it sent, compares it with what the index holds, and tells you when the two disagree."
          />
        )}

        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {TRUST.map((t) => (
            <article
              key={t.id}
              id={detailed ? t.id : undefined}
              className="flex scroll-mt-24 flex-col rounded-2xl border border-border bg-card p-6"
            >
              <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <t.icon className="size-5" />
              </div>
              <h3 className="mt-4 font-bold">{t.title}</h3>
              <p className="mt-2 flex-1 text-sm leading-relaxed text-muted-foreground">{t.body}</p>
              {detailed && (
                <p className="mt-4 border-t border-border pt-3 text-xs leading-relaxed text-muted-foreground">
                  {t.detail}
                </p>
              )}
            </article>
          ))}
        </div>

        {!detailed && (
          <div className="mt-8 text-center">
            <a
              href={sitePath("/trust/")}
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline"
            >
              How each check works <ArrowRight className="size-4" />
            </a>
          </div>
        )}
      </Container>
    </section>
  );
}
