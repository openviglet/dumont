import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useMemo } from "react";
import { Outlet, useParams } from "react-router-dom";
import { ROUTES } from "@/app/routes.const";
import { PageHeader } from "./page-header";

interface PageProps {
  turIcon?: React.ElementType;
  title: string;
  /** Path segment under the integration instance (e.g. "source", "db-source"). */
  urlBase: string;
}

/**
 * Detects whether Dumont is running standalone (served by its own backend) or
 * as a Module Federation remote loaded inside another host (e.g. Turing, which
 * already renders its own PageHeader + breadcrumb). The flag is set in
 * `main.tsx` during the standalone bootstrap and is absent when the app is
 * mounted via `loadRemote`.
 */
function isStandalone(): boolean {
  return (
    globalThis.window !== undefined
    && (globalThis as unknown as { __DUMONT_STANDALONE__?: boolean }).__DUMONT_STANDALONE__ === true
  );
}

export const Page: React.FC<PageProps> = ({ turIcon: TurIcon, title, urlBase }) => {
  const { id } = useParams() as { id: string };
  const fullUrlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}/${urlBase}`;

  // Register this page's label as the root breadcrumb. Sub-pages can append more
  // levels via useSubPageBreadcrumb. This works in both standalone and remote
  // mode — in remote mode, the host (Turing) consumes the breadcrumb via the
  // shared BreadcrumbContext (singleton).
  const item = useMemo(() => ({ label: title, href: fullUrlBase }), [title, fullUrlBase]);
  useSubPageBreadcrumb(item);

  const standalone = isStandalone();

  return (
    <>
      {standalone && (
        <PageHeader turIcon={TurIcon} title={title} urlBase={fullUrlBase} />
      )}
      <main className="flex flex-1 flex-col pt-2 md:pt-4 max-md:[&_.px-6]:px-2">
        <Outlet />
      </main>
    </>
  );
};
