import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { PageHero } from "@/components/sections/page-hero";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { CallToAction } from "@/components/sections/cta";
import { AEM_POINTS, LINKS } from "@/lib/site-content";

const DEPLOYMENTS = [
  {
    name: "On-premise and Managed Services",
    reads: "Author servlets: infinity.json and QueryBuilder.",
    needs: "An author account the connector can read with.",
  },
  {
    name: "AEM as a Cloud Service",
    reads: "Delivery APIs: model.json for pages, the Content Fragment API, Assets Search.",
    needs: "No author credential.",
  },
  {
    name: "Edge Delivery Services",
    reads: "The site's query-index.json, and its sitemap if you want it.",
    needs: "The public aem.live address.",
  },
];

const CHANNELS = [
  {
    name: "Adobe I/O Events",
    body: "A webhook per source answers the Developer Console's verification challenge and takes publish, modify, unpublish and delete events as they happen. The low-latency path.",
  },
  {
    name: "The AEM-side bundle",
    body: "An OSGi bundle installed in AEM sends page and Content Fragment replication events to Dumont. For instances where Adobe I/O Events is not an option.",
  },
  {
    name: "The journal poller",
    body: "Reads the event journal on a schedule and recovers whatever a push dropped. Off by default; turn it on when a missed event is more expensive than a poll.",
  },
];

const WEBHOOK = `# Adobe Developer Console → Events → Webhook URL
POST https://dumont.example.com/api/v2/aem/webhook/{source}

# The AEM-side bundle, or anything else that knows a path changed
POST https://dumont.example.com/api/v2/aem/index/{source}`;

export function AemPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Dumont for Adobe Experience Manager"
          title={
            <>
              AEM content in your search index,{" "}
              <span className="text-gradient">the moment it is published</span>
            </>
          }
          lead="Pages, Content Fragments, Experience Fragments and assets, read through AEM's own APIs and kept in step through replication events. For AEM on-prem, Managed Services, AEM as a Cloud Service and Edge Delivery Services."
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={LINKS.install}>
                Install it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="/marketplace/wknd/">See the WKND sample</a>
            </Button>
          </div>
        </PageHero>

        <section className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Every AEM you might be running"
              title="One connector, three deployment models"
              description="A source says which AEM it talks to, and the connector reads it the way that deployment allows."
            />
            <div className="grid gap-5 md:grid-cols-3">
              {DEPLOYMENTS.map((d) => (
                <article key={d.name} className="rounded-2xl border border-border bg-card p-6">
                  <h3 className="font-bold">{d.name}</h3>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{d.reads}</p>
                  <p className="mt-4 flex gap-2 border-t border-border pt-3 text-sm font-medium">
                    <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                    {d.needs}
                  </p>
                </article>
              ))}
            </div>
          </Container>
        </section>

        <section className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="What it understands"
              title={
                <>
                  Built around AEM's content model,{" "}
                  <span className="text-primary">not its HTML</span>
                </>
              }
            />
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {AEM_POINTS.map((p) => (
                <div key={p.title} className="rounded-2xl border border-border bg-card p-6">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <p.icon className="size-5" />
                  </div>
                  <h3 className="mt-4 font-bold">{p.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
                </div>
              ))}
            </div>
          </Container>
        </section>

        <section className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Keeping up"
              title={
                <>
                  Three ways to hear about a change,{" "}
                  <span className="text-primary">so none is the single point of failure</span>
                </>
              }
              description="All three feed the same event dispatcher, an unchanged page is skipped by its checksum, and a page none of them reported is caught by the nightly audit."
            />
            <div className="grid items-start gap-8 lg:grid-cols-2 [&>*]:min-w-0">
              <ol className="space-y-4">
                {CHANNELS.map((c, i) => (
                  <li key={c.name} className="flex gap-4 rounded-xl border border-border bg-card p-5">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 font-mono text-sm font-bold text-primary">
                      {i + 1}
                    </span>
                    <div className="min-w-0">
                      <h3 className="font-bold">{c.name}</h3>
                      <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{c.body}</p>
                    </div>
                  </li>
                ))}
              </ol>
              <div>
                <CodeBlock caption="where events go" code={WEBHOOK} />
                <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
                  When the event is for a shared Experience Fragment or Content Fragment,
                  Dumont works out every page that embeds it and reindexes those too, so a
                  changed footer or product description reaches search without a full
                  reindex.
                </p>
                <a
                  href="/trust/"
                  className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:underline"
                >
                  What the nightly audit checks <ArrowRight className="size-4" />
                </a>
              </div>
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
