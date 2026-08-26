import { ROUTES } from "@/app/routes.const";
import { IntegrationIndexingRulesForm } from "@/components/integration/integration.indexing.rules.form";
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model";
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

export default function IntegrationInstanceIndexingRulePage() {
  const navigate = useNavigate();
  const { id, ruleId } = useParams() as { id: string, ruleId: string };
  const [integrationIndexingRules, setIntegrationIndexingRules] = useState<TurIntegrationIndexingRule>({} as TurIntegrationIndexingRule);
  const turIntegrationIndexingRuleService = useMemo(
    () => new TurIntegrationIndexingRuleService(id || ""),
    [id]
  );
  /*
   * D53 - derived, not state. `isNew` is a fact about the route, so a `useState` seeded `true`
   * and corrected inside an effect renders the wrong value first and the right one after, and
   * the correction is a `setState` in an effect body that React 19 refuses.
   */
  const isNew = ruleId === "new";
  const [open, setOpen] = useState(false);
  const { t } = useTranslation();
  useEffect(() => {
    if (!isNew) {
      turIntegrationIndexingRuleService.get(ruleId).then(setIntegrationIndexingRules);
    }
  }, [ruleId, isNew, turIntegrationIndexingRuleService]);
  async function onDelete() {
    try {
      if (await turIntegrationIndexingRuleService.delete(integrationIndexingRules)) {
        toast.success(t("integration.indexingRules.deleted", { name: integrationIndexingRules.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule`);
      } else {
        toast.error(t("integration.indexingRules.notDeleted", { name: integrationIndexingRules.name }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.indexingRules.notDeleted", { name: integrationIndexingRules.name }));
    }
    setOpen(false);
  }
  return (
    <IntegrationIndexingRulesForm
      value={integrationIndexingRules}
      integrationId={id}
      isNew={isNew}
      onDelete={onDelete}
      open={open}
      setOpen={setOpen}
    />
  )
}
