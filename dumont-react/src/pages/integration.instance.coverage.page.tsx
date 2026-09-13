import { AiSummaryPanel } from "@/components/ai-summary-panel";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { Badge } from "@/components/ui/badge";
import { AdaptivePanel } from "@/components/bento/adaptive-panel";
import { Progress } from "@/components/ui/progress";
import { AdaptiveSectionCard as SectionCard } from "@viglet/viglet-design-system/bento";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import type {
    TurIntegrationCoverageDrift,
    TurIntegrationCoverageSnapshot,
    TurIntegrationFieldDrift,
    TurIntegrationFieldLastSeen,
    TurIntegrationProvenanceLastSeen,
} from "@/models/integration/integration-coverage.model";
import { TurIntegrationCoverageService } from "@/services/integration/integration-coverage.service";
import {
    IconActivityHeartbeat,
    IconAlertTriangle,
    IconCircleCheck,
    IconClockExclamation,
    IconLoader2,
    IconRoute,
    IconSparkles,
    IconTrendingDown,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

function coverageBarColor(pct: number): string {
    if (pct >= 90) return "*:data-[slot=progress-indicator]:bg-emerald-500";
    if (pct >= 50) return "*:data-[slot=progress-indicator]:bg-amber-500";
    return "*:data-[slot=progress-indicator]:bg-red-500";
}

function deltaForField(drift: TurIntegrationCoverageDrift | undefined, field: string):
    TurIntegrationFieldDrift | undefined {
    return drift?.fields.find((f) => f.field === field);
}

export default function IntegrationInstanceCoveragePage() {
    const { t } = useTranslation();
    const { id } = useParams() as { id: string };

    const service = useMemo(() => (id ? new TurIntegrationCoverageService(id) : null), [id]);

    const [sources, setSources] = useState<string[]>([]);
    const [source, setSource] = useState<string>("");
    const [history, setHistory] = useState<TurIntegrationCoverageSnapshot[] | null>(null);
    const [drift, setDrift] = useState<TurIntegrationCoverageDrift | null>(null);
    const [provenance, setProvenance] = useState<TurIntegrationProvenanceLastSeen | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (!service) return;
        service.getCountBySource()
            .then((counts) => {
                const names = Object.keys(counts).sort((a, b) => a.localeCompare(b));
                setSources(names);
                setSource((current) => current || names[0] || "");
            })
            .catch(() => setSources([]));
    }, [service]);

    /*
     * D53 - the spinner is raised by whoever asks for a load, not by the loader.
     *
     * `setIsLoading(true)` here ran synchronously on the effect's call, which React 19 refuses.
     * Both callers still show it: the initial state is `true`, and the source picker below raises
     * it again when the reader switches source - which is the refetch a spinner is actually for.
     */
    const loadCoverage = useCallback(async () => {
        if (!service || !source) return;        try {
            const [hist, drf, prov] = await Promise.all([
                service.getHistory(source),
                service.getDrift(source),
                service.getProvenanceLastSeen(source),
            ]);
            setHistory(hist);
            setDrift(drf);
            setProvenance(prov);
        } catch {
            setHistory([]);
            setDrift(null);
            setProvenance(null);
        } finally {
            setIsLoading(false);
        }
    }, [service, source]);

    /*
     * D53 - the load is awaited inside the effect rather than called from its body.
     *
     * The rule refuses a synchronous `setState` in an effect, and cannot see across a
     * `useCallback` boundary - so any direct call to a loader that writes state reads as
     * synchronous. Awaiting it inside an async IIFE says what is actually true: the effect
     * schedules work and returns, and every write happens after a suspension point.
     */    useEffect(() => {
        void (async () => { await loadCoverage() })();
    }, [loadCoverage]);

    const latest = history?.[0];

    const lastSeenByField = useMemo(() => {
        const map = new Map<string, TurIntegrationFieldLastSeen>();
        provenance?.fields.forEach((f) => map.set(f.field, f));
        return map;
    }, [provenance]);

    return (
        <>
            <AdaptivePageHeader
                icon={IconActivityHeartbeat}
                feature={t("integration.coverage.title")}
                name={t("integration.coverage.title")}
                description={t("integration.coverage.description")}
            />
            <div className="px-6 py-4 space-y-4">
                <div className="flex items-center gap-2">
                    <span className="text-sm text-muted-foreground">{t("integration.coverage.source")}</span>
                    <Select
                        value={source}
                        onValueChange={(next) => {
                            setIsLoading(true);
                            setSource(next);
                        }}
                    >
                        <SelectTrigger className="w-72 h-9 font-mono text-sm">
                            <SelectValue placeholder={t("integration.coverage.selectSource")} />
                        </SelectTrigger>
                        <SelectContent>
                            {sources.map((name) => (
                                <SelectItem key={name} value={name} className="font-mono text-sm">
                                    {name}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>

                {isLoading && (
                    <div className="flex items-center justify-center py-20">
                        <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                    </div>
                )}

                {!isLoading && source && history && history.length === 0 && (
                    <AdaptivePanel contentClassName="p-6 text-center text-muted-foreground">
                        {t("integration.coverage.noHistory")}
                    </AdaptivePanel>
                )}

                {!isLoading && latest && (
                    <>
                        {/* Drift banner */}
                        {drift?.hasDrift ? (
                            <SectionCard variant="rose">
                                <SectionCard.StaticHeader
                                    icon={IconAlertTriangle}
                                    title={t("integration.coverage.driftDetected")}
                                    description={t("integration.coverage.driftDesc", {
                                        threshold: drift.dropThreshold,
                                    })}
                                />
                                <SectionCard.Content>
                                    <div className="flex flex-wrap gap-2 py-2">
                                        {drift.fields
                                            .filter((f) => f.drift)
                                            .map((f) => (
                                                <Badge
                                                    key={f.field}
                                                    variant="outline"
                                                    className="flex items-center gap-1.5 bg-red-500/10 text-red-600 border-red-500/20 font-mono text-xs"
                                                >
                                                    <IconTrendingDown className="h-3 w-3" />
                                                    {f.field}: {f.previousPercent}% → {f.currentPercent}%
                                                </Badge>
                                            ))}
                                    </div>
                                </SectionCard.Content>
                            </SectionCard>
                        ) : (
                            history.length >= 2 && (
                                <AdaptivePanel contentClassName="flex items-center gap-2 p-4 text-sm text-emerald-600">
                                    <IconCircleCheck className="h-4 w-4" />
                                    {t("integration.coverage.noDrift")}
                                </AdaptivePanel>
                            )
                        )}

                        {/* Latest-run coverage table */}
                        <SectionCard variant="blue">
                            <SectionCard.StaticHeader
                                icon={IconActivityHeartbeat}
                                title={t("integration.coverage.latestRun")}
                                description={t("integration.coverage.latestRunDesc", {
                                    documents: latest.documentCount,
                                    date: new Date(latest.capturedAt).toLocaleString(),
                                })}
                            />
                            <SectionCard.Content>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>{t("integration.coverage.field")}</TableHead>
                                            <TableHead>{t("integration.coverage.type")}</TableHead>
                                            <TableHead className="w-1/4">{t("integration.coverage.coverageCol")}</TableHead>
                                            <TableHead>{t("integration.coverage.change")}</TableHead>
                                            <TableHead>{t("integration.coverage.origin")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {latest.fields.map((field) => {
                                            const change = deltaForField(drift ?? undefined, field.field);
                                            const origin = lastSeenByField.get(field.field);
                                            return (
                                                <TableRow key={field.field}>
                                                    <TableCell className="py-3">
                                                        <span className="font-mono text-sm">{field.field}</span>
                                                        {field.mandatory && (
                                                            <Badge variant="outline" className="ml-2 text-[10px] px-1.5 py-0 bg-violet-500/10 text-violet-600 border-violet-500/20">
                                                                {t("integration.coverage.mandatory")}
                                                            </Badge>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="py-3 text-xs font-mono text-muted-foreground">
                                                        {field.type}
                                                    </TableCell>
                                                    <TableCell className="py-3">
                                                        <div className="flex items-center gap-2">
                                                            <Progress
                                                                value={field.percent}
                                                                className={`h-2 flex-1 ${coverageBarColor(field.percent)}`}
                                                            />
                                                            <span className="font-mono text-xs w-20 text-right text-muted-foreground">
                                                                {field.percent}% ({field.present}/{field.total})
                                                            </span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell className="py-3">
                                                        {change && change.delta !== 0 ? (
                                                            <span className={`font-mono text-xs font-semibold ${change.drift ? "text-red-600" : change.delta > 0 ? "text-emerald-600" : "text-amber-600"}`}>
                                                                {change.delta > 0 ? "+" : ""}{change.delta}%
                                                            </span>
                                                        ) : (
                                                            <span className="text-xs text-muted-foreground">—</span>
                                                        )}
                                                    </TableCell>
                                                    <TableCell className="py-3">
                                                        {origin && origin.strategy ? (
                                                            <div className="flex flex-col gap-1">
                                                                <span className="flex items-center gap-1.5 font-mono text-xs text-muted-foreground">
                                                                    <IconRoute className="h-3 w-3 shrink-0" />
                                                                    {origin.strategy}
                                                                    {origin.selector && (
                                                                        <span className="text-muted-foreground/70">· {origin.selector}</span>
                                                                    )}
                                                                </span>
                                                                {origin.presentInLatest === false && origin.lastSeenRunsAgo > 0 && (
                                                                    <Badge
                                                                        variant="outline"
                                                                        className="w-fit flex items-center gap-1 bg-amber-500/10 text-amber-600 border-amber-500/20 text-[10px] px-1.5 py-0"
                                                                    >
                                                                        <IconClockExclamation className="h-3 w-3" />
                                                                        {t("integration.coverage.lastSeenAgo", { count: origin.lastSeenRunsAgo })}
                                                                    </Badge>
                                                                )}
                                                            </div>
                                                        ) : (
                                                            <span className="text-xs text-muted-foreground">—</span>
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })}
                                    </TableBody>
                                </Table>
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Run history */}
                        {history.length > 1 && (
                            <SectionCard variant="slate">
                                <SectionCard.StaticHeader
                                    icon={IconActivityHeartbeat}
                                    title={t("integration.coverage.runHistory")}
                                    description={t("integration.coverage.runHistoryDesc", { count: history.length })}
                                />
                                <SectionCard.Content>
                                    <div className="space-y-1">
                                        {history.map((run, index) => (
                                            <div key={`${run.capturedAt}-${index}`} className="flex items-center justify-between py-1.5 border-b last:border-b-0 text-sm">
                                                <span className="font-mono text-muted-foreground">
                                                    {new Date(run.capturedAt).toLocaleString()}
                                                </span>
                                                <span className="font-mono">
                                                    {run.documentCount} {t("integration.coverage.docs")}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* LLM health summary */}
                        <SectionCard variant="violet">
                            <SectionCard.StaticHeader
                                icon={IconSparkles}
                                title={t("integration.coverage.healthSummary")}
                                description={t("integration.coverage.healthSummaryDesc")}
                            />
                            <SectionCard.Content>
                                {service && source && (
                                    <AiSummaryPanel
                                        key={source}
                                        endpoint={service.summaryEndpoint(source)}
                                        i18nPrefix="integration.coverage"
                                    />
                                )}
                            </SectionCard.Content>
                        </SectionCard>
                    </>
                )}
            </div>
        </>
    );
}
