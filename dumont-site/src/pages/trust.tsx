import { Container, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { PageHero } from "@/components/sections/page-hero";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { Trust } from "@/components/sections/trust";
import { CallToAction } from "@/components/sections/cta";

const METRICS = `# GET http://dumont:30131/actuator/prometheus
dumont_run_last_status{source="wknd",provider="turing"}        1
dumont_run_last_finished_seconds{source="wknd",provider="turing"}
dumont_run_last_duration_seconds{source="wknd",provider="turing"}
dumont_run_last_documents{source="wknd",provider="turing"}
dumont_runs_total{outcome="complete",source="wknd",provider="turing"}

# alert when the nightly run is stale
time() - dumont_run_last_finished_seconds > 26 * 3600`;

const RULES = [
  "A run that was interrupted removes nothing.",
  "A run that finished partially removes nothing.",
  "A run in which at least half the items failed removes nothing.",
  "A crawl that indexed nothing keeps the index it had.",
  "Documents that left the source are reported first, and removed only when a person applies the report.",
];

export function TrustPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="An index you can trust"
          title={
            <>
              Sending documents is the easy half.{" "}
              <span className="text-gradient">Dumont also checks them.</span>
            </>
          }
          lead="It keeps a record of every item it sent, compares that record with what the index holds and with what the source still has, and reports the differences before it acts on them."
        />

        <section className="py-20">
          <Container className="max-w-3xl">
            <SectionHeader
              eyebrow="The safe finish"
              title="When Dumont will not delete anything"
              description="Removing documents is the one step that can make search worse than doing nothing, so it is the step with the most conditions."
            />
            <ul className="space-y-3">
              {RULES.map((r) => (
                <li key={r} className="rounded-xl border border-border bg-card px-5 py-4 text-sm font-medium">
                  {r}
                </li>
              ))}
            </ul>
          </Container>
        </section>

        <div className="border-y border-border bg-muted/40">
          <Trust detailed />
        </div>

        <section className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Watch it from outside"
              title="Run outcomes as Prometheus metrics"
              description="On the management port only, never the public one, tagged by source and provider. The question an on-call engineer asks first is whether last night's run happened."
            />
            <div className="mx-auto max-w-3xl">
              <CodeBlock caption="prometheus" code={METRICS} />
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
