import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { DOCKER_RUN, LINKS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

export function CallToAction() {
  return (
    // `data-md-skip`: the Markdown twin of every route would otherwise carry
    // this identically.
    <section className="py-20" data-md-skip>
      <Container>
        <div className="rounded-3xl border border-border bg-muted/40 px-6 py-16 text-center">
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">
            Point it at one source this afternoon.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-base text-muted-foreground sm:text-lg">
            One container, a console in English or Portuguese, and a dry scan that shows what
            would be indexed before anything is. Apache 2.0, with the roadmap in the
            repository.
          </p>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild size="lg">
              <a href={LINKS.install}>
                Install it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button asChild size="lg" variant="outline">
              <a href={sitePath("/#marketplace")}>Start from a sample</a>
            </Button>
            <Button asChild size="lg" variant="ghost">
              <a href={LINKS.repo} target="_blank" rel="noreferrer">
                Read the source
              </a>
            </Button>
          </div>

          <div className="mt-8 flex justify-center">
            <CopyCommand command={DOCKER_RUN} />
          </div>
        </div>
      </Container>
    </section>
  );
}
