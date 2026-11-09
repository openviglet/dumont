import { useEffect, useState } from "react";
import { ArrowRight, ArrowUpRight, ChevronDown, Menu, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ModeToggle } from "@/components/mode-toggle";
import { Container, Logo } from "@/components/brand";
import { DESTINATIONS, LINKS, NAV_AEM, NAV_GROUPS, NAV_LINKS, PRODUCTS, sitePath } from "@/lib/site-links";
import { cn } from "@/lib/utils";

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  return (
    <header
      className={cn(
        "sticky top-0 z-50 backdrop-blur-md transition-colors",
        scrolled
          ? "border-b border-border bg-background/80"
          : "border-b border-transparent bg-background/60"
      )}
    >
      <Container className="flex h-16 items-center gap-3 sm:gap-4">
        <div className="flex shrink-0 items-center gap-2.5">
          {/* The full name stays visible at every width (hub STRATEGY I.7): a phone
              gets a smaller mark and type, never the mark alone. */}
          <a href={sitePath("/")} className="text-sm sm:text-base" aria-label={`${PRODUCTS.dumont.fullName} home`}>
            <Logo className="hidden sm:inline-flex" />
            <Logo size={26} className="gap-1.5 sm:hidden" />
          </a>
          <a
            href={LINKS.viglet}
            className="hidden items-center gap-1 border-l border-border pl-2.5 text-xs font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground sm:inline-flex"
          >
            part of viglet.org
            <ArrowUpRight className="size-3" />
          </a>
        </div>

        <nav className="ml-4 hidden items-center gap-5 lg:flex xl:ml-6 xl:gap-6">
          {NAV_LINKS.map((item) => (
            <a
              key={item.label}
              href={item.href}
              className="text-sm font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground"
            >
              {item.label}
            </a>
          ))}
          {/* AEM is the deepest connector and the audience most likely to be
              looking for one, so it gets a top-level link instead of a dropdown
              row: an ordinary link, as viglet.org's nav has no highlight pills. */}
          <a
            href={NAV_AEM.href}
            className="text-sm font-semibold whitespace-nowrap text-muted-foreground transition-colors hover:text-foreground"
          >
            {NAV_AEM.label}
          </a>
          {NAV_GROUPS.map((group) => (
            <DropdownMenu key={group.label}>
              <DropdownMenuTrigger className="group flex items-center gap-1 text-sm font-semibold whitespace-nowrap text-muted-foreground outline-hidden transition-colors hover:text-foreground data-[state=open]:text-foreground">
                {group.label}
                <ChevronDown className="size-3.5 transition-transform group-data-[state=open]:rotate-180" />
              </DropdownMenuTrigger>
              {/* Radix returns focus to the trigger on close, and that focus()
                  scrolls the top-of-page trigger back into view, cancelling the
                  native hash jump to the section just clicked. */}
              <DropdownMenuContent
                align="start"
                className="min-w-52"
                onCloseAutoFocus={(e) => e.preventDefault()}
              >
                {group.items.map((item) => (
                  <DropdownMenuItem key={item.label} asChild>
                    <a href={item.href} className="w-full cursor-pointer font-medium">
                      {item.label}
                    </a>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
          <Button asChild size="sm">
            <a href={DESTINATIONS.getStarted.href}>
              {DESTINATIONS.getStarted.label}
              <ArrowRight className="hidden size-4 sm:block" />
            </a>
          </Button>
          <ModeToggle />
          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            aria-expanded={menuOpen}
            className="flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
          >
            {menuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </Container>

      {menuOpen && (
        <nav className="border-t border-border bg-background/95 backdrop-blur-md lg:hidden">
          <Container className="flex flex-col py-3">
            {NAV_LINKS.map((item) => (
              <a
                key={item.label}
                href={item.href}
                onClick={closeMenu}
                className="rounded-lg px-2 py-2.5 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
              >
                {item.label}
              </a>
            ))}
            {NAV_GROUPS.map((group) => (
              <div key={group.label} className="mt-2 border-t border-border/60 pt-2">
                <p className="px-2 py-1 text-[0.7rem] font-bold tracking-wide text-muted-foreground/70 uppercase">
                  {group.label}
                </p>
                {group.items.map((item) => (
                  <a
                    key={item.label}
                    href={item.href}
                    onClick={closeMenu}
                    className="block rounded-lg px-2 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                  >
                    {item.label}
                  </a>
                ))}
              </div>
            ))}
            <a
              href={LINKS.viglet}
              onClick={closeMenu}
              className="mt-3 inline-flex w-fit items-center gap-1 px-2 text-xs font-semibold text-muted-foreground transition-colors hover:text-foreground"
            >
              part of viglet.org
              <ArrowUpRight className="size-3" />
            </a>
          </Container>
        </nav>
      )}
    </header>
  );
}
