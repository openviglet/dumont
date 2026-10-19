import { cn } from "@/lib/utils";

/**
 * The hero's artefact: one run of one source, the way an operator reads it.
 *
 * The numbers are illustrative, and the panel says so. The shape is not: every
 * line is a thing a run records today (checksum skips, a rejected batch held
 * for the next run, orphans parked in a report instead of deleted). Where the
 * Shio hero shows a session, this one shows the outcome, because the product
 * is judged on what the index looks like the morning after.
 */
const LINES: { k: string; v: string; tone?: "ok" | "warn" | "dim" }[] = [
  { k: "source", v: "wknd-site  (aem · cloud service)" },
  { k: "target", v: "turing  ·  sn site wknd" },
  { k: "trigger", v: "cron 0 0 3 * * *  ·  nightly audit" },
  { k: "read", v: "4,812 items" },
  { k: "unchanged", v: "4,630  skipped by checksum", tone: "dim" },
  { k: "indexed", v: "171  new or changed", tone: "ok" },
  { k: "fragments", v: "1 footer XF changed → 9 pages requeued", tone: "ok" },
  { k: "rejected", v: "1 batch (2 docs) held for next run", tone: "warn" },
  { k: "orphans", v: "11 → report 2026-10-19, awaiting review", tone: "warn" },
  { k: "coverage", v: "abstract 98.7%  (−0.1 since last run)", tone: "dim" },
];

const TONE: Record<string, string> = {
  ok: "text-[#7dd3a0]",
  warn: "text-[#f5c46b]",
  dim: "text-[#86a5a1]",
};

export function RunReport({ className }: { className?: string }) {
  return (
    <figure
      className={cn(
        "scanlines relative overflow-hidden rounded-2xl border border-white/10 bg-[#0b1a19]/90 shadow-2xl backdrop-blur",
        className
      )}
    >
      <figcaption className="flex items-center justify-between gap-3 border-b border-white/10 px-4 py-2.5 font-mono text-xs font-semibold text-[#91aeaa]">
        <span className="flex items-center gap-2">
          <span className="flex gap-1.5" aria-hidden>
            <span className="size-2 rounded-full bg-[#14b8a6]/80" />
            <span className="size-2 rounded-full bg-[#f5c46b]/70" />
            <span className="size-2 rounded-full bg-[#7dd3a0]/70" />
          </span>
          run #1284 · finished
        </span>
        <span className="rounded border border-white/10 px-1.5 py-0.5 text-[0.65rem] text-[#6f8f8b]">
          illustrative
        </span>
      </figcaption>
      <dl className="grid grid-cols-[6.5rem_1fr] gap-x-4 gap-y-1.5 px-4 py-4 font-mono text-[0.8rem] leading-relaxed text-[#dcefec] sm:grid-cols-[7.5rem_1fr]">
        {LINES.map((l) => (
          <div key={l.k} className="contents">
            <dt className="text-[#6f8f8b]">{l.k}</dt>
            <dd className={cn("min-w-0 break-words", l.tone && TONE[l.tone])}>{l.v}</dd>
          </div>
        ))}
      </dl>
      <div className="border-t border-white/10 px-4 py-3 font-mono text-[0.78rem] text-[#a9c4c0]">
        <span className="text-[#5eead4]">status</span> ok · nothing was removed without review
        <span className="animate-caret ml-1 inline-block h-3.5 w-1.5 translate-y-0.5 bg-[#5eead4]" />
      </div>
    </figure>
  );
}
