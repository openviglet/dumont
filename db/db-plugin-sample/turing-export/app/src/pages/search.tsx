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
  IconDeviceGamepad2,
  IconFilter,
  IconLoader2,
  IconMoodEmpty,
  IconSearch,
  IconTag,
  IconUser,
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
        Filter By
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

/* ── Game card ── */
function GameCard({
  doc,
  onClick,
}: Readonly<{
  doc: ResolvedDocument;
  onClick: () => void;
}>) {
  const fields = doc.raw.fields;
  const author = fields.author ?? "";
  const tags: string[] = [];
  if (Array.isArray(fields.tags)) {
    tags.push(...fields.tags);
  } else if (fields.tags) {
    tags.push(String(fields.tags));
  }

  return (
    <button
      onClick={onClick}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-border/50 bg-card text-left transition-all duration-300 hover:border-primary/40 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {/* Glow effect on hover */}
      <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none bg-linear-to-b from-primary/5 via-transparent to-transparent" />

      <div className="flex flex-1 flex-col gap-2.5 p-5">
        {/* Title */}
        <h3
          className="text-base font-semibold leading-tight line-clamp-2 group-hover:text-primary transition-colors"
          dangerouslySetInnerHTML={{ __html: fields.title ?? doc.title }}
        />

        {/* Abstract */}
        {(fields.abstract ?? doc.description) && (
          <p className="text-sm text-muted-foreground line-clamp-3 leading-relaxed">
            {fields.abstract ?? doc.description}
          </p>
        )}

        {/* Tags */}
        <div className="flex flex-wrap gap-1.5 mt-auto pt-1">
          {tags.slice(0, 3).map((t) => (
            <Badge
              key={t}
              variant="outline"
              className={cn("text-[10px] py-0 h-5", getGenreColor(t))}
            >
              <IconTag className="size-2.5 mr-0.5" />
              {t}
            </Badge>
          ))}
        </div>

        {/* Author */}
        {author && (
          <div className="flex items-center gap-1.5 pt-1">
            <Badge
              variant="secondary"
              className="text-[10px] py-0 h-5 bg-secondary/60"
            >
              <IconUser className="size-2.5 mr-0.5" />
              {author}
            </Badge>
          </div>
        )}
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
            <IconDeviceGamepad2 className="size-6 text-primary" />
            <span className="font-bold text-sm tracking-wide uppercase">
              The Arcade Vault
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
        {/* Neon green→purple gradient background */}
        <div className="absolute inset-0 bg-linear-to-b from-primary/10 via-purple-600/5 to-background" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--tw-gradient-stops))] from-green-500/8 via-transparent to-transparent" />

        {/* Decorative neon particles */}
        <div className="absolute top-8 left-[12%] size-2 rounded-full bg-green-400/20 animate-pulse" />
        <div className="absolute top-16 right-[18%] size-1.5 rounded-full bg-purple-400/25 animate-pulse [animation-delay:1s]" />
        <div className="absolute bottom-12 left-[35%] size-1 rounded-full bg-green-300/20 animate-pulse [animation-delay:0.5s]" />

        <div className="relative mx-auto max-w-7xl px-4 sm:px-6 text-center">
          {isLanding && (
            <>
              <div className="mb-3 flex items-center justify-center gap-2 text-primary/70 text-sm font-medium uppercase tracking-widest">
                <Separator className="w-8 bg-primary/30" />
                Welcome to
                <Separator className="w-8 bg-primary/30" />
              </div>
              <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight bg-linear-to-b from-foreground via-foreground to-muted-foreground bg-clip-text text-transparent pb-2 uppercase">
                The Arcade Vault
              </h1>
              <p className="mt-4 max-w-xl mx-auto text-muted-foreground text-base sm:text-lg leading-relaxed">
                Explore the golden age of gaming. From Pac-Man to Street
                Fighter, discover the machines that ate your quarters.
                {totalCount > 0 && (
                  <span className="block mt-1 text-primary/80 font-medium">
                    {totalCount} game{totalCount === 1 ? "" : "s"} in the vault
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
              <div className="absolute -inset-0.5 rounded-xl bg-linear-to-r from-green-500/30 via-purple-500/20 to-green-500/30 opacity-0 group-focus-within:opacity-100 blur-sm transition-opacity duration-300" />
              <div className="relative flex items-center gap-2 rounded-xl border border-border/60 bg-card/80 backdrop-blur-sm px-4 py-2 shadow-lg shadow-primary/5 group-focus-within:border-primary/40">
                <IconSearch className="size-5 text-muted-foreground shrink-0" />
                <TuringSearchField.Input
                  placeholder="Search the arcade... Pac-Man, shooter, Namco..."
                  className="flex-1 border-0 shadow-none bg-transparent focus-visible:ring-0 focus-visible:outline-none text-base placeholder:text-muted-foreground/60"
                />
                <TuringSearchField.Button className="shrink-0 inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium bg-linear-to-r from-green-600 to-purple-600 hover:from-green-500 hover:to-purple-500 text-white shadow-md cursor-pointer">
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
              Inserting coin...
            </p>
          </div>
        )}

        {/* Error state */}
        {turing.status === "error" && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-24 text-center">
            <IconDeviceGamepad2 className="size-10 text-destructive/60" />
            <p className="text-sm text-destructive/80">
              GAME OVER. Connection lost. {turing.error}
            </p>
            <Button variant="outline" size="sm" onClick={turing.showAll}>
              Continue?
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
                    game{totalCount === 1 ? "" : "s"} found
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
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {turing.documents.map((doc, i) => (
                    <GameCard
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
                    No games found. Insert coin to try again.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={turing.showAll}
                  >
                    Show all games
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
            <IconDeviceGamepad2 className="size-10 text-primary/40" />
            <p className="text-muted-foreground text-sm max-w-md">
              Enter a search query or browse the full collection. Every cabinet
              has a story to tell.
            </p>
          </div>
        )}
      </main>

      {/* ── Footer ── */}
      <footer className="border-t border-border/30 bg-card/30">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 py-6 flex items-center justify-between text-xs text-muted-foreground">
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
