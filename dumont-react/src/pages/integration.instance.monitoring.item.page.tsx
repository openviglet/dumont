import { ROUTES } from "@/app/routes.const";
import { BadgeAemEnv } from "@/components/badge-aem-env";
import { BadgeIndexingStatus } from "@/components/badge-indexing-status";
import { BadgeLocale } from "@/components/badge-locale";
import { BadgeSites } from "@/components/badge-sites";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { GradientButton } from "@/components/ui/gradient-button";
import { SectionCard } from "@/components/ui/section-card";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurIntegrationIndexingDetail } from "@/models/integration/integration-indexing-detail.model";
import type {
    TurIntegrationLoggingEntry,
    TurIntegrationLoggingStatus,
} from "@/models/integration/integration-logging-history.model";
import { useDateLocale } from "@/hooks/use-date-locale";
import { truncateMiddle } from "@/lib/utils";
import { TurIntegrationLoggingService } from "@/services/integration/integration-logging.service";
import { TurIntegrationMonitoringService } from "@/services/integration/integration-monitoring.service";
import {
    IconArrowLeft,
    IconFileSearch,
    IconGitBranch,
    IconHistory,
    IconInfoCircle,
    IconLink,
    IconSearch,
} from "@tabler/icons-react";
import { type Locale, formatDistanceToNow } from "date-fns";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100, 200];
const DEFAULT_PAGE_SIZE = 50;

const TAB_DETAIL = "detail";
const TAB_HISTORY = "history";

function formatDateTime(value?: string): string {
    if (!value) return "-";
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return "-";
    return d.toLocaleString(undefined, {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    });
}

function HistoryDateCell({
    value,
    dateLocale,
}: Readonly<{ value?: string; dateLocale: Locale }>) {
    if (!value) {
        return <span className="font-mono text-xs">-</span>;
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return <span className="font-mono text-xs">-</span>;
    }
    const formatted = date.toLocaleString(undefined, {
        day: "2-digit",
        month: "2-digit",
        year: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    });
    const timeAgo = formatDistanceToNow(date, { addSuffix: true, locale: dateLocale });
    return (
        <div className="flex flex-col">
            <span className="font-mono text-xs whitespace-nowrap">{formatted}</span>
            <span className="text-[10px] text-muted-foreground italic">({timeAgo})</span>
        </div>
    );
}

