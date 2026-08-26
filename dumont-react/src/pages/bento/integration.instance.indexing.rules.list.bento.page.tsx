import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model";
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoEntityList, useEntityList } from "./bento-entity-list";

/**
 * The indexing rules list, in bento chrome (D59).
 *
 * The first list on this tree that is not a source, and it needed nothing added to the shared
 * component beyond a label override — the console page was a `GridList` of name plus description
 * over a `BlankSlate`, which is what the six source lists were too. That is the whole argument for
 * having named the shared piece after the shape.
 */
export default function IntegrationInstanceIndexingRulesListBentoPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const service = useMemo(() => new TurIntegrationIndexingRuleService(id), [id]);
  const { items, error } = useEntityList<TurIntegrationIndexingRule>(service);

  return (
    <BentoEntityList<TurIntegrationIndexingRule>
      surfaceId="indexing-rule"
      i18nPrefix="integration.indexingRules"
      items={items}
      error={error}
      newLabel={t("integration.indexingRules.newRule")}
      itemKey={(rule) => rule.id}
      itemTitle={(rule) => rule.name}
      itemDescription={(rule) => rule.description}
    />
  );
}
