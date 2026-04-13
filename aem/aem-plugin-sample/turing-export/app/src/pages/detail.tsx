import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import {
  IconArrowLeft,
  IconCalendar,
  IconFileText,
  IconGlobe,
  IconLanguage,
  IconMountain,
  IconServer,
  IconTag,
} from "@tabler/icons-react";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";
import { useLocation, useNavigate } from "react-router-dom";

/* ── Tag color map (same as search) ── */
const TAG_COLORS: Record<string, string> = {
  "Surfing": "bg-cyan-500/15 text-cyan-300 border-cyan-500/25",
  "Water Sports": "bg-cyan-500/15 text-cyan-300 border-cyan-500/25",
  "Skiing": "bg-blue-500/15 text-blue-300 border-blue-500/25",
  "Winter Sports": "bg-blue-500/15 text-blue-300 border-blue-500/25",
  "Alps": "bg-blue-500/15 text-blue-300 border-blue-500/25",
  "Rock Climbing": "bg-orange-500/15 text-orange-300 border-orange-500/25",
  "Skateboarding": "bg-yellow-500/15 text-yellow-300 border-yellow-500/25",
  "Trekking": "bg-emerald-500/15 text-emerald-300 border-emerald-500/25",
  "Jungle": "bg-emerald-500/15 text-emerald-300 border-emerald-500/25",
  "Wildlife": "bg-emerald-500/15 text-emerald-300 border-emerald-500/25",
  "Mountain Biking": "bg-red-500/15 text-red-300 border-red-500/25",
  "Extreme Sports": "bg-red-500/15 text-red-300 border-red-500/25",
  "Travel": "bg-violet-500/15 text-violet-300 border-violet-500/25",
  "Adventure Travel": "bg-violet-500/15 text-violet-300 border-violet-500/25",
  "Road Trip": "bg-amber-500/15 text-amber-300 border-amber-500/25",
  "Camping": "bg-amber-500/15 text-amber-300 border-amber-500/25",
};

const FALLBACK_COLORS: string[] = [
  "bg-teal-500/15 text-teal-300 border-teal-500/25",
  "bg-pink-500/15 text-pink-300 border-pink-500/25",
  "bg-lime-500/15 text-lime-300 border-lime-500/25",
  "bg-fuchsia-500/15 text-fuchsia-300 border-fuchsia-500/25",
  "bg-sky-500/15 text-sky-300 border-sky-500/25",
];

function getTagColor(tag: string): string {
  if (TAG_COLORS[tag]) return TAG_COLORS[tag];
  let hash = 0;
  for (let i = 0; i < tag.length; i++) {
    hash = tag.charCodeAt(i) + ((hash << 5) - hash);
  }
  return FALLBACK_COLORS[Math.abs(hash) % FALLBACK_COLORS.length];
}

/* ── Hero gradient by category ── */
const HERO_GRADIENTS: Record<string, string> = {
  "Surfing": "from-cyan-700/90 via-cyan-900/70 to-background",
  "Skiing": "from-blue-700/90 via-blue-900/70 to-background",
  "Rock Climbing": "from-orange-700/90 via-orange-900/70 to-background",
  "Skateboarding": "from-yellow-700/90 via-yellow-900/70 to-background",
  "Trekking": "from-emerald-700/90 via-emerald-900/70 to-background",
  "Mountain Biking": "from-red-700/90 via-red-900/70 to-background",
  "Road Trip": "from-amber-700/90 via-amber-900/70 to-background",
};

function getHeroGradient(tags: string[]): string {
  for (const tag of tags) {
    if (HERO_GRADIENTS[tag]) return HERO_GRADIENTS[tag];
  }
  return "from-primary/90 via-primary/50 to-background";
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string" && value) return [value];
  return [];
}

