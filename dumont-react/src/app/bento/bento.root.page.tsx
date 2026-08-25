import { dumontNavGroups, dumontNavItems, visibleNavSurfaces } from "@/app/nav.const";
import { ROUTES } from "@/app/routes.const";
import { DumontLogo } from "@/components/logo/dumont-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { usePlugin } from "@/contexts/plugin.context";
import { DumUserService } from "@/services/auth/user.service";
import { IconSearch } from "@tabler/icons-react";
import { UserProvider as VigUserProvider } from "@viglet/viglet-design-system";
import {
  BentoBackToTop,
  BentoCommandPalette,
  BentoNavRail,
  BentoUserMenu,
} from "@viglet/viglet-design-system/bento";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Outlet, useParams } from "react-router-dom";

/**
 * The palette's shortcut hint, honest per platform: macOS uses ⌘, everything else Ctrl (the
 * handler accepts both). A bare ⌘K on Windows tells a user to press a key their keyboard has not
 * got.
 */
const IS_MAC =
  typeof navigator !== "undefined" &&
  /Mac|iPhone|iPad|iPod/.test(navigator.userAgent || "");
const SHORTCUT_HINT = IS_MAC ? "⌘K" : "Ctrl K";

const STANDALONE_ID = "local";

/*
 * The provider is the design system's; which endpoint holds *this* product's user is ours.
 * Declared at module scope so the reference is stable across renders and the provider does not
 * read every render as a new fetch.
 */
const dumUserService = new DumUserService();
const fetchCurrentUser = () => dumUserService.get();

/**
 * The bento shell — the second era's root, mounted beside the console one (D37).
 *
 * Deliberately *parallel* rather than a replacement. The two eras mount different chrome and a
 * page cannot be half of each: `ConsoleRootPage` provides a `SidebarProvider` whose collapse
 * state and content-pushing the bento components neither expect nor tolerate. So a surface
 * migrates by being added to this tree while its console route keeps serving, and moves back the
 * same way. Nothing here redirects — both trees answer, and which one a person reaches is a
 * decision D37 does not make.
 *
 * Three pieces and no provider, which is the package's own contract: the rail down the left, the
 * user menu in the header, back-to-top at the corner. The console era needs a provider because
 * its sidebar collapses, remembers and pushes content around; the rail does none of that — it is
 * fixed, one width, and hidden below `md`. A provider here would be state nothing reads.
 *
 * The application's own providers (theme, plugins, user, breadcrumb) already wrap both trees in
 * `App`, so this root adds none of them.
 */
export default function BentoRootPage() {
  /*
   * The design system keeps its own user context, and `BentoUserMenu` reads *that* one — not
   * Dumont's `@/contexts/user.context`, which the console chrome uses. Two providers rather than
   * one because they answer different questions: Dumont's redirects to the login page when the
   * session is gone, the package's reports loading / ready / failed so the menu can stop
   * rendering a skeleton for ever.
   *
   * It has to sit *above* the shell: a component never sees a context it mounts itself, so
   * wrapping in the same function that renders the menu throws `useCurrentUser must be used
   * within UserProvider` on every bento route.
   */
  return (
    <VigUserProvider fetchUser={fetchCurrentUser}>
      <BentoShell />
    </VigUserProvider>
  );
}

