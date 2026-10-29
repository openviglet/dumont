import { ArrowRight, Check } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { CONNECTORS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

/** The six source cards. `detailed` adds each connector's points, for /connectors/. */
export function Connectors({ detailed = false }: { detailed?: boolean }) {
  return (
    <section id="connectors" className="py-20">
      <Container>
        {!detailed && (
          <SectionHeader
            eyebrow="Connectors"
            title={
              <>
                Where your content lives today,{" "}
                <span className="text-primary">read the way it wants to be read</span>
              </>
            }
            description="Each connector has its own console screen, its own REST endpoints and an export package, so a source configured once can be moved between environments as a file."
          />
        )}

        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {CONNECTORS.map((c) => {
            const Card = detailed ? "article" : "a";
            return (
              <Card
                key={c.id}
                id={detailed ? c.id : undefined}
                href={detailed ? undefined : sitePath(`/connectors/#${c.id}`)}
                className="group flex scroll-mt-24 flex-col rounded-2xl border border-border bg-card p-6 transition-all hover:border-primary/40 hover:shadow-lg"
              >
                <div className="flex size-11 items-center justify-center rounded-xl bg-gradient-to-br from-[#0f766e] to-[#0e7490] text-white shadow-sm">
                  <c.icon className="size-5" />
                </div>
                <h3 className="mt-4 text-lg font-bold">{c.name}</h3>
                <p className="mt-0.5 text-xs font-medium text-muted-foreground/90">{c.short}</p>
                <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">{c.body}</p>
                {detailed ? (
                  <ul className="mt-4 space-y-2 border-t border-border pt-4">
                    {c.points.map((pt) => (
                      <li key={pt} className="flex gap-2 text-sm leading-snug">
                        <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                        {pt}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                    Details
                    <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
                  </span>
                )}
              </Card>
            );
          })}
        </div>
      </Container>
    </section>
  );
}
