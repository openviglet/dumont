import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import {
  IconArrowLeft,
  IconCalendar,
  IconClock,
  IconDeviceGamepad2,
  IconTag,
  IconUser,
  IconWorld,
} from "@tabler/icons-react";
import type { ResolvedDocument } from "@viglet/turing-react-sdk";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

/* ── Genre tag color map ── */
const genreColors: Record<string, string> = {
  Maze: "bg-yellow-500/20 text-yellow-300 border-yellow-500/30",
  Shooter: "bg-red-500/20 text-red-300 border-red-500/30",
  "Fixed Shooter": "bg-red-500/15 text-red-400 border-red-500/25",
  Platformer: "bg-green-500/20 text-green-300 border-green-500/30",
  Fighting: "bg-orange-500/20 text-orange-300 border-orange-500/30",
  Competitive: "bg-orange-500/15 text-orange-400 border-orange-500/25",
  Puzzle: "bg-cyan-500/20 text-cyan-300 border-cyan-500/30",
  "Run and Gun": "bg-pink-500/20 text-pink-300 border-pink-500/30",
  Action: "bg-violet-500/20 text-violet-300 border-violet-500/30",
  Strategy: "bg-blue-500/20 text-blue-300 border-blue-500/30",
  Classic: "bg-amber-500/20 text-amber-300 border-amber-500/30",
  "Sci-Fi": "bg-indigo-500/20 text-indigo-300 border-indigo-500/30",
  Nintendo: "bg-red-500/15 text-red-400 border-red-500/25",
  Capcom: "bg-blue-500/15 text-blue-400 border-blue-500/25",
  Namco: "bg-yellow-500/15 text-yellow-400 border-yellow-500/25",
  Konami: "bg-orange-500/15 text-orange-400 border-orange-500/25",
  "Neo Geo": "bg-purple-500/20 text-purple-300 border-purple-500/30",
};

function getGenreColor(genre: string): string {
  return (
    genreColors[genre] ??
    "bg-primary/20 text-primary border-primary/30"
  );
}

/* ── Metadata row ── */
function MetaRow({
  icon,
  label,
  children,
}: Readonly<{
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
}>) {
  return (
    <div className="flex items-start gap-3 py-3">
      <div className="mt-0.5 text-muted-foreground shrink-0">{icon}</div>
      <div className="min-w-0">
        <div className="text-xs font-medium uppercase tracking-wider text-muted-foreground mb-1">
          {label}
        </div>
        <div className="text-sm">{children}</div>
      </div>
    </div>
  );
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string" && value) return [value];
  return [];
}

function formatDate(value: unknown): string {
  if (!value) return "";
  const date =
    typeof value === "number"
      ? new Date(value)
      : new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

export default function DetailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const doc = location.state?.document as ResolvedDocument | undefined;

  useEffect(() => {
    if (!doc) navigate("/", { replace: true });
  }, [doc, navigate]);

  if (!doc) return null;

  const fields = doc.raw.fields;
  const author = fields.author ?? "";
  const tags = toArray(fields.tags);
  const releaseDate = formatDate(fields.publication_date);
  const modificationDate = formatDate(fields.modification_date);
  const site = fields.site ?? "";

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(-1)}
            className="gap-1.5 text-muted-foreground hover:text-foreground"
          >
            <IconArrowLeft className="size-4" />
            Back to the Vault
          </Button>
          <ModeToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 sm:px-6 py-8">
        {/* ── Hero section ── */}
        <div className="relative mb-8 overflow-hidden rounded-2xl border border-border/30 bg-linear-to-br from-card via-card to-purple-900/5">
          <div className="flex flex-col p-6 sm:p-8">
            {/* Tags row */}
            <div className="flex flex-wrap gap-2 mb-3">
              {tags.map((t) => (
                <Badge
                  key={t}
                  variant="outline"
                  className={cn("text-xs", getGenreColor(t))}
                >
                  <IconTag className="size-3 mr-0.5" />
                  {t}
                </Badge>
              ))}
            </div>

            <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight leading-tight">
              {doc.title}
            </h1>

            {/* Abstract */}
            {(fields.abstract ?? doc.description) && (
              <p className="mt-3 text-muted-foreground text-base leading-relaxed max-w-3xl">
                {fields.abstract ?? doc.description}
              </p>
            )}

            {/* Author badge */}
            {author && (
              <div className="mt-4 flex items-center gap-2">
                <Badge
                  variant="secondary"
                  className="text-sm bg-primary/10 text-primary border border-primary/20"
                >
                  <IconUser className="size-3.5 mr-1" />
                  {author}
                </Badge>
              </div>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* ── Main content ── */}
          <div className="lg:col-span-2 space-y-8">
            {/* Full text / game history */}
            {doc.text && (
              <div className="space-y-3">
                <h2 className="text-lg font-semibold flex items-center gap-2">
                  <IconDeviceGamepad2 className="size-5 text-primary" />
                  Game History
                </h2>
                <div className="text-sm leading-relaxed text-foreground/85 whitespace-pre-line rounded-xl border border-border/30 bg-card/50 p-5">
                  {doc.text}
                </div>
              </div>
            )}
          </div>

          {/* ── Sidebar metadata ── */}
          <aside className="space-y-1 rounded-xl border border-border/40 bg-card/50 p-5 h-fit">
            <h2 className="text-sm font-bold uppercase tracking-widest text-primary/70 mb-2 flex items-center gap-1.5">
              <IconDeviceGamepad2 className="size-4" />
              Game Info
            </h2>

            {author && (
              <MetaRow icon={<IconUser className="size-4" />} label="Developer">
                {author}
              </MetaRow>
            )}

            {releaseDate && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconCalendar className="size-4" />}
                  label="Release Date"
                >
                  {releaseDate}
                </MetaRow>
              </>
            )}

            {modificationDate && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconClock className="size-4" />}
                  label="Last Updated"
                >
                  {modificationDate}
                </MetaRow>
              </>
            )}

            {site && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconWorld className="size-4" />}
                  label="Site"
                >
                  {site}
                </MetaRow>
              </>
            )}

            {/* Tags in sidebar */}
            {tags.length > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconTag className="size-4" />}
                  label="Genre"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {tags.map((t) => (
                      <Badge
                        key={t}
                        variant="outline"
                        className={cn("text-xs", getGenreColor(t))}
                      >
                        {t}
                      </Badge>
                    ))}
                  </div>
                </MetaRow>
              </>
            )}
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30 mt-12">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconDeviceGamepad2 className="size-3.5" />
            The Arcade Vault
          </span>
        </div>
      </footer>
    </div>
  );
}
