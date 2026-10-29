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
    <section id="aem" className="relative isolate overflow-hidden bg-[#081615] py-20 text-[#e8f5f3]">
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-b pointer-events-none absolute -bottom-40 -left-24 -z-10 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,#14b8a6,transparent_70%)] opacity-30 blur-[70px]" />
      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>Adobe Experience Manager</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Search for AEM that reads AEM,{" "}
            <span className="text-gradient">not the HTML it renders</span>
          </h2>
          <p className="mt-3 text-base leading-relaxed text-[#a9c4c0] sm:text-lg">
            On-prem, Managed Services or AEM as a Cloud Service. Content types, fragments and
            replication events are things the connector understands, not markup it scrapes.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {AEM_POINTS.map((p) => (
            <div key={p.title} className="rounded-2xl border border-white/10 bg-white/[0.04] p-5">
              <p.icon className="size-5 text-[#5eead4]" />
              <h3 className="mt-3 font-bold">{p.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-[#a9c4c0]">{p.body}</p>
            </div>
          ))}
        </div>

        <div className="mt-10 flex justify-center">
          <Button asChild size="lg">
            <a href={sitePath("/aem/")}>
              Dumont for AEM <ArrowRight className="size-4" />
            </a>
          </Button>
        </div>
      </Container>
    </section>
  );
}
