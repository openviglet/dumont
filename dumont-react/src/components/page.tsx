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

export const Page: React.FC<PageProps> = ({ turIcon: TurIcon, title, urlBase }) => {
  const { id } = useParams() as { id: string };
  const fullUrlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}/${urlBase}`;

  // Register this page's label as the root breadcrumb. Sub-pages can append more
  // levels via useSubPageBreadcrumb.
  const item = useMemo(() => ({ label: title, href: fullUrlBase }), [title, fullUrlBase]);
  useSubPageBreadcrumb(item);

  return (
    <>
      <PageHeader turIcon={TurIcon} title={title} urlBase={fullUrlBase} />
      <main className="flex flex-1 flex-col pt-2 md:pt-4 max-md:[&_.px-6]:px-2">
        <Outlet />
      </main>
    </>
  );
};
