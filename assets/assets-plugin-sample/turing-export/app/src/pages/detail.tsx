import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  IconArrowLeft,
  IconCalendar,
  IconExternalLink,
  IconFile,
  IconRuler2,
  IconTelescope,
  IconWorld,
} from "@tabler/icons-react";
import type { ResolvedDocument } from "@openviglet/turing-react-sdk";
import { useLocation, useNavigate } from "react-router-dom";

/* ── File extension badge colors ── */
const extensionColors: Record<string, string> = {
  tiff: "bg-violet-500/15 text-violet-300 border-violet-500/25",
  png: "bg-blue-500/15 text-blue-300 border-blue-500/25",
  fits: "bg-cyan-500/15 text-cyan-300 border-cyan-500/25",
  jpg: "bg-amber-500/15 text-amber-300 border-amber-500/25",
  jpeg: "bg-amber-500/15 text-amber-300 border-amber-500/25",
};

function getExtensionColor(ext: string): string {
  return (
    extensionColors[ext.toLowerCase()] ??
    "bg-slate-500/15 text-slate-300 border-slate-500/25"
  );
}

/* ── File size formatter ── */
function formatFileSize(bytes: string | number | undefined): string {
  if (!bytes) return "";
  const b = typeof bytes === "string" ? parseInt(bytes, 10) : bytes;
  if (isNaN(b) || b === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(b) / Math.log(1024));
  const size = b / Math.pow(1024, i);
  return `${size.toFixed(size < 10 && i > 0 ? 1 : 0)} ${units[i]}`;
}

/* ── Date formatter ── */
function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return "";
  try {
    return new Date(dateStr).toLocaleDateString("en-US", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return dateStr;
  }
}

export default function DetailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const doc = (location.state as { document?: ResolvedDocument })?.document;

  if (!doc) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4">
        <IconTelescope className="size-12 text-muted-foreground/40" />
        <p className="text-muted-foreground">No celestial object selected.</p>
        <Button variant="outline" onClick={() => navigate("/")}>
          Back to Gallery
        </Button>
      </div>
    );
  }

  const fields = doc.raw.fields;
  const title = fields.title ?? doc.title ?? "Untitled";
  const text = fields.text ?? doc.description ?? "";
  const fileExtension = fields.fileExtension ?? "";
  const fileSize = fields.fileSize ?? "";
  const site = fields.site ?? "";
  const publicationDate = fields.publication_date ?? "";
  const url = fields.url ?? "";

  return (
    <div className="min-h-screen flex flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-40 border-b border-border/40 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <IconTelescope className="size-6 text-primary" />
            <span className="font-bold text-sm tracking-wide uppercase">
              Celestial Gallery
            </span>
          </div>
          <ModeToggle />
        </div>
      </header>

      {/* ── Content ── */}
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 sm:px-6 py-8">
        {/* Back button */}
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate(-1)}
          className="mb-6 text-muted-foreground hover:text-foreground"
        >
          <IconArrowLeft className="size-4 mr-1" />
          Back to results
        </Button>

        <div className="flex flex-col lg:flex-row gap-8">
          {/* Main content */}
          <div className="flex-1 min-w-0">
            {/* Title */}
            <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight leading-tight mb-6">
              {title}
            </h1>

            <Separator className="mb-6" />

            {/* Full text */}
            <div className="prose prose-sm dark:prose-invert max-w-none">
              <p className="text-base leading-relaxed text-foreground/90 whitespace-pre-line">
                {text}
              </p>
            </div>
          </div>

          {/* Sidebar */}
          <aside className="w-full lg:w-80 shrink-0">
            <div className="rounded-xl border border-border/50 bg-card p-6 space-y-5 lg:sticky lg:top-20">
              <h2 className="text-sm font-bold uppercase tracking-widest text-primary/70">
                Object Details
              </h2>

              {/* File Type */}
              {fileExtension && (
                <div className="flex items-start gap-3">
                  <IconFile className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-1">
                      File Type
                    </p>
                    <Badge
                      variant="outline"
                      className={`uppercase font-mono ${getExtensionColor(fileExtension)}`}
                    >
                      {fileExtension}
                    </Badge>
                  </div>
                </div>
              )}

              {/* File Size */}
              {fileSize && (
                <div className="flex items-start gap-3">
                  <IconRuler2 className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-1">
                      File Size
                    </p>
                    <p className="text-sm font-medium font-mono">
                      {formatFileSize(fileSize)}
                    </p>
                  </div>
                </div>
              )}

              {/* Collection */}
              {site && (
                <div className="flex items-start gap-3">
                  <IconWorld className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-1">
                      Collection
                    </p>
                    <p className="text-sm font-medium">{site}</p>
                  </div>
                </div>
              )}

              {/* Publication Date */}
              {publicationDate && (
                <div className="flex items-start gap-3">
                  <IconCalendar className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-1">
                      Publication Date
                    </p>
                    <p className="text-sm font-medium">
                      {formatDate(publicationDate)}
                    </p>
                  </div>
                </div>
              )}

              {/* URL */}
              {url && (
                <div className="flex items-start gap-3">
                  <IconExternalLink className="size-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-1">
                      Source URL
                    </p>
                    <a
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm text-primary hover:underline break-all"
                    >
                      {url}
                    </a>
                  </div>
                </div>
              )}
            </div>
          </aside>
        </div>
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30 mt-8">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground">
          <span>Powered by Viglet Turing ES</span>
          <span className="flex items-center gap-1.5">
            <IconTelescope className="size-3.5" />
            Celestial Gallery
          </span>
        </div>
      </footer>
    </div>
  );
}