function formatDate(value: unknown): string {
  if (!value) return "";
  try {
    const d = new Date(value as string);
    return d.toLocaleDateString("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return String(value);
  }
}

/* ── Sidebar info row ── */
function InfoRow({
  icon: Icon,
  label,
  value,
}: Readonly<{
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
}>) {
  if (!value) return null;
  return (
    <div className="flex items-start gap-3 py-2">
      <Icon className="size-4 text-muted-foreground shrink-0 mt-0.5" />
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground uppercase tracking-wider font-medium">{label}</p>
        <p className="text-sm text-foreground mt-0.5">{value}</p>
      </div>
    </div>
  );
}

export default function DetailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const doc = (location.state as { document?: ResolvedDocument })?.document;

  if (!doc) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4">
        <IconMountain className="size-12 text-muted-foreground/40" />
        <p className="text-muted-foreground">No article data available.</p>
        <Button variant="outline" onClick={() => navigate("/")}>
          Back to WKND
        </Button>
      </div>
    );
  }

  const fields = doc.raw.fields;
  const tags = toArray(fields.tags);
  const sourceApps = toArray(fields.source_apps);
  const title = fields.title ?? doc.title ?? "Untitled";
  const text = typeof fields.text === "string" ? fields.text : "";
  const description = fields.description ?? doc.description ?? "";
  const abstract = fields.abstract ?? "";
  const pageTemplate = fields.pageTemplate ?? "";
  const pageLanguage = fields.pageLanguage ?? "";
  const site = fields.site ?? "";
  const publicationDate = formatDate(fields.publicationDate);
  const heroGradient = getHeroGradient(tags);

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate("/")}
              className="gap-1.5"
            >
              <IconArrowLeft className="size-4" />
              Back
            </Button>
            <Separator orientation="vertical" className="h-4" />
            <div className="flex items-center gap-2">
              <IconMountain className="size-5 text-primary" />
              <span className="font-black text-sm tracking-wide uppercase">
                WKND
              </span>
            </div>
          </div>
          <ModeToggle />
        </div>
      </header>

      {/* ── Hero image area ── */}
      <section className="relative h-64 sm:h-80 lg:h-96 overflow-hidden">
        {/* Background gradient simulating full-width image */}
        <div className={cn(
          "absolute inset-0 bg-linear-to-b",
          heroGradient
        )} />
        {/* Decorative texture */}
        <div className="absolute inset-0 opacity-10 bg-[radial-gradient(circle_at_30%_40%,white_1px,transparent_1px)] bg-[length:24px_24px]" />
        <div className="absolute inset-0 opacity-5 bg-[radial-gradient(circle_at_70%_60%,white_2px,transparent_2px)] bg-[length:48px_48px]" />

        {/* Title overlay at bottom */}
        <div className="absolute inset-0 bg-linear-to-t from-background via-background/40 to-transparent" />
        <div className="absolute bottom-0 left-0 right-0 px-4 sm:px-6 pb-6">
          <div className="mx-auto max-w-4xl">
            {/* Tags */}
            <div className="flex flex-wrap gap-1.5 mb-3">
              {tags.map((tag) => (
                <Badge
                  key={tag}
                  variant="outline"
                  className={cn("text-xs py-0.5 backdrop-blur-sm", getTagColor(tag))}
                >
                  <IconTag className="size-3 mr-0.5" />
                  {tag}
                </Badge>
              ))}
            </div>
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-black leading-tight text-foreground">
              {title}
            </h1>
            {publicationDate && (
              <p className="mt-2 text-sm text-muted-foreground flex items-center gap-1.5">
                <IconCalendar className="size-3.5" />
                {publicationDate}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* ── Content ── */}
      <main className="mx-auto max-w-4xl w-full flex-1 px-4 sm:px-6 py-8">
        <div className="flex flex-col lg:flex-row gap-8">
          {/* Article body */}
          <article className="flex-1 min-w-0">
            {/* Abstract */}
            {abstract && (
              <blockquote className="mb-6 pl-4 border-l-4 border-primary/40 text-lg text-muted-foreground italic leading-relaxed">
                {abstract}
              </blockquote>
            )}

            {/* Description */}
            {description && (
              <p className="mb-6 text-base text-foreground/90 leading-relaxed font-medium">
                {description}
              </p>
            )}

            {/* Main text */}
            {text && (
              <div className="prose prose-sm dark:prose-invert max-w-none">
                {text.split("\n").map((paragraph, i) => (
                  <p key={i} className="mb-4 text-base leading-relaxed text-foreground/80">
                    {paragraph}
                  </p>
                ))}
              </div>
            )}
          </article>

          {/* Sidebar */}
          <aside className="w-full lg:w-64 shrink-0">
            <div className="rounded-xl border border-border/50 bg-card p-5 space-y-1 sticky top-20">
              <h3 className="text-xs font-bold uppercase tracking-widest text-primary/70 mb-3">
                Article Details
              </h3>
              <Separator className="mb-3" />

              <InfoRow icon={IconFileText} label="Template" value={pageTemplate} />
              <InfoRow icon={IconLanguage} label="Language" value={pageLanguage} />
              <InfoRow icon={IconGlobe} label="Site" value={site} />
              <InfoRow icon={IconCalendar} label="Published" value={publicationDate} />

              {sourceApps.length > 0 && (
                <div className="flex items-start gap-3 py-2">
                  <IconServer className="size-4 text-muted-foreground shrink-0 mt-0.5" />
                  <div className="min-w-0">
                    <p className="text-xs text-muted-foreground uppercase tracking-wider font-medium">Source App</p>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {sourceApps.map((app) => (
                        <Badge key={app} variant="outline" className="text-[10px] py-0 h-5">
                          {app}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {tags.length > 0 && (
                <>
                  <Separator className="my-3" />
                  <div className="space-y-2">
                    <p className="text-xs text-muted-foreground uppercase tracking-wider font-medium flex items-center gap-1.5">
                      <IconTag className="size-3.5" />
                      Tags
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {tags.map((tag) => (
                        <Badge
                          key={tag}
                          variant="outline"
                          className={cn("text-[10px] py-0 h-5", getTagColor(tag))}
                        >
                          {tag}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </>
              )}
            </div>
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconMountain className="size-3.5" />
            WKND Magazine
          </span>
        </div>
      </footer>
    </div>
  );
}
