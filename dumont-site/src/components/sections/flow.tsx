import { ArrowRight, Database, Layers } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { VigletLogo } from "@/components/viglet-logo";
import { CONNECTORS, TARGETS } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

const STAGES = ["read", "extract", "checksum", "queue", "index", "audit"];

/**
 * Sources on the left, targets on the right, and the pipeline in the middle
 * spelled as the stages a run actually goes through. It is the one diagram on
 * the site, and it is drawn with the same cards as everything else so it
 * reflows to a column on a phone instead of shrinking into illegibility.
 */
export function Flow() {
  return (
    <section id="how" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="How it fits"
          title={
            <>
              Six kinds of source, three engines,{" "}
              <span className="text-primary">one pipeline between them</span>
            </>
          }
          description="Every connector runs in the same JVM and goes through the same queue, the same checksums and the same audit. A database and an AEM site are operated the same way."
        />

        <div className="grid items-stretch gap-5 lg:grid-cols-[1fr_auto_1.1fr_auto_1fr] [&>*]:min-w-0">
          <div className="rounded-2xl border border-border bg-card p-5">
            <p className="flex items-center gap-2 text-xs font-bold tracking-wide text-muted-foreground uppercase">
              <Layers className="size-3.5" /> Sources
            </p>
            <ul className="mt-4 space-y-2">
              {CONNECTORS.map((c) => (
                <li key={c.id}>
                  <a
                    href={sitePath(`/connectors/#${c.id}`)}
                    className="flex items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm font-medium transition-colors hover:bg-muted"
                  >
                    <c.icon className="size-4 shrink-0 text-primary" />
                    {c.name}
                  </a>
                </li>
              ))}
            </ul>
          </div>

          <ArrowRight className="mx-auto hidden size-6 self-center text-muted-foreground/50 lg:block" aria-hidden />

          <div className="relative isolate overflow-hidden rounded-2xl border border-white/10 bg-[#081615] p-5 text-[#e8f5f3]">
            <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
            <div className="flex items-center gap-3">
              <VigletLogo identifier="dumont" size={40} />
              <div>
                <p className="font-extrabold">Viglet Dumont</p>
                <p className="font-mono text-xs text-[#86a5a1]">Spring Boot 4 · Java 21 · port 30130</p>
              </div>
            </div>
            <ol className="mt-5 grid grid-cols-2 gap-2 font-mono text-xs sm:grid-cols-3">
              {STAGES.map((s, i) => (
                <li
                  key={s}
                  className="rounded-lg border border-white/10 bg-white/5 px-2.5 py-2 text-[#b5d0cc]"
                >
                  <span className="text-[#5eead4]">{i + 1}</span> {s}
                </li>
              ))}
            </ol>
            <p className="mt-4 text-xs leading-relaxed text-[#86a5a1]">
              An embedded, persistent Artemis queue between extraction and indexing, so a
              slow target backs work up instead of losing it.
            </p>
          </div>

          <ArrowRight className="mx-auto hidden size-6 self-center text-muted-foreground/50 lg:block" aria-hidden />

          <div className="rounded-2xl border border-border bg-card p-5">
            <p className="flex items-center gap-2 text-xs font-bold tracking-wide text-muted-foreground uppercase">
              <Database className="size-3.5" /> Targets
            </p>
            <ul className="mt-4 space-y-3">
              {TARGETS.map((t) => (
                <li key={t.property} className="rounded-lg px-2 py-1">
                  <p className="text-sm font-semibold">{t.name}</p>
                  <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{t.note}</p>
                </li>
              ))}
            </ul>
            <p className="mt-4 border-t border-border pt-3 font-mono text-[0.7rem] text-muted-foreground">
              dumont.indexing.provider = turing | solr | elasticsearch
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
