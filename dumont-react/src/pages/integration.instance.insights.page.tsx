import { SubPageHeader } from "@/components/sub.page.header";
import { AiSummaryPanel } from "@/components/ai-summary-panel";
import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceInsightsPage() {
    const { id } = useParams() as { id: string };
    const { t } = useTranslation();

    return (
        <>
            <SubPageHeader
                icon={IconSparkles}
                name={t("integration.insights.title")}
                feature={t("integration.insights.title")}
                description={t("integration.insights.description")}
            />
            <AiSummaryPanel
                endpoint={`/v2/integration/${id}/connector/summary`}
                i18nPrefix="integration.insights"
            />
        </>
    );
}
