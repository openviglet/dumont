import { Container, SectionHeader } from "@/components/brand";
import { VigletLogo } from "@/components/viglet-logo";
import { NON_GOALS, SUITE } from "@/lib/site-content";
import { cn } from "@/lib/utils";

export function Suite() {
  return (
    <section id="suite" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="The Viglet suite"
          title={
            <>
              Dumont fills the index.{" "}
              <span className="text-primary">Turing answers from it.</span>
            </>
          }
          description="Dumont works on its own against Solr or Elasticsearch. With Turing it does more: it provisions the field schema before the first document, audits the index, and borrows Turing's LLM for every AI feature."
        />

        <div className="grid gap-5 md:grid-cols-3">
          {SUITE.map((p) => {
            const self = p.id === "dumont";
            return (
              <a
                key={p.id}
                href={p.href}
                target={p.href.startsWith("http") ? "_blank" : undefined}
                rel={p.href.startsWith("http") ? "noreferrer" : undefined}
                className={cn(
                  "flex flex-col rounded-2xl border bg-card p-6 transition-colors",
                  self ? "border-primary/50 ring-1 ring-primary/20" : "border-border hover:border-primary/40"
                )}
              >
                <VigletLogo identifier={p.id} size={48} />
                <h3 className="mt-4 font-bold">{p.name}</h3>
                <p className="mt-0.5 text-xs font-semibold text-primary">{p.role}</p>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </a>
            );
          })}
        </div>

        <div className="mx-auto mt-16 max-w-4xl">
          <h3 className="text-center text-xl font-extrabold tracking-tight">Where Dumont is the wrong tool</h3>
          <p className="mt-2 text-center text-sm text-muted-foreground">
            Written down in the strategy, so nobody has to find out by trying.
          </p>
          <div className="mt-6 grid gap-4 md:grid-cols-3">
            {NON_GOALS.map((n) => (
              <div key={n.title} className="rounded-xl border border-dashed border-border bg-background p-5">
                <p className="font-bold">{n.title}</p>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{n.body}</p>
              </div>
            ))}
          </div>
        </div>
      </Container>
    </section>
  );
}
