import IntegrationInstanceSourceModelPage from "@/pages/integration.instance.source.model.page";

/**
 * One AEM plugin model, in bento chrome (D60).
 *
 * Reached only from the models tab of the AEM source detail page, never from the rail or the
 * palette, which is why no nav entry declares it and why the migration count never counted it —
 * and why nothing failed when D47 put the page above it on this tree and left this route unserved.
 */
export default function IntegrationInstanceSourceModelBentoPage() {
  return (
    <IntegrationInstanceSourceModelPage />
  );
}
