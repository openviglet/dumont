import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  IconAlertTriangle,
  IconLoader2,
  IconSparkles,
} from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

/** RAG readiness of a Turing SN site, as returned by Dumont's genai-status proxy (D17). */
interface RagStatus {
  site: string;
  ragEnabled: boolean;
  reason: string;
}

interface Props {
  /** Integration instance id (standalone uses the fixed "local" id). */
  integrationId: string;
  /** The Turing SN site name the source publishes to. */
  siteName: string | undefined;
  className?: string;
}

/**
 * Shows whether the target Turing SN site has RAG enabled, so a source bound to a non-RAG site is
 * flagged — its documents would be keyword-only, never embedded (roadmap D17). Renders nothing
 * when no site name is set. The disabled state explains why via Turing's diagnostic reason code.
 */
export const RagStatusBadge: React.FC<Props> = ({ integrationId, siteName, className }) => {
  const { t } = useTranslation();
  const [status, setStatus] = useState<RagStatus | null>(null);
  const [loading, setLoading] = useState(false);

  const site = siteName?.trim();

  useEffect(() => {
    if (!site) {
      setStatus(null);
      return;
    }
    let active = true;
    setLoading(true);
    axios
      .get<RagStatus>(
        `/v2/integration/${integrationId}/connector/genai-status/${encodeURIComponent(site)}`
      )
      .then(({ data }) => {
        if (active) setStatus(data);
      })
      .catch(() => {
        if (active) setStatus({ site, ragEnabled: false, reason: "UNREACHABLE" });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [integrationId, site]);

  if (!site) {
    return null;
  }

  if (loading) {
    return (
      <Badge
        variant="outline"
        className={`flex w-fit items-center gap-1.5 px-2 py-0.5 text-[10px] font-semibold tracking-tight uppercase bg-slate-500/10 text-slate-600 border-slate-500/20 ${className ?? ""}`}
      >
        <IconLoader2 className="h-3 w-3 animate-spin" />
        {t("integration.ragStatus.checking")}
      </Badge>
    );
  }

  if (!status) {
    return null;
  }

  if (status.ragEnabled) {
    return (
      <Badge
        variant="outline"
        className={`flex w-fit items-center gap-1.5 px-2 py-0.5 text-[10px] font-semibold tracking-tight uppercase bg-emerald-500/10 text-emerald-600 border-emerald-500/20 ${className ?? ""}`}
      >
        <IconSparkles className="h-3 w-3" />
        {t("integration.ragStatus.enabled")}
      </Badge>
    );
  }

  const reasonLabel = t(
    `integration.ragStatus.reason.${status.reason}`,
    t("integration.ragStatus.reason.UNKNOWN")
  );

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Badge
            variant="outline"
            className={`flex w-fit items-center gap-1.5 px-2 py-0.5 text-[10px] font-semibold tracking-tight uppercase bg-amber-500/10 text-amber-600 border-amber-500/20 ${className ?? ""}`}
          >
            <IconAlertTriangle className="h-3 w-3" />
            {t("integration.ragStatus.disabled")}
          </Badge>
        </TooltipTrigger>
        <TooltipContent>
          <p className="max-w-xs text-xs">{reasonLabel}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};