function BentoShell() {
  const { t } = useTranslation();
  const { id } = useParams<{ id?: string }>();
  const { providers } = usePlugin();
  const integrationId = id ?? STANDALONE_ID;
  const home = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`;
  const [paletteOpen, setPaletteOpen] = useState(false);

  /*
   * D39 — the rail and the palette, from one declaration.
   *
   * `visibleNavSurfaces` answers which surfaces this reader may see, because that is a product
   * question: the package renders what it is handed and has no way to read a connector's plugin
   * list. Both derivations take the *same* filtered set, so `⌘K` and the rail cannot disagree
   * about what exists.
   */
  const visible = useMemo(() => visibleNavSurfaces(providers), [providers]);
  const navGroups = useMemo(
    () => dumontNavGroups(integrationId, visible),
    [integrationId, visible],
  );
  const navItems = useMemo(
    () => dumontNavItems(integrationId, visible),
    [integrationId, visible],
  );

  /*
   * ⌘K / Ctrl+K, registered once at the root so it works from any bento surface. Ignored while
   * the reader is typing, so it never eats a keystroke meant for a field.
   */
  useEffect(() => {
    function isTyping(target: EventTarget | null): boolean {
      const el = target as HTMLElement | null;
      const tag = el?.tagName;
      return tag === "INPUT" || tag === "TEXTAREA" || Boolean(el?.isContentEditable);
    }
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        if (isTyping(event.target)) return;
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <div className="bento-rail-gutter relative min-h-svh w-full bg-background text-foreground">
      {/*
       * Skip link: the first focusable element, so a keyboard user can jump past the rail and the
       * header instead of tabbing through both on every surface. Hidden until focused.
       */}
      <a
        href="#bento-main-content"
        className="sr-only rounded-full focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:border focus:border-border/60 focus:bg-card focus:px-4 focus:py-2 focus:text-sm focus:shadow-lg"
      >
        {t("bento.skipToContent", { defaultValue: "Skip to content" })}
      </a>

      {/*
       * The rail renders only sections that have a hub page to open, so today it carries Home
       * alone — Dumont has no hubs yet. It is handed the declaration all the same: the day a
       * section gains an `areaRoute`, the rail lists it without a second list being written.
       */}
      <BentoNavRail
        groups={navGroups}
        homeRoute={home}
        homeLabel={t("common.home", { defaultValue: "Home" })}
      />

      <header className="bento-shell-header sticky top-0 z-30 flex shrink-0 items-center justify-between gap-3 border-b border-border/40 bg-background/55 px-4 py-3 backdrop-blur-xl backdrop-saturate-150 md:px-8">
        <Link to={home} className="flex items-center gap-2">
          <DumontLogo className="size-8" />
          <span className="text-sm font-medium tracking-tight md:text-base">Viglet Dumont</span>
        </Link>
        <div className="flex items-center gap-2">
          {/*
           * On desktop this doubles as the hint that ⌘K exists; below `md`, where the rail is
           * hidden, it is the only way to reach a surface that is not on screen.
           */}
          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            aria-label={t("bento.palette.open", { defaultValue: "Open command palette" })}
            className="bento-tile bento-tile-clickable inline-flex items-center gap-2 rounded-full border border-border/60 bg-card/60 py-1.5 pl-3 pr-2 text-sm text-muted-foreground backdrop-blur hover:text-foreground"
          >
            <IconSearch size={16} aria-hidden />
            <span className="hidden sm:inline">
              {t("bento.palette.search", { defaultValue: "Search" })}
            </span>
            <kbd className="hidden rounded border border-border/60 bg-muted px-1.5 py-0.5 text-[10px] font-medium tracking-wide sm:inline">
              {SHORTCUT_HINT}
            </kbd>
          </button>
          <ModeToggle />
          <BentoUserMenu accountRoute={ROUTES.USER_ACCOUNT} logoutUrl={ROUTES.LOGOUT} />
        </div>
      </header>

      <main
        id="bento-main-content"
        tabIndex={-1}
        aria-label={t("bento.mainContent", { defaultValue: "Main content" })}
        className="relative z-10 mx-auto w-full max-w-7xl px-4 py-6 focus:outline-none md:px-8 md:py-10"
      >
        <Outlet />
      </main>

      <BentoBackToTop />
      {/*
       * The palette needs no hub, so it reads the declaration in full today: a migrated surface
       * navigates inside the shell, one that has not moved links out to the console and is marked
       * as leaving. That is what lets `⌘K` reach all 39 while only one of them has migrated.
       */}
      <BentoCommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} items={navItems} />
    </div>
  );
}
