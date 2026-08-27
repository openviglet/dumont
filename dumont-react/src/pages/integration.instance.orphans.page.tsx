import { ROUTES } from "@/app/routes.const";
import { AdaptivePageHeader } from "@/components/bento/adaptive-page-header";
import { AdaptivePanel } from "@/components/bento/adaptive-panel";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { GradientButton } from "@/components/ui/gradient-button";
import type {
  TurIntegrationOrphanReport,
  TurIntegrationOrphanReportStatus,
} from "@/models/integration/integration-orphan-report.model";
import { TurIntegrationOrphanReportService } from "@/services/integration/integration-orphan-report.service";
import { IconGhost2, IconTrash, IconX } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * The review the index audit's orphans wait in (D70).
 *
 * D68 made deletion a deliberate act with somewhere to happen; this is where it happens. Nothing
 * on this page acts on more than one report: a report is per source and per site because that is
 * the unit an operator can reason about, and a bulk apply across reports would be a decision about
 * evidence nobody read.
 *
 * An expired report is rendered like any other, greyed and with its apply disabled. Hiding it
 * would tidy away the one thing worth seeing — that the audit found something and nobody looked.
 */
export default function IntegrationInstanceOrphansPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();

  const [reports, setReports] = useState<TurIntegrationOrphanReport[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** The report a call is in flight for, so only its own buttons disable. */
  const [busy, setBusy] = useState<number | null>(null);
  const [confirming, setConfirming] = useState<number | null>(null);

  const service = useMemo(
    () => (id ? new TurIntegrationOrphanReportService(id) : null),
    [id],
  );

  const load = useCallback(async () => {
    if (!service) return;
    try {
      setReports(await service.query());
    } catch {
      setError(t("integration.orphans.loadFailed"));
    }
  }, [service, t]);

  useEffect(() => {
    // Nothing here is written synchronously — every setState in `load` is behind an await, and
    // the IIFE is what lets the rule see that rather than reading through the `useCallback`.
    void (async () => {
      await load();
    })();
  }, [load]);

  /**
   * A refusal is information, not a failure to hide: the connector re-checks expiry and status on
   * every apply, so a report the page believed applicable can still come back 410 or 409. Reload
   * either way — whatever the answer, the list on screen is now out of date.
   */
  const decide = useCallback(
    async (report: TurIntegrationOrphanReport, discard: boolean) => {
      if (!service) return;
      setConfirming(null);
      setBusy(report.id);
      try {
        const result = discard
          ? await service.discard(report.id)
          : await service.apply(report.id);
        toast.success(
          discard
            ? t("integration.orphans.discarded")
            : t("integration.orphans.applied", { count: result.deindexed }),
        );
      } catch (caught) {
        toast.error(t(refusalKey(caught)));
      } finally {
        setBusy(null);
        await load();
      }
    },
    [service, t, load],
  );

  if (!id) {
    return <div>{t("integration.indexingStats.invalidId")}</div>;
  }

  return (
    <LoadProvider
      checkIsNotUndefined={reports ?? undefined}
      error={error}
      tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/orphans`}
    >
      <AdaptivePageHeader
        icon={IconGhost2}
        name={t("integration.orphans.title")}
        feature={t("integration.orphans.title")}
        description={t("integration.orphans.description")}
      />
      <div className="px-6 py-4 space-y-4">
        {reports?.length === 0 ? (
          <AdaptivePanel contentClassName="p-6">
            <p className="text-sm text-muted-foreground">{t("integration.orphans.empty")}</p>
          </AdaptivePanel>
        ) : (
          <Accordion type="multiple">
            {reports?.map((report) => (
              <OrphanReportItem
                key={report.id}
                report={report}
                busy={busy === report.id}
                confirming={confirming === report.id}
                setConfirming={(next) => {
                  const open = typeof next === "function" ? next(confirming === report.id) : next;
                  setConfirming(open ? report.id : null);
                }}
                onApply={() => void decide(report, false)}
                onDiscard={() => void decide(report, true)}
              />
            ))}
          </Accordion>
        )}
      </div>
    </LoadProvider>
  );
}

/** Which refusal the connector gave, as something a reader can act on. */
function refusalKey(caught: unknown): string {
  if (!axios.isAxiosError(caught)) return "integration.orphans.decideFailed";
  switch (caught.response?.status) {
    case 410:
      return "integration.orphans.refusedExpired";
    case 409:
      return "integration.orphans.refusedDecided";
    case 404:
      return "integration.orphans.refusedSuperseded";
    default:
      return "integration.orphans.decideFailed";
  }
}

const STATUS_VARIANT: Record<TurIntegrationOrphanReportStatus, "secondary" | "outline"> = {
  PENDING: "secondary",
  APPLIED: "outline",
  DISCARDED: "outline",
};

function OrphanReportItem({
  report,
  busy,
  confirming,
  setConfirming,
  onApply,
  onDiscard,
}: Readonly<{
  report: TurIntegrationOrphanReport;
  busy: boolean;
  confirming: boolean;
  setConfirming: Dispatch<SetStateAction<boolean>>;
  onApply: () => void;
  onDiscard: () => void;
}>) {
  const { t } = useTranslation();
  const open = report.status === "PENDING";
  const applicable = open && !report.expired;

  return (
    <AccordionItem value={String(report.id)}>
      <AccordionTrigger className="text-sm gap-2">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{report.source}</span>
          <span className="text-muted-foreground">/</span>
          <span className="font-mono text-xs">{report.site}</span>
          <Badge variant={STATUS_VARIANT[report.status]}>
            {t(`integration.orphans.status.${report.status}`)}
          </Badge>
          {report.expired && open && (
            <Badge variant="destructive">{t("integration.orphans.expiredBadge")}</Badge>
          )}
          <span className="text-xs text-muted-foreground">
            {t("integration.orphans.ratio", {
              count: report.count,
              indexed: report.indexedCount,
            })}
          </span>
        </span>
      </AccordionTrigger>
      <AccordionContent>
        <div className="space-y-3 pb-2">
          <dl className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
            <Fact label={t("integration.orphans.discoveredAt")} value={when(report.discoveredAt)} />
            <Fact
              label={t("integration.orphans.expiresAt")}
              value={report.expiresAt ? when(report.expiresAt) : t("integration.orphans.noExpiry")}
            />
            <Fact
              label={t("integration.orphans.decidedAt")}
              value={report.decidedAt ? when(report.decidedAt) : "—"}
            />
          </dl>

          {/*
           * The ids themselves, not a count. Recognising that a report names paths a reader knows
           * are gone — or that it names the whole site — is the judgement this page exists for.
           */}
          <div className="border rounded-md max-h-64 overflow-y-auto">
            <ul className="divide-y">
              {report.objectIds.map((objectId) => (
                <li
                  key={objectId}
                  className="px-3 py-1.5 text-xs font-mono text-muted-foreground truncate"
                  title={objectId}
                >
                  {objectId}
                </li>
              ))}
            </ul>
          </div>

          {open && (
            <div className="flex flex-wrap items-center gap-2">
              <DialogDelete
                feature={t("integration.orphans.title")}
                name={String(report.id)}
                open={confirming}
                setOpen={setConfirming}
                title={t("integration.orphans.confirmTitle")}
                description={t("integration.orphans.confirmDescription", {
                  count: report.count,
                  source: report.source,
                  site: report.site,
                })}
                confirmLabel={t("integration.orphans.apply")}
                onDelete={onApply}
                trigger={
                  <GradientButton
                    size="sm"
                    variant="destructive"
                    className="gap-1"
                    loading={busy}
                    disabled={!applicable}
                  >
                    <IconTrash className="size-4" />
                    {t("integration.orphans.apply")}
                  </GradientButton>
                }
              />
              <GradientButton
                size="sm"
                variant="outline"
                className="gap-1"
                loading={busy}
                onClick={onDiscard}
              >
                <IconX className="size-4" />
                {t("integration.orphans.discard")}
              </GradientButton>
              {report.expired && (
                <span className="text-xs text-muted-foreground">
                  {t("integration.orphans.expiredHelp")}
                </span>
              )}
            </div>
          )}
        </div>
      </AccordionContent>
    </AccordionItem>
  );
}

function Fact({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

function when(iso: string): string {
  return new Date(iso).toLocaleString();
}
