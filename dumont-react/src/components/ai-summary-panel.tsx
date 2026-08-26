import { AdaptivePanel } from "@/components/bento/adaptive-panel";
import { GradientButton } from "@/components/ui/gradient-button";
import { Skeleton } from "@/components/ui/skeleton";
import { IconLoader2, IconRefresh } from "@tabler/icons-react";
import axios from "axios";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";

export interface SummaryResponse {
    success: boolean;
    error: string | null;
    content: string | null;
    canRegenerate: boolean;
}

interface AiSummaryPanelProps {
    endpoint: string;
    i18nPrefix: string;
}

export function AiSummaryPanel({ endpoint, i18nPrefix }: Readonly<AiSummaryPanelProps>) {
    const { t } = useTranslation();
    const [content, setContent] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [canRegenerate, setCanRegenerate] = useState(false);

    /*
     * D53 â€” the reset moved to the one caller that needs it.
     *
     * This loader is called two ways: once by the effect below on mount, and again by the
     * regenerate button. Clearing the panel first only matters for the second â€” on mount there is
     * nothing to clear, and `isLoading` already starts `true`. Doing it here made every call
     * synchronous, and a synchronous write inside an effect body is what React 19 refuses.
     *
     * Nothing before the first `await`, so the effect's call schedules work and returns.
     */
    const fetchSummary = useCallback(async (regenerate = false) => {
        try {
            const response = await axios.get<SummaryResponse>(
                endpoint,
                { params: regenerate ? { regenerate: true } : {} }
            );

            if (response.data.success && response.data.content) {
                setContent(response.data.content);
            } else {
                setError(response.data.error ?? t(`${i18nPrefix}.failedGenerate`));
            }
            setCanRegenerate(response.data.canRegenerate ?? false);
        } catch {
            setError(t(`${i18nPrefix}.failedConnect`));
        } finally {
            setIsLoading(false);
        }
    }, [endpoint, i18nPrefix, t]);

    /*
     * D53 - the load is awaited inside the effect rather than called from its body.
     *
     * The rule refuses a synchronous `setState` in an effect, and cannot see across a
     * `useCallback` boundary - so any direct call to a loader that writes state reads as
     * synchronous. Awaiting it inside an async IIFE says what is actually true: the effect
     * schedules work and returns, and every write happens after a suspension point.
     */    useEffect(() => {
        void (async () => { await fetchSummary() })();
    }, [fetchSummary]);

    return (
        <div className="px-6 py-6 space-y-4">
            {isLoading && (
                <AdaptivePanel contentClassName="p-6 space-y-4">
                    <div className="flex items-center gap-2 text-muted-foreground">
                        <IconLoader2 className="size-4 animate-spin" />
                        <span className="text-sm">{t(`${i18nPrefix}.generating`)}</span>
                    </div>
                    <Skeleton className="h-4 w-3/4" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-5/6" />
                    <Skeleton className="h-4 w-2/3" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-4/5" />
                </AdaptivePanel>
            )}

            {error && !isLoading && (
                <AdaptivePanel contentClassName="p-6">
                    <p className="text-sm text-destructive">{error}</p>
                </AdaptivePanel>
            )}

            {content && !isLoading && (
                <AdaptivePanel contentClassName="p-6">
                    <div className="prose prose-sm dark:prose-invert max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
                            {content}
                        </ReactMarkdown>
                    </div>
                </AdaptivePanel>
            )}

            {!isLoading && canRegenerate && (
                <div className="flex justify-end">
                    <GradientButton
                        type="button"
                        variant="outline"
                        onClick={() => {
                                // The reset the loader used to do for both callers (D53).
                                setIsLoading(true);
                                setError(null);
                                setContent(null);
                                fetchSummary(true);
                            }}
                    >
                        <IconRefresh className="size-4" />
                        {t(`${i18nPrefix}.regenerate`)}
                    </GradientButton>
                </div>
            )}
        </div>
    );
}
