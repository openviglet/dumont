import { SubPageHeader } from "@/components/sub.page.header";
import { Input } from "@/components/ui/input";
import { SectionCard } from "@/components/ui/section-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
    IconCpu,
    IconDatabase,
    IconDeviceDesktop,
    IconDisc,
    IconInfoCircle,
    IconLoader2,
    IconSearch,
    IconServer,
    IconVariable,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

interface ConnectorMemory {
    maxMemory: number;
    totalMemory: number;
    usedMemory: number;
    freeMemory: number;
    totalPhysicalMemory: number;
    freePhysicalMemory: number;
}

interface ConnectorDisk {
    totalSpace: number;
    usableSpace: number;
    usedSpace: number;
}

interface PropertyEntry {
    value: string;
    property: string;
}

interface ConnectorIndexing {
    provider: PropertyEntry;
    turingUrl?: PropertyEntry;
    turingSolrEndpoint?: PropertyEntry;
    solrUrl?: PropertyEntry;
    solrCollection?: PropertyEntry;
    elasticsearchUrl?: PropertyEntry;
    elasticsearchIndex?: PropertyEntry;
    elasticsearchUsername?: PropertyEntry;
}

interface ConnectorSystemInfo {
    appVersion: string;
    appName: string;
    javaVersion: string;
    javaVendor: string;
    javaVmName: string;
    osName: string;
    osVersion: string;
    osArch: string;
    memory: ConnectorMemory;
    disk: ConnectorDisk;
    indexing: ConnectorIndexing;
    status: string;
}

function formatBytes(bytes: number): string {
    if (bytes < 0) return "N/A";
    if (bytes === 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`;
}

function usagePercent(used: number, total: number): number {
    if (total <= 0) return 0;
    return Math.round((used / total) * 100);
}

function ProgressBar({ value, color }: Readonly<{ value: number; color: string }>) {
    return (
        <div className="h-2 w-full rounded-full bg-muted">
            <div
                className={`h-2 rounded-full transition-all ${color}`}
                style={{ width: `${Math.min(value, 100)}%` }}
            />
        </div>
    );
}

function InfoRow({ label, value, property }: Readonly<{ label: string; value: string; property?: string }>) {
    return (
        <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <div className="flex flex-col">
                <span className="text-sm text-muted-foreground">{label}</span>
                {property && <span className="text-xs font-mono text-muted-foreground/60">{property}</span>}
            </div>
            <span className="text-sm font-medium font-mono">{value}</span>
        </div>
    );
}

function StatusBadge({ status, t }: Readonly<{ status: string; t: (key: string) => string }>) {
    const isUp = status === "UP";
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${isUp
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400"
            }`}>
            <span className={`size-1.5 rounded-full ${isUp ? "bg-emerald-500" : "bg-red-500"}`} />
            {isUp ? t("integration.connectorSystemInfo.up") : t("integration.connectorSystemInfo.down")}
        </span>
    );
}

