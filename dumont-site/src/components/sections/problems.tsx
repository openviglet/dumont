import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { PROBLEMS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

/**
 * The argument of the site, in four pairs. A visitor who has run a search
 * index recognises at least one of the failures, and the answer next to it is
 * what makes Dumont a product rather than a pipe.
 */
export function Problems() {
  return (
    <section id="why" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Why it exists"
          title={
            <>
              Indexes rarely break on day one.{" "}
              <span className="text-primary">They drift.</span>
            </>
          }
          description="The four ways a search index goes wrong after launch, and what Dumont does about each one."
        />

        <div className="grid gap-5 md:grid-cols-2">
          {PROBLEMS.map((p) => (
            <article key={p.failure} className="flex flex-col rounded-2xl border border-border bg-card p-6">
              <div className="flex size-10 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
                <p.icon className="size-5" />
              </div>
              <p className="mt-4 font-bold leading-snug">{p.failure}</p>
              <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">{p.answer}</p>
            </article>
          ))}
        </div>

        <div className="mt-8 text-center">
          <a
            href={sitePath("/trust/")}
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline"
          >
            Everything Dumont checks after a run <ArrowRight className="size-4" />
          </a>
        </div>
      </Container>
    </section>
  );
}
