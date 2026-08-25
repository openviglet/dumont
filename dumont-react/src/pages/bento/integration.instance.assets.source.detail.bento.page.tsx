import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento";
import IntegrationInstanceAssetsSourcePage from "@/pages/integration.instance.assets.source.page";

/**
 * The assets source detail page, in bento chrome (D47).
 *
 * The console page itself, rendered under a chrome declaration. Its form is 500 lines of real
 * field logic and only its header and section cards differ between the two eras, so both read
 * `useSectionChrome` and render accordingly - forking the page to migrate it would leave two
 * copies of that logic drifting for the length of the migration.
 *
 * Routes inside resolve through `ROUTES.INTEGRATION_INSTANCE`, which reads its prefix from the
 * live URL. That was the trap while detail pages lived only on the console tree; now that they
 * serve on both, it is exactly right - a save or a cancel from here stays in the shell it
 * started in.
 */
export default function IntegrationInstanceAssetsSourceDetailBentoPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <IntegrationInstanceAssetsSourcePage />
    </SectionCardChromeProvider>
  );
}
