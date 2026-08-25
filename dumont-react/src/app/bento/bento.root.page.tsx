import { ROUTES } from "@/app/routes.const";
import { DumontLogo } from "@/components/logo/dumont-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { DumUserService } from "@/services/auth/user.service";
import { UserProvider as VigUserProvider } from "@viglet/viglet-design-system";
import { BentoBackToTop, BentoNavRail, BentoUserMenu } from "@viglet/viglet-design-system/bento";
import { useTranslation } from "react-i18next";
import { Link, Outlet, useParams } from "react-router-dom";

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
  const integrationId = id ?? STANDALONE_ID;
  const home = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integrationId}`;

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
       * No sections yet, on purpose. The rail lists section *hubs*, never leaves — that is what
       * keeps it a fixed, small height however many of the 39 surfaces migrate, and it is the flat
       * console sidebar's overflow inverted. Dumont has no such declaration to hand it: its map
       * still lives as markup inside `app-sidebar`, and turning that into data both the rail and
       * the `⌘K` palette can read is D39. Passing a hand-copied array here would let the rail
       * advertise bento routes this tree does not serve, so until D39 the mosaic below is the
       * navigation and the rail carries Home alone.
       */}
      <BentoNavRail
        groups={[]}
        homeRoute={home}
        homeLabel={t("common.home", { defaultValue: "Home" })}
      />

      <header className="bento-shell-header sticky top-0 z-30 flex shrink-0 items-center justify-between gap-3 border-b border-border/40 bg-background/55 px-4 py-3 backdrop-blur-xl backdrop-saturate-150 md:px-8">
        <Link to={home} className="flex items-center gap-2">
          <DumontLogo className="size-8" />
          <span className="text-sm font-medium tracking-tight md:text-base">Viglet Dumont</span>
        </Link>
        <div className="flex items-center gap-2">
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
    </div>
  );
}
