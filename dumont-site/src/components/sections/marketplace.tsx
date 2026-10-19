import { ArrowUpRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { SAMPLES } from "@/lib/site-content";

/**
 * The samples are built and copied into /marketplace/<slug>/ by the Pages
 * workflow, beside this site rather than inside it, each with its README
 * rendered as the folder's index and the importable zip next to it.
 */
export function Marketplace() {
  return (
    <section id="marketplace" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Marketplace"
          title={
            <>
              One sample per connector,{" "}
              <span className="text-primary">ready to import into Turing</span>
            </>
          }
          description="Each sample is a search front end plus the Turing export it needs. Download the package, import it, point the connector at the source, and you have a working search to take apart."
        />

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {SAMPLES.map((s) => (
            <a
              key={s.slug}
              href={`/marketplace/${s.slug}/`}
              className="group flex flex-col rounded-xl border border-border bg-card p-5 transition-colors hover:border-primary/40"
            >
              <span className="w-fit rounded-md border border-primary/25 bg-primary/5 px-2 py-0.5 font-mono text-[0.68rem] font-semibold text-primary">
                {s.source}
              </span>
              <h3 className="mt-3 flex items-center gap-1 font-bold">
                {s.title}
                <ArrowUpRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
              </h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{s.body}</p>
            </a>
          ))}
        </div>
      </Container>
    </section>
  );
}
