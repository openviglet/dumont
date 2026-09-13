import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { AdaptivePanel } from "@/components/bento/adaptive-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDateLocale } from "@/hooks/use-date-locale";
import type { TurPlugin } from "@/models/system/plugin.model";
import { TurPluginService } from "@/services/system/plugin.service";
import {
    IconAlertTriangle,
    IconBan,
    IconCheck,
    IconPlayerPlay,
    IconPlayerStop,
    IconPuzzle,
    IconTrash,
    IconUpload,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { type Locale, formatDistanceToNow } from "date-fns";
import { useEffect, useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

function stateBadgeVariant(state: string): BadgeVariant {
    switch (state) {
        case "STARTED": return "default";
        case "FAILED": return "destructive";
        case "DISABLED": return "secondary";
        default: return "outline";
    }
}

function renderLastModified(iso: string | null, locale: Locale) {
    if (!iso) return <span>—</span>;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return <span>—</span>;
    const formatted = d.toLocaleString(undefined, {
        day: "2-digit",
        month: "2-digit",
        year: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    });
    const timeAgo = formatDistanceToNow(d, { addSuffix: true, locale });
    return (
        <div className="flex flex-col">
            <span className="font-mono text-sm">{formatted}</span>
            <span className="text-xs text-muted-foreground italic">({timeAgo})</span>
        </div>
    );
}

export default function IntegrationInstancePluginsPage() {
    const { id } = useParams() as { id: string };
    const service = useMemo(() => new TurPluginService(id), [id]);
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const [plugins, setPlugins] = useState<TurPlugin[]>([]);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    /*
     * D56 - stable, so the effect below can list it.
     *
     * It was a plain function rebuilt on every render, which is why the effect depended on `id`
     * and pretended not to call it: naming an unstable function in a dependency array re-runs the
     * effect forever. `useCallback` makes the dependency true instead of impossible.
     */
    const refresh = useCallback(async () => {
        try {
            setPlugins(await service.list());
        } catch {
            toast.error(t("integration.plugins.loadFailed"));
        }
    }, [service, t]);

    /*
     * D53 - the load is awaited inside the effect rather than called from its body.
     *
     * The rule refuses a synchronous `setState` in an effect, and cannot see across a
     * `useCallback` boundary - so any direct call to a loader that writes state reads as
     * synchronous. Awaiting it inside an async IIFE says what is actually true: the effect
     * schedules work and returns, and every write happens after a suspension point.
     */
    useEffect(() => {
        void (async () => { await refresh() })();
    }, [refresh]);

    const onPickFile = () => fileInputRef.current?.click();

    const onUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setUploading(true);
        try {
            const result = await service.upload(file);
            toast.success(t("integration.plugins.uploadSuccess", { id: result.id }));
            await refresh();
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { detail?: string } }; message?: string })
                ?.response?.data?.detail
                ?? (err as { message?: string })?.message
                ?? "";
            toast.error(t("integration.plugins.uploadFailed", { error: msg }));
        } finally {
            setUploading(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    const action = async (
        verb: "start" | "stop" | "disable" | "enable" | "delete",
        plugin: TurPlugin,
        fn: () => Promise<unknown>
    ) => {
        try {
            await fn();
            toast.success(t(`integration.plugins.${verb}Success`, { id: plugin.id }));
            await refresh();
        } catch {
            toast.error(t(`integration.plugins.${verb}Failed`, { id: plugin.id }));
        }
    };

    const onDelete = (plugin: TurPlugin) => {
        if (plugin.state === "FAILED" && plugin.filename) {
            return action("delete", plugin, () => service.deleteFailed(plugin.filename!));
        }
        return action("delete", plugin, () => service.delete(plugin.id));
    };

    return (
        <>
            <AdaptivePageHeader
                icon={IconPuzzle}
                name={t("integration.plugins.title")}
                feature={t("integration.plugins.feature")}
                description={t("integration.plugins.description")}
            />
            <div className="space-y-6 p-4">
                <AdaptivePanel contentClassName="p-4 flex items-center justify-between">
                    <div>
                        <h3 className="text-sm font-semibold">{t("integration.plugins.upload.title")}</h3>
                        <p className="text-xs text-muted-foreground">
                            {t("integration.plugins.upload.hint")}
                        </p>
                    </div>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".jar"
                        className="hidden"
                        onChange={onUpload}
                        aria-label={t("integration.plugins.upload.button")}
                    />
                    <Button onClick={onPickFile} disabled={uploading}>
                        <IconUpload className="size-4" />
                        {uploading
                            ? t("integration.plugins.upload.uploading")
                            : t("integration.plugins.upload.button")}
                    </Button>
                </AdaptivePanel>

                <AdaptivePanel className="overflow-hidden" contentClassName="p-0">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>{t("integration.plugins.column.id")}</TableHead>
                                <TableHead>{t("integration.plugins.column.version")}</TableHead>
                                <TableHead>{t("integration.plugins.column.provider")}</TableHead>
                                <TableHead>{t("integration.plugins.column.description")}</TableHead>
                                <TableHead>{t("integration.plugins.column.state")}</TableHead>
                                <TableHead>{t("integration.plugins.column.lastModified")}</TableHead>
                                <TableHead className="text-right">{t("integration.plugins.column.actions")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {plugins.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={7} className="text-center text-muted-foreground py-6">
                                        {t("integration.plugins.empty")}
                                    </TableCell>
                                </TableRow>
                            )}
                            {plugins.map((p) => {
                                const isFailed = p.state === "FAILED";
                                const rowKey = p.filename ?? p.id;
                                return (
                                <TableRow key={rowKey}>
                                    <TableCell className="font-medium">
                                        <div className="flex flex-col gap-1">
                                            <span>{p.id}</span>
                                            {isFailed && (
                                                <span className="text-xs text-muted-foreground font-mono">
                                                    {p.filename}
                                                </span>
                                            )}
                                            {isFailed && p.error && (
                                                <span className="flex items-start gap-1 text-xs text-destructive">
                                                    <IconAlertTriangle className="size-3.5 shrink-0 mt-0.5" />
                                                    <span className="break-all">{p.error}</span>
                                                </span>
                                            )}
                                        </div>
                                    </TableCell>
                                    <TableCell>{p.version ?? "—"}</TableCell>
                                    <TableCell>{p.provider ?? "—"}</TableCell>
                                    <TableCell className="text-muted-foreground">{p.description ?? "—"}</TableCell>
                                    <TableCell>
                                        <Badge variant={stateBadgeVariant(p.state)}>{p.state}</Badge>
                                    </TableCell>
                                    <TableCell className="whitespace-nowrap">
                                        {renderLastModified(p.lastModified, dateLocale)}
                                    </TableCell>
                                    <TableCell className="text-right">
                                        <div className="inline-flex gap-1">
                                            {!isFailed && p.state !== "STARTED" && p.state !== "DISABLED" && (
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    onClick={() => action("start", p, () => service.start(p.id))}
                                                    title={t("integration.plugins.action.start")}>
                                                    <IconPlayerPlay className="size-4" />
                                                </Button>
                                            )}
                                            {p.state === "STARTED" && (
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    onClick={() => action("stop", p, () => service.stop(p.id))}
                                                    title={t("integration.plugins.action.stop")}>
                                                    <IconPlayerStop className="size-4" />
                                                </Button>
                                            )}
                                            {!isFailed && p.state !== "DISABLED" && (
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    onClick={() => action("disable", p, () => service.disable(p.id))}
                                                    title={t("integration.plugins.action.disable")}>
                                                    <IconBan className="size-4" />
                                                </Button>
                                            )}
                                            {p.state === "DISABLED" && (
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    onClick={() => action("enable", p, () => service.enable(p.id))}
                                                    title={t("integration.plugins.action.enable")}>
                                                    <IconCheck className="size-4" />
                                                </Button>
                                            )}
                                            <Button
                                                size="sm"
                                                variant="ghost"
                                                onClick={() => onDelete(p)}
                                                title={t("integration.plugins.action.delete")}>
                                                <IconTrash className="size-4 text-destructive" />
                                            </Button>
                                        </div>
                                    </TableCell>
                                </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </AdaptivePanel>
            </div>
        </>
    );
}