function HistoryBody({
    loading,
    history,
    dateLocale,
    t,
}: Readonly<{
    loading: boolean;
    history: TurIntegrationLoggingEntry[];
    dateLocale: Locale;
    t: (key: string, opts?: Record<string, unknown>) => string;
}>) {
    if (loading) {
        return (
            <span className="text-sm text-muted-foreground">
                {t("integration.monitoring.item.historyLoading")}
            </span>
        );
    }
    if (history.length === 0) {
        return (
            <span className="text-sm text-muted-foreground">
                {t("integration.monitoring.item.noHistory")}
            </span>
        );
    }
    return (
        <div className="rounded-lg border overflow-hidden">
            <table className="w-full text-sm">
                <thead className="bg-muted/60">
                    <tr>
                        <th className="text-left px-3 py-2 font-medium text-muted-foreground">
                            {t("integration.monitoring.item.historyDate")}
                        </th>
                        <th className="text-left px-3 py-2 font-medium text-muted-foreground">
                            {t("integration.monitoring.item.status")}
                        </th>
                        <th className="text-left px-3 py-2 font-medium text-muted-foreground">
                            {t("integration.monitoring.item.historyResult")}
                        </th>
                        <th className="text-left px-3 py-2 font-medium text-muted-foreground">
                            {t("integration.monitoring.item.environment")}
                        </th>
                        <th className="text-left px-3 py-2 font-medium text-muted-foreground">
                            {t("integration.monitoring.item.historyDetails")}
                        </th>
                    </tr>
                </thead>
                <tbody>
                    {history.map((entry, idx) => (
                        <tr
                            key={`${entry.date ?? idx}-${idx}`}
                            className="border-t hover:bg-muted/30 transition-colors"
                        >
                            <td className="px-3 py-2">
                                <HistoryDateCell value={entry.date} dateLocale={dateLocale} />
                            </td>
                            <td className="px-3 py-2">
                                {entry.status ? (
                                    <BadgeIndexingStatus status={entry.status} />
                                ) : (
                                    "-"
                                )}
                            </td>
                            <td className="px-3 py-2 text-xs">
                                {entry.resultStatus ?? "-"}
                            </td>
                            <td className="px-3 py-2 text-xs">
                                {entry.environment ?? "-"}
                            </td>
                            <td className="px-3 py-2 text-xs text-muted-foreground break-all">
                                {entry.details ?? ""}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
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
    const dateLocale = useDateLocale();
    const { id, source, itemId, tab } = useParams<{
        id: string;
        source?: string;
        itemId: string;
        tab?: string;
    }>();

    const activeTab = tab === TAB_HISTORY ? TAB_HISTORY : TAB_DETAIL;

    const [item, setItem] = useState<TurIntegrationIndexingDetail>();
    const [error, setError] = useState<string | null>(null);
    const [loggingStatus, setLoggingStatus] = useState<TurIntegrationLoggingStatus>();
    const [history, setHistory] = useState<TurIntegrationLoggingEntry[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [historyPage, setHistoryPage] = useState(0);
    const [historyPageSize, setHistoryPageSize] = useState(DEFAULT_PAGE_SIZE);
    const [historyTotalElements, setHistoryTotalElements] = useState(0);
    const [historyTotalPages, setHistoryTotalPages] = useState(0);
    // When the user enters the History tab, the next fetch should jump to the
    // last page so the most-recent entries (sorted asc, newest at the end) are
    // visible by default. Subsequent navigation (prev/next, page-size) is left
    // alone.
    const jumpToLastPageRef = useRef(false);

    const monitoringService = useMemo(
        () => (id ? new TurIntegrationMonitoringService(id) : null),
        [id],
    );

    const loggingService = useMemo(
        () => (id ? new TurIntegrationLoggingService(id) : null),
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

    useEffect(() => {
        let cancelled = false;
        if (!loggingService) return;
        loggingService
            .status()
            .then((s) => {
                if (!cancelled) setLoggingStatus(s);
            })
            .catch((err) => console.error("logging status failed", err));
        return () => {
            cancelled = true;
        };
    }, [loggingService]);

    useEffect(() => {
        let cancelled = false;
        if (!loggingService || !item?.objectId) return;
        if (!loggingStatus?.enabled) return;
        if (activeTab !== TAB_HISTORY) return;
        setHistoryLoading(true);
        loggingService
            .indexingHistory({
                contentId: item.objectId,
                page: historyPage,
                pageSize: historyPageSize,
                sort: "asc",
            })
            .then((res) => {
                if (cancelled) return;
                const totalPages = res.totalPages ?? 0;
                // First fetch after the tab opens: if there's more than one
                // page, redirect to the last page (most recent entries).
                if (jumpToLastPageRef.current && totalPages > 1 && historyPage !== totalPages - 1) {
                    jumpToLastPageRef.current = false;
                    setHistoryPage(totalPages - 1);
                    return;
                }
                jumpToLastPageRef.current = false;
                setHistory(res.content ?? []);
                setHistoryTotalElements(res.totalElements ?? 0);
                setHistoryTotalPages(totalPages);
            })
            .catch((err) => {
                console.error("logging history failed", err);
                if (!cancelled) {
                    setHistory([]);
                    setHistoryTotalElements(0);
                    setHistoryTotalPages(0);
                    jumpToLastPageRef.current = false;
                }
            })
            .finally(() => {
                if (!cancelled) setHistoryLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [
        loggingService,
        item?.objectId,
        loggingStatus?.enabled,
        activeTab,
        historyPage,
        historyPageSize,
    ]);

    const monitoringListUrl = `${ROUTES.INTEGRATION_INSTANCE}/${id}/monitoring/${source ?? "all"}`;
    const itemBaseUrl = `${monitoringListUrl}/${itemId}`;

    const handleTabChange = useCallback(
        (next: string) => {
            const target = next === TAB_HISTORY ? `${itemBaseUrl}/${TAB_HISTORY}` : itemBaseUrl;
            navigate(target);
        },
        [itemBaseUrl, navigate],
    );

    // Auto-jump to last page whenever the History tab becomes active. Covers
    // both the tab-click path (state transition) and direct URL navigation
    // (`/.../history`) on initial mount.
    useEffect(() => {
        if (activeTab === TAB_HISTORY) {
            jumpToLastPageRef.current = true;
            setHistoryPage(0);
        }
    }, [activeTab]);

    const handlePageSizeChange = useCallback((value: string) => {
        setHistoryPageSize(Number(value));
        setHistoryPage(0);
    }, []);

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
                feature={item?.objectId
                    ? truncateMiddle(item.objectId, 80)
                    : t("integration.monitoring.item.title")}
                description={item
                    ? [
                          t("integration.monitoring.title"),
                          t("integration.monitoring.item.provider") + ": " + (item.provider ?? "-"),
                          t("integration.monitoring.item.source") + ": " + (item.source ?? "-"),
                      ].join(" · ")
                    : t("integration.monitoring.item.description")}
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

                    <Tabs value={activeTab} onValueChange={handleTabChange}>
                        <TabsList>
                            <TabsTrigger value={TAB_DETAIL}>
                                <IconInfoCircle className="size-4 mr-1" />
                                {t("integration.monitoring.item.tabDetail")}
                            </TabsTrigger>
                            {loggingStatus?.enabled && (
                                <TabsTrigger value={TAB_HISTORY}>
                                    <IconHistory className="size-4 mr-1" />
                                    {t("integration.monitoring.item.tabHistory")}
                                </TabsTrigger>
                            )}
                        </TabsList>

                        <TabsContent value={TAB_DETAIL} className="mt-4 space-y-4">
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
                        </TabsContent>

                        {loggingStatus?.enabled && (
                            <TabsContent value={TAB_HISTORY} className="mt-4 space-y-4">
                                <SectionCard variant="amber">
                                    <SectionCard.StaticHeader
                                        icon={IconHistory}
                                        title={t("integration.monitoring.item.history")}
                                        description={t("integration.monitoring.item.historyDesc", {
                                            engine: loggingStatus.engine,
                                        })}
                                    />
                                    <SectionCard.Content>
                                        <HistoryBody
                                            loading={historyLoading}
                                            history={history}
                                            dateLocale={dateLocale}
                                            t={t}
                                        />

                                        <div className="flex flex-wrap items-center justify-end gap-3 pt-3 border-t mt-3 text-sm">
                                            <span className="text-xs text-muted-foreground">
                                                {t("forms.common.totalRecords", {
                                                    count: historyTotalElements.toLocaleString(),
                                                })}
                                            </span>
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs">
                                                    {t("forms.common.rowsPerPage")}
                                                </span>
                                                <Select
                                                    value={String(historyPageSize)}
                                                    onValueChange={handlePageSizeChange}
                                                >
                                                    <SelectTrigger className="h-8 w-20 text-xs">
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent side="top">
                                                        {PAGE_SIZE_OPTIONS.map((size) => (
                                                            <SelectItem
                                                                key={size}
                                                                value={String(size)}
                                                                className="text-xs"
                                                            >
                                                                {size}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </div>
                                            <div className="flex w-32 items-center justify-center text-xs font-medium">
                                                {t("forms.common.pageOfPages", {
                                                    page: historyPage + 1,
                                                    totalPages: historyTotalPages || 1,
                                                })}
                                            </div>
                                            <div className="flex items-center gap-1">
                                                <GradientButton
                                                    variant="outline"
                                                    size="sm"
                                                    className="h-8 w-8 p-0"
                                                    onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                                                    disabled={historyPage <= 0 || historyLoading}
                                                >
                                                    <span>&lt;</span>
                                                </GradientButton>
                                                <GradientButton
                                                    variant="outline"
                                                    size="sm"
                                                    className="h-8 w-8 p-0"
                                                    onClick={() => setHistoryPage((p) => p + 1)}
                                                    disabled={
                                                        historyLoading ||
                                                        historyPage >= historyTotalPages - 1 ||
                                                        historyTotalPages === 0
                                                    }
                                                >
                                                    <span>&gt;</span>
                                                </GradientButton>
                                            </div>
                                        </div>
                                    </SectionCard.Content>
                                </SectionCard>
                            </TabsContent>
                        )}
                    </Tabs>
                </div>
            )}
        </LoadProvider>
    );
}
