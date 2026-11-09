import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow } from "@/components/brand";
import { AEM_POINTS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

/**
 * AEM gets a band of its own on the landing page because it is the deepest
 * connector and the audience most likely to arrive with a budget and a
 * deadline: an AEM site whose search is a crawler pointed at the publish tier.
 */
export function AemBand() {
  return (
    <section id="aem" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>Adobe Experience Manager</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Search for AEM that reads AEM,{" "}
            <span className="text-primary">not the HTML it renders</span>
          </h2>
          <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
            On-prem, Managed Services or AEM as a Cloud Service. Content types, fragments and
            replication events are things the connector understands, not markup it scrapes.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {AEM_POINTS.map((p) => (
            <div key={p.title} className="rounded-2xl border border-border bg-card p-5">
              <p.icon className="size-5 text-primary" />
              <h3 className="mt-3 font-bold">{p.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
            </div>
          ))}
        </div>

        <div className="mt-10 flex justify-center">
          <Button asChild size="lg">
            <a href={sitePath("/aem/")}>
              Viglet Dumont for AEM <ArrowRight className="size-4" />
            </a>
          </Button>
        </div>
      </Container>
    </section>
  );
}
