import { useEffect } from "react";
import { LandingPage } from "@/pages/landing";
import { AemPage } from "@/pages/aem";
import { ConnectorsPage } from "@/pages/connectors";
import { TrustPage } from "@/pages/trust";
import { ConsentBanner } from "@/components/consent-banner";

/**
 * Routing is one table the prerender and the client both read: no react-router
 * and no SSG framework. Navigation is plain `<a href>` full loads, because every
 * route is prerendered to its own static HTML anyway.
 *
 * A route's path is declared once, here, and `entry-server.tsx` attaches the
 * title and description to the same string. Adding a page is a row here plus a
 * row there, and the prerender refuses if you write only one of them.
 */
export const ROUTES: Record<string, () => React.JSX.Element> = {
  "/": LandingPage,
  "/aem": AemPage,
  "/connectors": ConnectorsPage,
  "/trust": TrustPage,
};

/**
 * Resolve a pathname to a page, tolerating the trailing slash a server adds.
 * Trimmed by index rather than by `/\/+$/`, which backtracks on `/////…`.
 */
export function matchRoute(path: string): (() => React.JSX.Element) | null {
  let end = path.length;
  while (end > 1 && path[end - 1] === "/") end -= 1;
  return ROUTES[path.slice(0, end)] ?? null;
}

export default function App({ path = "/" }: Readonly<{ path?: string }>) {
  // A cross-page link to a home anchor ("/#how") full-loads "/" and relies on
  // the browser's native hash scroll, but the section mounts after React does,
  // so re-run the scroll once the element exists, and on hashchange.
  useEffect(() => {
    const scrollToHash = (smooth: boolean) => {
      const { hash } = window.location;
      if (hash.length < 2) return;
      const id = decodeURIComponent(hash.slice(1));
      let tries = 0;
      const tick = () => {
        const el = document.getElementById(id);
        if (el) {
          el.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
        } else if (tries++ < 30) {
          requestAnimationFrame(tick);
        }
      };
      tick();
    };
    scrollToHash(false);
    const onHashChange = () => scrollToHash(true);
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const Page = matchRoute(path) ?? LandingPage;
  return (
    <>
      <Page />
      <ConsentBanner />
    </>
  );
}
