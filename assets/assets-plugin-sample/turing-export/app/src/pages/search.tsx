import { ModeToggle } from "@/components/mode-toggle";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { resolveSiteName } from "@/lib/resolve-site";
import { cn } from "@/lib/utils";
import {
  IconChevronLeft,
  IconChevronRight,
  IconChevronsLeft,
  IconChevronsRight,
  IconClock,
  IconFilter,
  IconLoader2,
  IconMoodEmpty,
  IconSearch,
  IconTelescope,
  IconX,
} from "@tabler/icons-react";
import {
  TuringProvider,
  TuringSearchField,
  useTuringUrlSearch,
  type ResolvedDocument,
  type TurFacetGroup,
  type TurFacetItem,
  type TurPaginationItem,
} from "@viglet/turing-react-sdk";
import { useNavigate, useSearchParams } from "react-router-dom";

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

/* ── Facet sidebar ── */
function FacetSidebar({
  groups,
  onNavigate,
}: Readonly<{
  groups: TurFacetGroup[];
  onNavigate: (href: string) => void;
}>) {
  if (!groups || groups.length === 0) return null;

  return (
    <aside className="w-64 shrink-0 space-y-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground/80 uppercase tracking-widest">
        <IconFilter className="size-4" />
        Browse By
      </div>
      {groups.map((group) => (
        <div key={group.name} className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-widest text-primary/70 border-b border-primary/20 pb-1.5">
            {group.label?.text ?? group.name}
          </h3>
          <div className="space-y-0.5">
            {group.facets.map((facet: TurFacetItem) => (
              <button
                key={facet.label + facet.link}
                onClick={() => onNavigate(facet.link)}
                className={cn(
                  "flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-sm transition-colors",
                  facet.selected
                    ? "bg-primary/15 text-primary font-medium"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground"
                )}
              >
                <span className="flex items-center gap-2 truncate">
                  {facet.selected && (
                    <IconX className="size-3 shrink-0 text-primary" />
                  )}
                  <span className="truncate">{facet.label}</span>
                </span>
                <span
                  className={cn(
                    "ml-2 shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-mono",
                    facet.selected
                      ? "bg-primary/20 text-primary"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  {facet.count}
                </span>
              </button>
            ))}
          </div>
        </div>
      ))}
    </aside>
  );
}

/* ── Asset card ── */
function AssetCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const fileExtension = fields.fileExtension ?? "";
  const fileSize = fields.fileSize ?? "";

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-linear-to-b from-primary/5 via-transparent to-transparent" />

      <div className="flex flex-1 flex-col gap-3 p-5">
        {/* Title */}
        <h3
          className="text-base font-semibold leading-tight line-clamp-2 group-hover:text-primary transition-colors"
          dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
        />

        {/* Description */}
        {doc.description && (
          <p className="text-sm text-muted-foreground line-clamp-3 leading-relaxed">
            {doc.description}
          </p>
        )}

        {/* File info row */}
        <div className="flex flex-wrap items-center gap-1.5 mt-auto pt-2">
          {fileExtension && (
            <Badge
              variant="outline"
              className={cn(
                "text-[10px] py-0 h-5 uppercase font-mono",
                getExtensionColor(fileExtension)
              )}
            >
              {fileExtension}
            </Badge>
          )}
          {fileSize && (
            <Badge
              variant="secondary"
              className="text-[10px] py-0 h-5 bg-secondary/60 font-mono"
            >
              {formatFileSize(fileSize)}
            </Badge>
          )}
        </div>
      </div>
    </button>
  );
}

