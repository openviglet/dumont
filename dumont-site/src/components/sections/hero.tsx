import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { RunReport } from "@/components/run-report";
import { DOCKER_RUN } from "@/lib/site-content";
import { DESTINATIONS, sitePath } from "@/lib/site-links";

const PROMISES = [
  "A failed run never empties the index",
  "Deletions wait for a person",
  "Self-hosted, Apache 2.0",
];

/**
 * The landing hero, on the page's own ground like viglet.org's: no dark band in
 * light mode, no grid or moving orbs, metadata as plain eyebrow text rather than a
 * chip. The one artefact that stays dark is the run report, because it is a
 * terminal, which is what the product's evidence looks like.
 */
export function Hero() {
  return (
    <section id="top" className="border-b border-border bg-background">
      <Container className="grid grid-cols-1 items-center gap-12 py-16 md:py-20 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="min-w-0">
          <Eyebrow>Open source · Self-hosted · AEM · Solr · Elasticsearch</Eyebrow>

          <h1 className="mt-4 text-4xl font-extrabold leading-[1.08] tracking-tight md:text-5xl">
            Your content in your search index.{" "}
            <span className="text-primary">And kept there, correct.</span>
          </h1>

          <p className="mt-5 max-w-xl text-lg leading-relaxed text-muted-foreground">
            Viglet Dumont connects AEM, Edge Delivery Services, databases, file shares,
            web sites and JSON APIs to Viglet Turing, Solr or Elasticsearch. Then it keeps
            checking: what changed, what disappeared, which field stopped arriving.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted-foreground">
            Getting documents into an index is a weekend script. Keeping the index true to
            the source for years, through outages, template changes and unpublished
            pages, is the part Viglet Dumont is built for.
          </p>

          <div className="mt-7 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={DESTINATIONS.explore.href}>
                {DESTINATIONS.explore.label} <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button asChild size="lg" variant="outline">
              <a href={sitePath("/aem/")}>Viglet Dumont for AEM</a>
            </Button>
            <Button asChild size="lg" variant="ghost">
              <a href={DESTINATIONS.docs.href}>{DESTINATIONS.docs.label}</a>
            </Button>
          </div>

          <ul className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
            {PROMISES.map((item) => (
              <li key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-4 text-primary" />
                {item}
              </li>
            ))}
          </ul>

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
