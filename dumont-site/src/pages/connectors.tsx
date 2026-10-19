import { Puzzle, Sparkles } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { PageHero } from "@/components/sections/page-hero";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { Connectors } from "@/components/sections/connectors";
import { CallToAction } from "@/components/sections/cta";
import { TARGETS } from "@/lib/site-content";

export function ConnectorsPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Connectors"
          title={
            <>
              Six kinds of source,{" "}
              <span className="text-gradient">one way to run them</span>
            </>
          }
          lead="Every connector has a console screen, REST endpoints for create, update, export and import, and a JSON export package seeded on first boot. They all go through the same queue, checksums, safe finish and audit."
        />

        <Connectors detailed />

        <section className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="Targets"
              title="Where the documents go"
              description="One property chooses the engine. Schema provisioning and the index audit need the target to answer back, which today only Turing does."
            />
            <div className="grid gap-5 md:grid-cols-3">
              {TARGETS.map((t) => (
                <article key={t.property} className="rounded-2xl border border-border bg-card p-6">
                  <h3 className="font-bold">{t.name}</h3>
                  <p className="mt-1 font-mono text-xs text-primary">
                    dumont.indexing.provider={t.property}
                  </p>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{t.note}</p>
                </article>
              ))}
            </div>
          </Container>
        </section>

        <section className="py-20">
          <Container className="grid gap-5 md:grid-cols-2">
            <article className="rounded-2xl border border-border bg-card p-6">
              <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Sparkles className="size-5" />
              </div>
              <h3 className="mt-4 font-bold">Not sure how to model a source?</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                Source inference takes a URL and drafts the source and its fields with the
                LLM configured in Turing. A dry run shows the coverage and a diff; nothing is
                indexed until someone confirms the draft.
              </p>
            </article>
            <article className="rounded-2xl border border-border bg-card p-6">
              <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Puzzle className="size-5" />
              </div>
              <h3 className="mt-4 font-bold">A source nobody has written yet</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                Connectors implement one Java interface in connector-commons. Plugins can be
                uploaded, started, stopped and disabled at runtime, without rebuilding the
                connector.
              </p>
            </article>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