/* ── Pagination ── */
function Pagination({
  items,
  onNavigate,
}: Readonly<{
  items: TurPaginationItem[];
  onNavigate: (href: string) => void;
}>) {
  if (!items || items.length === 0) return null;

  const iconMap: Record<string, React.ReactNode> = {
    FIRST: <IconChevronsLeft className="size-4" />,
    PREVIOUS: <IconChevronLeft className="size-4" />,
    NEXT: <IconChevronRight className="size-4" />,
    LAST: <IconChevronsRight className="size-4" />,
  };

  return (
    <nav className="flex items-center justify-center gap-1 py-8">
      {items.map((item, i) => {
        const isPage = item.type === "PAGE";
        const isCurrent = isPage && !item.href;

        return (
          <Button
            key={`${item.type}-${item.page}-${i}`}
            variant={isCurrent ? "default" : "ghost"}
            size="sm"
            disabled={!item.href}
            onClick={() => item.href && onNavigate(item.href)}
            className={cn(
              "min-w-8 font-mono",
              isCurrent && "pointer-events-none"
            )}
          >
            {isPage ? item.text : iconMap[item.type] ?? item.text}
          </Button>
        );
      })}
    </nav>
  );
}

/* ── Main search content ── */
function SearchContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const turing = useTuringUrlSearch(searchParams, (s: string) =>
    setSearchParams(s)
  );
  const query = turing.params.q ?? "*";
  const isLanding = query === "*" && turing.status !== "loading";
  const hasResults = turing.documents.length > 0;
  const facetGroups = turing.data?.widget?.facet ?? [];
  const pagination = turing.data?.pagination ?? [];
  const totalCount = turing.data?.queryContext?.count ?? 0;

  function goToDetail(doc: ResolvedDocument) {
    navigate("/detail", { state: { document: doc } });
  }

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

      {/* ── Hero ── */}
      <section
        className={cn(
          "relative overflow-x-clip transition-all duration-500",
          isLanding ? "py-20 sm:py-28" : "py-8 sm:py-10"
        )}
      >
        {/* Deep space gradient background */}
        <div className="absolute inset-0 bg-linear-to-b from-primary/10 via-indigo-600/5 to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-blue-500/8 via-transparent to-transparent" />

        {/* Animated star-like particles */}
        <div className="absolute top-6 left-[10%] size-1 rounded-full bg-blue-300/30 animate-pulse" />
        <div className="absolute top-14 right-[15%] size-1.5 rounded-full bg-indigo-300/25 animate-pulse [animation-delay:1s]" />
        <div className="absolute bottom-10 left-[30%] size-1 rounded-full bg-violet-300/20 animate-pulse [animation-delay:0.5s]" />
        <div className="absolute top-20 left-[55%] size-0.5 rounded-full bg-cyan-300/30 animate-pulse [animation-delay:1.5s]" />
        <div className="absolute bottom-6 right-[22%] size-1.5 rounded-full bg-blue-200/20 animate-pulse [animation-delay:2s]" />
        <div className="absolute top-10 right-[42%] size-1 rounded-full bg-indigo-400/15 animate-pulse [animation-delay:0.7s]" />
        <div className="absolute top-28 left-[78%] size-0.5 rounded-full bg-violet-400/25 animate-pulse [animation-delay:1.2s]" />
        <div className="absolute bottom-16 left-[65%] size-1 rounded-full bg-cyan-400/20 animate-pulse [animation-delay:0.3s]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              <div className="mb-3 flex items-center justify-center gap-2 text-primary/70 text-sm font-medium uppercase tracking-widest">
                <Separator className="w-8 bg-primary/30" />
                Wonders of the cosmos
                <Separator className="w-8 bg-primary/30" />
              </div>
              <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight bg-linear-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2">
                Celestial Gallery
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Explore the wonders of the cosmos. From nebulae to black holes,
                discover the universe's most spectacular sights.
                {totalCount > 0 && (
                  <span className="block mt-1 text-primary/80 font-medium">
                    {totalCount} celestial objects indexed
                  </span>
                )}
              </p>
            </>
          )}

          {/* Search bar */}
          <TuringSearchField
            turing={turing}
            className={cn(
              "relative z-50 mx-auto w-full transition-all duration-500",
              isLanding ? "mt-8 max-w-xl" : "max-w-2xl"
            )}
          >
            <div className="relative group">
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-blue-500/30 via-indigo-500/20 to-violet-500/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search the cosmos... nebula, galaxy, Mars..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/60"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium bg-linear-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-md cursor-pointer">
                  Search
                </TuringSearchField.Button>
              </div>
              <TuringSearchField.Dropdown
                className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden max-h-64 overflow-y-auto"
                renderSuggestion={(term, onClick) => (
                  <button key={term} type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                    className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer">
                    <IconSearch className="size-3.5 text-muted-foreground shrink-0" />{term}
                  </button>
                )}
                renderHistory={(term, onClick, onRemove) => (
                  <div key={term} className="flex items-center hover:bg-accent transition-colors">
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClick}
                      className="flex-1 px-4 py-2.5 text-left text-sm flex items-center gap-2 cursor-pointer">
                      <IconClock className="size-3.5 text-muted-foreground shrink-0" />{term}
                    </button>
                    <button type="button" title="Remove" onMouseDown={(e) => e.preventDefault()} onClick={onRemove}
                      className="px-3 py-2.5 text-muted-foreground hover:text-destructive transition-colors cursor-pointer">&times;</button>
                  </div>
                )}
                renderHistoryHeader={(onClearAll) => (
                  <div className="flex items-center justify-between px-4 py-2 border-b border-border/50">
                    <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Recent searches</span>
                    <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={onClearAll}
                      className="text-xs text-muted-foreground hover:text-destructive transition-colors cursor-pointer">Clear all</button>
                  </div>
                )}
              />
            </div>
          </TuringSearchField>
        </div>
      </section>

      {/* ── Content area ── */}
      <main className="mx-auto flex w-full max-w-7xl flex-1 gap-8 px-4 sm:px-6 pb-12">
        {/* Loading state */}
        {turing.status === "loading" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24">
            <IconLoader2 className="size-8 text-primary animate-spin" />
            <p className="text-sm text-muted-foreground">
              Scanning the heavens...
            </p>
          </div>
        )}

        {/* Error state */}
        {turing.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconTelescope className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              Signal lost. Deep space interference. {turing.error}
            </p>
            <Button variant="outline" size="sm" onClick={turing.showAll}>
              Back to Gallery
            </Button>
          </div>
        )}

        {/* Results */}
        {turing.status === "success" && (
          <>
            {/* Facets sidebar */}
            <FacetSidebar
              groups={facetGroups}
              onNavigate={turing.navigate}
            />

            {/* Results area */}
            <div className="flex-1 min-w-0">
              {/* Results header */}
              {query !== "*" && (
                <div className="mb-6 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">
                    <span className="font-semibold text-foreground">
                      {totalCount}
                    </span>{" "}
                    celestial object{totalCount === 1 ? "" : "s"} found
                    {query !== "*" && (
                      <>
                        {" "}
                        for{" "}
                        <span className="font-medium text-primary">
                          &ldquo;{query}&rdquo;
                        </span>
                      </>
                    )}
                  </p>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={turing.showAll}
                    className="text-xs text-muted-foreground"
                  >
                    Clear search
                  </Button>
                </div>
              )}

              {/* Grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {turing.documents.map((doc, i) => (
                    <AssetCard
                      key={doc.raw.fields.id ?? `doc-${i}`}
                      doc={doc}
                      onClick={() => goToDetail(doc)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
                  <IconMoodEmpty className="size-10 text-muted-foreground/40" />
                  <p className="text-muted-foreground text-sm">
                    No celestial objects found in this region.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={turing.showAll}
                  >
                    Show all objects
                  </Button>
                </div>
              )}

              {/* Pagination */}
              <Pagination items={pagination} onNavigate={turing.navigate} />
            </div>
          </>
        )}

        {/* Landing state - idle */}
        {turing.status === "idle" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
            <IconTelescope className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md">
              Enter a search query or browse the full collection. Every image is
              a window into the cosmos.
            </p>
          </div>
        )}
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30">
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

export default function SearchPage() {
  const site = resolveSiteName();
  return (
    <TuringProvider
      config={{
        site,
        locale: import.meta.env.VITE_LOCALE,
      }}
    >
      <SearchContent />
    </TuringProvider>
  );
}
