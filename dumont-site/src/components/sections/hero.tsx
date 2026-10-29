import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { RunReport } from "@/components/run-report";
import { DOCKER_RUN, LINKS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

const PROMISES = [
  "A failed run never empties the index",
  "Deletions wait for a person",
  "Self-hosted, Apache 2.0",
];

export function Hero() {
  return (
    <section id="top" className="relative isolate overflow-hidden bg-[#081615] text-[#e8f5f3]">
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-a pointer-events-none absolute -top-44 -left-20 -z-10 h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,#14b8a6,transparent_70%)] opacity-40 blur-[70px]" />
      <div className="animate-orb-b pointer-events-none absolute -top-28 -right-24 -z-10 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,#38bdf8,transparent_70%)] opacity-25 blur-[70px]" />

      <Container className="grid grid-cols-1 items-center gap-12 py-20 lg:grid-cols-[1.05fr_0.95fr] lg:py-28">
        <div className="min-w-0">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 font-mono text-xs font-semibold text-[#b5d0cc]">
            <span className="size-1.5 rounded-full bg-[#7dd3a0] shadow-[0_0_8px] shadow-[#7dd3a0]" />
            Open source &middot; Self-hosted &middot; AEM &middot; Solr &middot; Elasticsearch
          </span>

          <h1 className="text-4xl font-extrabold leading-[1.06] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            Your content in your search index.{" "}
            <span className="text-gradient">And kept there, correct.</span>
          </h1>

          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#a9c4c0]">
            Viglet Dumont connects AEM, Edge Delivery Services, databases, file shares,
            web sites and JSON APIs to Turing ES, Solr or Elasticsearch. Then it keeps
            checking: what changed, what disappeared, which field stopped arriving.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#86a5a1]">
            Getting documents into an index is a weekend script. Keeping the index true to
            the source for years, through outages, template changes and unpublished
            pages, is the part Dumont is built for.
          </p>

          <div className="mt-7 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#self-host">
                Run it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href={sitePath("/aem/")}>Dumont for AEM</a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="text-[#a9c4c0] hover:bg-white/10 hover:text-white"
            >
              <a href={LINKS.docs}>Docs</a>
            </Button>
          </div>

          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#86a5a1]">
            {PROMISES.map((item) => (
              <span key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-4 text-[#7dd3a0]" />
                {item}
              </span>
            ))}
          </div>

          <div className="mt-8">
            <CopyCommand command={DOCKER_RUN} />
          </div>
        </div>

        <div className="min-w-0">
          <RunReport />
        </div>
      </Container>
    </section>
  );
}
