import IntegrationInstanceIndexAdminPage from "@/pages/integration.instance.indexing.manager.page";

/**
 * The indexing manager, in bento chrome (D59) — the last Integration surface to move.
 *
 * The console page under a chrome declaration, and the one form whose bar is not Save and Cancel:
 * it submits an operation, so it renders `AdaptiveSaveBar` (Reset plus the action's own verb)
 * rather than `AdaptiveFormHeader`. That bar was the console's last `StickySaveBar`, and the shim
 * behind it went with this task.
 */
export default function IntegrationInstanceIndexingManagerBentoPage() {
  return (
    <IntegrationInstanceIndexAdminPage />
  );
}