export default function IntegrationInstanceSystemInfoPage() {
    const { t } = useTranslation();
    const { id } = useParams() as { id: string };

    const [info, setInfo] = useState<ConnectorSystemInfo | null>(null);
    const [variables, setVariables] = useState<Record<string, string>>({});
    const [isLoading, setIsLoading] = useState(true);
    const [status, setStatus] = useState<string>("DOWN");
    const [varSearch, setVarSearch] = useState("");

    useEffect(() => {
        if (id && id !== "new") {
            Promise.all([
                axios.get<ConnectorSystemInfo>(`/v2/integration/${id}/connector/system-info`),
                axios.get<Record<string, string>>(`/v2/integration/${id}/connector/system-info/variables`),
            ])
                .then(([infoRes, varsRes]) => {
                    setInfo(infoRes.data);
                    setStatus(infoRes.data.status ?? "UP");
                    setVariables(varsRes.data);
                })
                .catch(() => {
                    setStatus("DOWN");
                    toast.error("Failed to load connector system information.");
                })
                .finally(() => setIsLoading(false));
        } else {
            setIsLoading(false);
        }
    }, [id]);

    const filteredVariables = useMemo(() => {
        const search = varSearch.toLowerCase();
        return Object.entries(variables).filter(
            ([key, value]) =>
                key.toLowerCase().includes(search) ||
                value.toLowerCase().includes(search),
        );
    }, [variables, varSearch]);

    if (isLoading) {
        return (
            <>
                <SubPageHeader
                    icon={IconInfoCircle}
                    feature={t("integration.connectorSystemInfo.title")}
                    name={t("integration.connectorSystemInfo.title")}
                    description={t("integration.connectorSystemInfo.description")}
                />
                <div className="flex items-center justify-center py-20">
                    <IconLoader2 className="size-6 animate-spin text-muted-foreground" />
                </div>
            </>
        );
    }

    const mem = info?.memory;
    const disk = info?.disk;
    const heapPercent = mem ? usagePercent(mem.usedMemory, mem.maxMemory) : 0;
    const diskPercent = disk ? usagePercent(disk.usedSpace, disk.totalSpace) : 0;
    const physicalUsed = mem && mem.totalPhysicalMemory > 0
        ? mem.totalPhysicalMemory - mem.freePhysicalMemory
        : 0;
    const physicalPercent = mem && mem.totalPhysicalMemory > 0
        ? usagePercent(physicalUsed, mem.totalPhysicalMemory)
        : -1;

    return (
        <>
            <SubPageHeader
                icon={IconInfoCircle}
                feature="System Information"
                name="System Information"
                description="Connector version and runtime details."
            />
            <div className="py-6 px-6">
                <Tabs defaultValue="overview">
                    <TabsList>
                        <TabsTrigger value="overview">
                            <IconServer className="size-4 mr-1" />
                            {t("systemInfo.overview")}
                        </TabsTrigger>
                        <TabsTrigger value="variables">
                            <IconVariable className="size-4 mr-1" />
                            {t("systemInfo.systemVariables")}
                        </TabsTrigger>
                    </TabsList>

                    <TabsContent value="overview" className="space-y-4 mt-4">
                        {/* Application */}
                        <SectionCard variant="blue">
                            <SectionCard.StaticHeader
                                icon={IconDeviceDesktop}
                                title={t("integration.connectorSystemInfo.application")}
                                description={t("integration.connectorSystemInfo.applicationDesc")}
                            />
                            <SectionCard.Content>
                                <div className="flex items-center justify-between py-2 border-b">
                                    <span className="text-sm text-muted-foreground">{t("integration.connectorSystemInfo.status")}</span>
                                    <StatusBadge status={status} t={t} />
                                </div>
                                <InfoRow label={t("integration.connectorSystemInfo.name")} value={info?.appName ?? "Unknown"} />
                                <InfoRow label={t("integration.connectorSystemInfo.version")} value={info?.appVersion ?? "Unknown"} />
                                <InfoRow label={t("integration.connectorSystemInfo.javaVersion")} value={info?.javaVersion ?? "Unknown"} />
                                <InfoRow label={t("integration.connectorSystemInfo.javaVendor")} value={info?.javaVendor ?? "Unknown"} />
                                <InfoRow label={t("integration.connectorSystemInfo.jvm")} value={info?.javaVmName ?? "Unknown"} />
                                <InfoRow label={t("integration.connectorSystemInfo.os")} value={`${info?.osName ?? ""} ${info?.osVersion ?? ""} (${info?.osArch ?? ""})`} />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Indexing Provider */}
                        {info?.indexing && (
                            <SectionCard variant="violet">
                                <SectionCard.StaticHeader
                                    icon={IconDatabase}
                                    title={t("integration.connectorSystemInfo.indexingProvider")}
                                    description={t("integration.connectorSystemInfo.indexingProviderDesc")}
                                />
                                <SectionCard.Content>
                                    <InfoRow label={t("integration.connectorSystemInfo.provider")} value={info.indexing.provider?.value?.toUpperCase() ?? "N/A"} property={info.indexing.provider?.property} />
                                    {info.indexing.provider?.value === "turing" && (
                                        <>
                                            <InfoRow label={t("integration.connectorSystemInfo.turingUrl")} value={info.indexing.turingUrl?.value ?? "N/A"} property={info.indexing.turingUrl?.property} />
                                            <InfoRow label={t("integration.connectorSystemInfo.turingSolrEndpoint")} value={info.indexing.turingSolrEndpoint?.value ?? "N/A"} property={info.indexing.turingSolrEndpoint?.property} />
                                        </>
                                    )}
                                    {info.indexing.provider?.value === "solr" && (
                                        <>
                                            <InfoRow label={t("integration.connectorSystemInfo.solrUrl")} value={info.indexing.solrUrl?.value ?? "N/A"} property={info.indexing.solrUrl?.property} />
                                            <InfoRow label={t("integration.connectorSystemInfo.solrCollection")} value={info.indexing.solrCollection?.value ?? "N/A"} property={info.indexing.solrCollection?.property} />
                                        </>
                                    )}
                                    {info.indexing.provider?.value === "elasticsearch" && (
                                        <>
                                            <InfoRow label={t("integration.connectorSystemInfo.elasticsearchUrl")} value={info.indexing.elasticsearchUrl?.value ?? "N/A"} property={info.indexing.elasticsearchUrl?.property} />
                                            <InfoRow label={t("integration.connectorSystemInfo.elasticsearchIndex")} value={info.indexing.elasticsearchIndex?.value ?? "N/A"} property={info.indexing.elasticsearchIndex?.property} />
                                            <InfoRow label={t("integration.connectorSystemInfo.elasticsearchUsername")} value={info.indexing.elasticsearchUsername?.value ?? "N/A"} property={info.indexing.elasticsearchUsername?.property} />
                                        </>
                                    )}
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* Physical RAM */}
                        {physicalPercent >= 0 && (
                            <SectionCard variant="cyan">
                                <SectionCard.StaticHeader
                                    icon={IconServer}
                                    title={t("systemInfo.physicalMemory")}
                                    description={t("systemInfo.physicalMemoryDesc")}
                                />
                                <SectionCard.Content>
                                    <div className="space-y-1">
                                        <div className="flex justify-between text-sm">
                                            <span className="text-muted-foreground">{t("systemInfo.ramUsage")}</span>
                                            <span className="font-medium">{physicalPercent}%</span>
                                        </div>
                                        <ProgressBar value={physicalPercent} color="bg-cyan-500" />
                                        <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                            <span>{t("systemInfo.used")} {formatBytes(physicalUsed)}</span>
                                            <span>{t("systemInfo.totalLabel")} {formatBytes(mem?.totalPhysicalMemory ?? 0)}</span>
                                        </div>
                                    </div>
                                    <InfoRow label={t("systemInfo.totalRam")} value={formatBytes(mem?.totalPhysicalMemory ?? 0)} />
                                    <InfoRow label={t("systemInfo.freeRam")} value={formatBytes(mem?.freePhysicalMemory ?? 0)} />
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* JVM Heap Memory */}
                        <SectionCard variant="emerald">
                            <SectionCard.StaticHeader
                                icon={IconCpu}
                                title={t("systemInfo.jvmHeap")}
                                description={t("systemInfo.jvmHeapDesc")}
                            />
                            <SectionCard.Content>
                                <div className="space-y-1">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">{t("systemInfo.heapUsage")}</span>
                                        <span className="font-medium">{heapPercent}%</span>
                                    </div>
                                    <ProgressBar value={heapPercent} color="bg-emerald-500" />
                                    <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                        <span>{t("systemInfo.used")} {formatBytes(mem?.usedMemory ?? 0)}</span>
                                        <span>{t("systemInfo.max")} {formatBytes(mem?.maxMemory ?? 0)}</span>
                                    </div>
                                </div>
                                <InfoRow label={t("systemInfo.totalAllocated")} value={formatBytes(mem?.totalMemory ?? 0)} />
                                <InfoRow label={t("systemInfo.freeAllocated")} value={formatBytes(mem?.freeMemory ?? 0)} />
                                <InfoRow label={t("systemInfo.maxHeap")} value={formatBytes(mem?.maxMemory ?? 0)} />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Disk */}
                        <SectionCard variant="amber">
                            <SectionCard.StaticHeader
                                icon={IconDisc}
                                title={t("systemInfo.diskSpace")}
                                description={t("systemInfo.diskSpaceDesc")}
                            />
                            <SectionCard.Content>
                                <div className="space-y-1">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-muted-foreground">{t("systemInfo.diskUsage")}</span>
                                        <span className="font-medium">{diskPercent}%</span>
                                    </div>
                                    <ProgressBar value={diskPercent} color="bg-amber-500" />
                                    <div className="flex justify-between text-xs text-muted-foreground pt-1">
                                        <span>{t("systemInfo.used")} {formatBytes(disk?.usedSpace ?? 0)}</span>
                                        <span>{t("systemInfo.totalLabel")} {formatBytes(disk?.totalSpace ?? 0)}</span>
                                    </div>
                                </div>
                                <InfoRow label={t("systemInfo.totalLabel")} value={formatBytes(disk?.totalSpace ?? 0)} />
                                <InfoRow label={t("systemInfo.available")} value={formatBytes(disk?.usableSpace ?? 0)} />
                                <InfoRow label={t("systemInfo.usedMemory")} value={formatBytes(disk?.usedSpace ?? 0)} />
                            </SectionCard.Content>
                        </SectionCard>
                    </TabsContent>

                    <TabsContent value="variables" className="mt-4">
                        <SectionCard variant="slate">
                            <SectionCard.StaticHeader
                                icon={IconVariable}
                                title={t("systemInfo.systemVariables")}
                                description={t("systemInfo.sysVarsDesc")}
                            />
                            <SectionCard.Content>
                                <div className="relative">
                                    <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
                                    <Input
                                        className="pl-9 max-w-sm"
                                        placeholder={t("systemInfo.searchVariables")}
                                        value={varSearch}
                                        onChange={(e) => setVarSearch(e.target.value)}
                                    />
                                </div>
                                <div className="rounded-lg border overflow-hidden">
                                    <div className="max-h-150 overflow-y-auto">
                                        <table className="w-full text-sm">
                                            <thead className="bg-muted/60 sticky top-0">
                                                <tr>
                                                    <th className="text-left px-4 py-2 font-medium text-muted-foreground w-1/3">{t("systemInfo.property")}</th>
                                                    <th className="text-left px-4 py-2 font-medium text-muted-foreground">{t("systemInfo.value")}</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {filteredVariables.map(([key, value]) => (
                                                    <tr key={key} className="border-t hover:bg-muted/30 transition-colors">
                                                        <td className="px-4 py-2 font-mono text-xs break-all">{key}</td>
                                                        <td className="px-4 py-2 font-mono text-xs break-all text-muted-foreground">{value}</td>
                                                    </tr>
                                                ))}
                                                {filteredVariables.length === 0 && (
                                                    <tr>
                                                        <td colSpan={2} className="px-4 py-8 text-center text-muted-foreground">
                                                            {t("systemInfo.noVariablesMatch")}
                                                        </td>
                                                    </tr>
                                                )}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                                <p className="text-xs text-muted-foreground">
                                    {t("systemInfo.propertiesShown", { count: filteredVariables.length, total: Object.keys(variables).length })}
                                </p>
                            </SectionCard.Content>
                        </SectionCard>
                    </TabsContent>
                </Tabs>
            </div>
        </>
    );
}
