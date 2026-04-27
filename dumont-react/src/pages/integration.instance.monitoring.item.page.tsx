import { ROUTES } from "@/app/routes.const";
import { BadgeAemEnv } from "@/components/badge-aem-env";
import { BadgeIndexingStatus } from "@/components/badge-indexing-status";
import { BadgeLocale } from "@/components/badge-locale";
import { BadgeSites } from "@/components/badge-sites";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { SectionCard } from "@/components/ui/section-card";
import type { TurIntegrationIndexingDetail } from "@/models/integration/integration-indexing-detail.model";
import { TurIntegrationMonitoringService } from "@/services/integration/integration-monitoring.service";
import {
    IconArrowLeft,
    IconFileSearch,
    IconGitBranch,
    IconInfoCircle,
    IconLink,
    IconSearch,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

function formatDateTime(value?: string): string {
    if (!value) return "-";
    const d = new Date(value);
    if (isNaN(d.getTime())) return "-";
    return d.toLocaleString(undefined, {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    });
}

function InfoRow({
    label,
    children,
}: Readonly<{ label: string; children: React.ReactNode }>) {
    return (
        <div className="flex items-start justify-between gap-4 py-2 border-b last:border-b-0">
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className="text-sm font-medium font-mono break-all text-right">
                {children}
            </span>
        </div>
    );
}

export default function IntegrationInstanceMonitoringItemPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { id, source, itemId } = useParams<{ id: string; source?: string; itemId: string }>();

    const [item, setItem] = useState<TurIntegrationIndexingDetail>();
    const [error, setError] = useState<string | null>(null);

    const monitoringService = useMemo(
        () => (id ? new TurIntegrationMonitoringService(id) : null),
        [id],
    );

    useEffect(() => {
        let cancelled = false;
        if (!monitoringService || !itemId) return;
        monitoringService
            .getItem(itemId)
            .then((data) => {
                if (!cancelled) setItem(data);
            })
            .catch((err) => {
                console.error(err);
                if (!cancelled) setError(t("integration.monitoring.item.loadFailed"));
            });
        return () => {
            cancelled = true;
        };
    }, [monitoringService, itemId, t]);

    const monitoringListUrl = `${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring/${source ?? "all"}`;

    const searchUrl = useMemo(() => {
        if (!item) return "";
        const sites = Array.isArray(item.sites) ? item.sites : [];
        const siteId = sites.length > 0 ? sites[0] : "";
        if (!siteId || !item.objectId) return "";
        return encodeURI(
            `/sn/${siteId}/?_setlocale=${item.locale ?? ""}&q=id:"${item.objectId}"`,
        );
    }, [item]);

    if (!id) {
        return <div>{t("integration.indexingStats.invalidId")}</div>;
    }

    return (
        <LoadProvider
            checkIsNotUndefined={item}
            error={error}
            tryAgainUrl={monitoringListUrl}
        >
            <SubPageHeader
                icon={IconFileSearch}
                name={t("integration.monitoring.item.title")}
                feature={t("integration.monitoring.title")}
                description={t("integration.monitoring.item.description")}
            />
            {item && (
                <div className="py-6 px-6 space-y-4">
                    <div className="flex items-center justify-between">
                        <GradientButton
                            variant="outline"
                            size="sm"
                            onClick={() => navigate(monitoringListUrl)}
                            className="h-8 gap-1.5"
                        >
                            <IconArrowLeft className="h-3.5 w-3.5" />
                            {t("integration.monitoring.item.backToList")}
                        </GradientButton>
                        {searchUrl && (
                            <GradientButton
                                variant="default"
                                size="sm"
                                className="h-8 gap-1.5"
                                onClick={() =>
                                    window.open(searchUrl, "_blank", "noopener,noreferrer")
                                }
                            >
                                <IconSearch className="h-3.5 w-3.5" />
                                {t("integration.monitoring.item.openSearch")}
                            </GradientButton>
                        )}
                    </div>

                    <SectionCard variant="blue">
                        <SectionCard.StaticHeader
                            icon={IconInfoCircle}
                            title={t("integration.monitoring.item.overview")}
                            description={t("integration.monitoring.item.overviewDesc")}
                        />
                        <SectionCard.Content>
                            <InfoRow label={t("integration.monitoring.item.objectId")}>
                                {item.objectId ?? "-"}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.status")}>
                                <BadgeIndexingStatus status={item.status} />
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.environment")}>
                                {item.environment ? (
                                    <BadgeAemEnv environment={item.environment} />
                                ) : (
                                    "-"
                                )}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.locale")}>
                                {item.locale ? <BadgeLocale locale={item.locale} /> : "-"}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.provider")}>
                                {item.provider ?? "-"}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.source")}>
                                {item.source ?? "-"}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.modificationDate")}>
                                {formatDateTime(item.modificationDate)}
                            </InfoRow>
                            <InfoRow label={t("integration.monitoring.item.created")}>
                                {formatDateTime(item.created)}
                            </InfoRow>
                            {item.checksum && (
                                <InfoRow label={t("integration.monitoring.item.checksum")}>
                                    {item.checksum}
                                </InfoRow>
                            )}
                            {item.transactionId && (
                                <InfoRow label={t("integration.monitoring.item.transactionId")}>
                                    {item.transactionId}
                                </InfoRow>
                            )}
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="cyan">
                        <SectionCard.StaticHeader
                            icon={IconLink}
                            title={t("integration.monitoring.item.sites")}
                            description={t("integration.monitoring.item.sitesDesc")}
                        />
                        <SectionCard.Content>
                            {Array.isArray(item.sites) && item.sites.length > 0 ? (
                                <BadgeSites sites={item.sites} />
                            ) : (
                                <span className="text-sm text-muted-foreground">
                                    {t("integration.monitoring.item.noSites")}
                                </span>
                            )}
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="violet">
                        <SectionCard.StaticHeader
                            icon={IconGitBranch}
                            title={t("integration.monitoring.item.dependencies")}
                            description={t("integration.monitoring.item.dependenciesDesc")}
                        />
                        <SectionCard.Content>
                            {Array.isArray(item.dependencies) && item.dependencies.length > 0 ? (
                                <ul className="space-y-1">
                                    {item.dependencies.map((dep) => (
                                        <li
                                            key={dep.id}
                                            className="font-mono text-xs break-all border-b last:border-b-0 py-1"
                                        >
                                            {dep.objectId}
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <span className="text-sm text-muted-foreground">
                                    {t("integration.monitoring.item.noDependencies")}
                                </span>
                            )}
                        </SectionCard.Content>
                    </SectionCard>
                </div>
            )}
        </LoadProvider>
    );
}
