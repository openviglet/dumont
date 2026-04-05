import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";
import {
  IconArrowLeft,
  IconBook2,
  IconCalendar,
  IconExternalLink,
  IconGlobe,
  IconTag,
  IconWorldWww,
} from "@tabler/icons-react";
import type { ResolvedDocument } from "@openviglet/turing-react-sdk";
import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

/* ── Tag color palette ── */
const tagColors: string[] = [
  "bg-amber-500/15 text-amber-300 border-amber-500/25",
  "bg-orange-500/15 text-orange-300 border-orange-500/25",
  "bg-rose-500/15 text-rose-300 border-rose-500/25",
  "bg-emerald-500/15 text-emerald-300 border-emerald-500/25",
  "bg-cyan-500/15 text-cyan-300 border-cyan-500/25",
  "bg-violet-500/15 text-violet-300 border-violet-500/25",
  "bg-blue-500/15 text-blue-300 border-blue-500/25",
  "bg-pink-500/15 text-pink-300 border-pink-500/25",
];

function getTagColor(tag: string): string {
  let hash = 0;
  for (let i = 0; i < tag.length; i++) {
    hash = tag.charCodeAt(i) + ((hash << 5) - hash);
  }
  return tagColors[Math.abs(hash) % tagColors.length];
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
  const tags = toArray(fields.tags);
  const siteName = fields.site ?? "";
  const sourceApps = fields.source_apps ?? "";
  const url = fields.url ?? "";
  const publicationDate = formatDate(fields.publication_date);
  const modificationDate = formatDate(fields.modification_date);
  const abstractText = fields.abstract ?? doc.description ?? "";

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
            Back to Explorer
          </Button>
          <ModeToggle />
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 sm:px-6 py-8">
        {/* ── Hero section ── */}
        <div className="relative mb-8 overflow-hidden rounded-2xl border border-border/30 bg-linear-to-br from-card via-card to-amber-900/5">
          <div className="p-6 sm:p-8">
            {/* Tags row */}
            <div className="flex flex-wrap gap-2 mb-4">
              {tags.map((tag) => (
                <Badge
                  key={tag}
                  variant="outline"
                  className={cn("text-xs", getTagColor(tag))}
                >
                  <IconTag className="size-3 mr-0.5" />
                  {tag}
                </Badge>
              ))}
            </div>

            {/* Title */}
            <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight leading-tight">
              {doc.title}
            </h1>

            {/* Abstract */}
            {abstractText && (
              <p className="mt-4 text-base text-muted-foreground italic max-w-3xl leading-relaxed">
                {abstractText}
              </p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* ── Main content ── */}
          <div className="lg:col-span-2 space-y-8">
            {/* Full text */}
            {doc.text && (
              <div className="space-y-3">
                <h2 className="text-lg font-semibold flex items-center gap-2">
                  <IconBook2 className="size-5 text-primary" />
                  Article
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
              <IconBook2 className="size-4" />
              Article Info
            </h2>

            {siteName && (
              <MetaRow icon={<IconGlobe className="size-4" />} label="Site">
                <Badge
                  variant="outline"
                  className="text-xs bg-amber-500/10 text-amber-300 border-amber-500/20"
                >
                  {siteName}
                </Badge>
              </MetaRow>
            )}

            {sourceApps && (
              <>
                <Separator />
                <MetaRow icon={<IconWorldWww className="size-4" />} label="Source">
                  {sourceApps}
                </MetaRow>
              </>
            )}

            {publicationDate && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconCalendar className="size-4" />}
                  label="Publication Date"
                >
                  {publicationDate}
                </MetaRow>
              </>
            )}

            {modificationDate && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconCalendar className="size-4" />}
                  label="Modification Date"
                >
                  {modificationDate}
                </MetaRow>
              </>
            )}

            {url && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconExternalLink className="size-4" />}
                  label="URL"
                >
                  <a
                    href={url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary hover:underline break-all text-xs"
                  >
                    {url}
                  </a>
                </MetaRow>
              </>
            )}

            {/* Tags in sidebar */}
            {tags.length > 0 && (
              <>
                <Separator />
                <MetaRow
                  icon={<IconTag className="size-4" />}
                  label="Tags"
                >
                  <div className="flex flex-wrap gap-1.5">
                    {tags.map((tag) => (
                      <Badge
                        key={tag}
                        variant="outline"
                        className={cn("text-xs", getTagColor(tag))}
                      >
                        {tag}
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
            <IconBook2 className="size-3.5" />
            Wikipedia Explorer
          </span>
        </div>
      </footer>
    </div>
  );
}
