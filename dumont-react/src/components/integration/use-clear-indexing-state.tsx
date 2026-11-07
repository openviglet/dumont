import type { AdaptiveFormAction } from "@/components/bento/adaptive-form-header";
import { TurIntegrationConnectorService } from "@/services/integration/integration-connector.service";
import { IconEraser } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { type ReactNode, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { DialogDelete } from "../dialog.delete";

interface Options {
  integrationId: string;
  /** What the connector knows the source by — the identifier its reindex/index calls already pass. */
  source: string;
  /** What the reader knows it by: the toast names it and the dialog asks for it to be typed. */
  label: string;
  /** The source is new or not loaded yet, so there is no state to clear. */
  disabled?: boolean;
}

/** What a source form takes to offer the action: its menu entry and the dialog behind it. */
export interface ClearIndexingState {
  menuAction: AdaptiveFormAction;
  dialog: ReactNode;
}

/**
 * Clear indexing state, the same on every source page.
 *
 * Returns a menu action for the form header's `⋮` and the confirmation dialog it opens. The dialog
 * is rendered without a trigger, so the caller places it anywhere in the header; the menu item is
 * what opens it.
 */
export function useClearIndexingState({ integrationId, source, label, disabled }: Options): ClearIndexingState {
  const { t } = useTranslation();
  const service = useMemo(() => new TurIntegrationConnectorService(integrationId), [integrationId]);
  const [open, setOpen] = useState(false);
  const [clearing, setClearing] = useState(false);

  async function onClear() {
    setOpen(false);
    setClearing(true);
    try {
      await service.clearState(source);
      toast.success(t("integration.clearState.cleared", { name: label }));
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.warning(t("integration.clearState.runInProgress", { name: label }));
      } else {
        console.error("Clear indexing state error", error);
        toast.error(t("integration.clearState.failed", { name: label }));
      }
    } finally {
      setClearing(false);
    }
  }

  return {
    menuAction: {
      label: t("integration.clearState.action"),
      icon: IconEraser,
      onSelect: () => setOpen(true),
      disabled: disabled || clearing || !source,
      tone: "destructive",
    },
    dialog: (
      <DialogDelete
        trigger={null}
        feature={t("integration.clearState.action")}
        name={label}
        open={open}
        setOpen={setOpen}
        title={t("integration.clearState.confirmTitle")}
        description={t("integration.clearState.confirmDescription", { name: label })}
        confirmLabel={t("integration.clearState.action")}
        onDelete={() => void onClear()}
      />
    ),
  };
}
