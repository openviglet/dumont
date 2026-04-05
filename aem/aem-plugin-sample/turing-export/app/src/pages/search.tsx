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
  IconMountain,
  IconSearch,
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
} from "@openviglet/turing-react-sdk";
import { useNavigate, useSearchParams } from "react-router-dom";

/* ── Tag color map ── */
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

/* ── Image gradient by category ── */
const CARD_GRADIENTS: Record<string, string> = {
  "Surfing": "from-cyan-600/80 via-cyan-900/60 to-transparent",
  "Skiing": "from-blue-600/80 via-blue-900/60 to-transparent",
  "Rock Climbing": "from-orange-600/80 via-orange-900/60 to-transparent",
  "Skateboarding": "from-yellow-600/80 via-yellow-900/60 to-transparent",
  "Trekking": "from-emerald-600/80 via-emerald-900/60 to-transparent",
  "Mountain Biking": "from-red-600/80 via-red-900/60 to-transparent",
  "Road Trip": "from-amber-600/80 via-amber-900/60 to-transparent",
};

function getCardGradient(tags: string[]): string {
  for (const tag of tags) {
    if (CARD_GRADIENTS[tag]) return CARD_GRADIENTS[tag];
  }
  return "from-primary/80 via-primary/40 to-transparent";
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) return value;
  if (typeof value === "string" && value) return [value];
  return [];
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
        Filter
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

/* ── Magazine-style article card ── */
function ArticleCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const tags = toArray(fields.tags);
  const gradient = getCardGradient(tags);

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-xl hover:shadow-primary/5 hover:-translate-y-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aspect-[4/3]"
    >
      {/* Simulated image area with gradient */}
      <div className="absolute inset-0">
        <div className={cn(
          "absolute inset-0 bg-linear-to-t",
          gradient
        )} />
        {/* Decorative pattern to simulate image texture */}
        <div className="absolute inset-0 opacity-10 bg-[radial-gradient(circle_at_30%_40%,white_1px,transparent_1px)] bg-[length:20px_20px]" />
        <div className="absolute inset-0 opacity-5 bg-[radial-gradient(circle_at_70%_60%,white_2px,transparent_2px)] bg-[length:40px_40px]" />
      </div>

      {/* Content overlay at bottom */}
      <div className="relative flex flex-1 flex-col justify-end p-5">
        {/* Dark gradient overlay for text readability */}
        <div className="absolute inset-0 bg-linear-to-t from-black/80 via-black/40 to-transparent" />

        <div className="relative z-10 space-y-2">
          {/* Tags row */}
          <div className="flex flex-wrap gap-1.5">
            {tags.slice(0, 3).map((tag) => (
              <Badge
                key={tag}
                variant="outline"
                className={cn("text-[10px] py-0 h-5 backdrop-blur-sm", getTagColor(tag))}
              >
                {tag}
              </Badge>
            ))}
            {tags.length > 3 && (
              <Badge
                variant="secondary"
                className="text-[10px] py-0 h-5 bg-white/10 text-white/80 border-white/20 backdrop-blur-sm"
              >
                +{tags.length - 3}
              </Badge>
            )}
          </div>

          {/* Title */}
          <h3
            className="text-lg font-bold leading-tight line-clamp-2 text-white group-hover:text-primary transition-colors"
            dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
          />

          {/* Description */}
          {doc.description && (
            <p className="text-xs text-white/70 line-clamp-2 leading-relaxed">
              {doc.description}
            </p>
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
            <IconMountain className="size-6 text-primary" />
            <span className="font-black text-sm tracking-wide uppercase">
              WKND
            </span>
            <Separator orientation="vertical" className="h-4 mx-1" />
            <span className="text-xs text-muted-foreground font-medium tracking-wider uppercase hidden sm:inline">
              Adventure Awaits
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
        {/* Teal/yellow gradient background */}
        <div className="absolute inset-0 bg-linear-to-b from-primary/10 via-teal-600/5 to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-yellow-500/8 via-transparent to-transparent" />

        {/* Decorative animated elements */}
        <div className="absolute top-8 left-[10%] size-2.5 rounded-full bg-yellow-400/20 animate-pulse" />
        <div className="absolute top-20 right-[15%] size-1.5 rounded-full bg-teal-400/25 animate-pulse [animation-delay:1s]" />
        <div className="absolute bottom-10 left-[30%] size-1 rounded-full bg-primary/20 animate-pulse [animation-delay:0.5s]" />
        <div className="absolute top-28 left-[55%] size-2 rounded-full bg-cyan-400/15 animate-pulse [animation-delay:1.5s]" />
        <div className="absolute bottom-6 right-[20%] size-1.5 rounded-full bg-yellow-300/20 animate-pulse [animation-delay:2s]" />
        <div className="absolute top-14 right-[45%] size-1 rounded-full bg-teal-500/15 animate-pulse [animation-delay:0.7s]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              <div className="mb-3 flex items-center justify-center gap-2 text-primary/70 text-sm font-medium uppercase tracking-widest">
                <Separator className="w-8 bg-primary/30" />
                Your guide to outdoor adventure
                <Separator className="w-8 bg-primary/30" />
              </div>
              <h1 className="text-6xl sm:text-8xl font-black tracking-tighter bg-linear-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2">
                WKND
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Surfing, skiing, climbing, trekking — find your next escape.
                {totalCount > 0 && (
                  <span className="block mt-1 text-primary/80 font-medium">
                    {totalCount} adventures indexed
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
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-yellow-500/30 via-teal-500/20 to-yellow-500/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search adventures... surfing, skiing, Yosemite..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/60"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium bg-linear-to-r from-yellow-600 to-teal-600 hover:from-yellow-500 hover:to-teal-500 text-white shadow-md cursor-pointer">
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
              Loading adventures...
            </p>
          </div>
        )}

        {/* Error state */}
        {turing.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconMountain className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              Trail closed. Try another route. {turing.error}
            </p>
            <Button variant="outline" size="sm" onClick={turing.showAll}>
              Back to base camp
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
                    adventure{totalCount === 1 ? "" : "s"} found
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

              {/* Magazine grid */}
              {hasResults ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                  {turing.documents.map((doc, i) => (
                    <ArticleCard
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
                    No adventures found. Time to explore new terrain.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={turing.showAll}
                  >
                    Show all adventures
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
            <IconMountain className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md">
              Search for your next adventure or browse the full collection.
              Every article is a gateway to the wild.
            </p>
          </div>
        )}
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
