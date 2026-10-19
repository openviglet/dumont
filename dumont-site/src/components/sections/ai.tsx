import { Sparkles } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { AI_POINTS } from "@/lib/site-content";

export function Ai() {
  return (
    <section id="ai" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="AI, where it helps"
          title={
            <>
              An LLM that drafts the source.{" "}
              <span className="text-primary">You decide what gets indexed.</span>
            </>
          }
          description="Dumont uses AI for the slow, fiddly parts: working out the shape of a new source, summarising a coverage report, labelling content. None of it writes to your index without a person or a rule you set."
        />

        <div className="grid gap-5 md:grid-cols-2">
          {AI_POINTS.map((p) => (
            <div key={p.title} className="flex gap-4 rounded-2xl border border-border bg-card p-6">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Sparkles className="size-5" />
              </div>
              <div className="min-w-0">
                <h3 className="font-bold">{p.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </div>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}
