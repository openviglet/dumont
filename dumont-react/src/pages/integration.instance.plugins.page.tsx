import { SubPageHeader } from "@/components/sub.page.header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { TurPlugin } from "@/models/system/plugin.model";
import { TurPluginService } from "@/services/system/plugin.service";
import {
    IconBan,
    IconCheck,
    IconPlayerPlay,
    IconPlayerStop,
    IconPuzzle,
    IconTrash,
    IconUpload,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useMemo, useRef, useState } from "react";
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

export default function IntegrationInstancePluginsPage() {
    const { id } = useParams() as { id: string };
    const service = useMemo(() => new TurPluginService(id), [id]);
    const { t } = useTranslation();
    const [plugins, setPlugins] = useState<TurPlugin[]>([]);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const refresh = async () => {
        try {
            setPlugins(await service.list());
        } catch {
            toast.error(t("integration.plugins.loadFailed"));
        }
    };

    useEffect(() => {
        refresh();
    }, [id]);

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

    return (
        <>
            <SubPageHeader
                icon={IconPuzzle}
                name={t("integration.plugins.title")}
                feature={t("integration.plugins.feature")}
                description={t("integration.plugins.description")}
            />
            <div className="space-y-6 p-4">
                <div className="rounded-lg border p-4 flex items-center justify-between">
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
                    />
                    <Button onClick={onPickFile} disabled={uploading}>
                        <IconUpload className="size-4" />
                        {uploading
                            ? t("integration.plugins.upload.uploading")
                            : t("integration.plugins.upload.button")}
                    </Button>
                </div>

                <div className="rounded-lg border">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>{t("integration.plugins.column.id")}</TableHead>
                                <TableHead>{t("integration.plugins.column.version")}</TableHead>
                                <TableHead>{t("integration.plugins.column.provider")}</TableHead>
                                <TableHead>{t("integration.plugins.column.description")}</TableHead>
                                <TableHead>{t("integration.plugins.column.state")}</TableHead>
                                <TableHead className="text-right">{t("integration.plugins.column.actions")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {plugins.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={6} className="text-center text-muted-foreground py-6">
                                        {t("integration.plugins.empty")}
                                    </TableCell>
                                </TableRow>
                            )}
                            {plugins.map((p) => (
                                <TableRow key={p.id}>
                                    <TableCell className="font-medium">{p.id}</TableCell>
                                    <TableCell>{p.version}</TableCell>
                                    <TableCell>{p.provider ?? "—"}</TableCell>
                                    <TableCell className="text-muted-foreground">{p.description ?? "—"}</TableCell>
                                    <TableCell>
                                        <Badge variant={stateBadgeVariant(p.state)}>{p.state}</Badge>
                                    </TableCell>
                                    <TableCell className="text-right">
                                        <div className="inline-flex gap-1">
                                            {p.state !== "STARTED" && p.state !== "DISABLED" && (
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
                                            {p.state !== "DISABLED" && (
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
                                                onClick={() => action("delete", p, () => service.delete(p.id))}
                                                title={t("integration.plugins.action.delete")}>
                                                <IconTrash className="size-4 text-destructive" />
                                            </Button>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </div>
            </div>
        </>
    );
}
