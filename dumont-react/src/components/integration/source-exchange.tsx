import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { GradientButton } from "@/components/ui/gradient-button";
import type { SourceImportMode, SourceReplacePlan } from "@/models/integration/source-replace-plan.model";
import { IconAlertTriangle, IconFileImport, IconPackageExport } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

/** What a source service offers to move every source of its kind between instances. */
export interface SourceExchangeService {
  exportAll(): Promise<Blob>;
  previewImport(file: File, mode: SourceImportMode): Promise<SourceReplacePlan>;
  importFile(file: File, mode: SourceImportMode): Promise<void>;
}

/** The server's reason for a refused import (a 400 from DU9/DU10/DU15), when it sent one. */
function refusalOf(error: unknown): string | undefined {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; detail?: string } | undefined;
    return data?.detail || data?.message || undefined;
  }
  return undefined;
}

/**
 * Export-all and the one Import of a source list (roadmap DU4, DU16). The zip is the one the
 * plugin's `GET .../export` writes and its `POST .../import` reads, so a file taken from one
 * instance is put back in on another unchanged; a single-source JSON is accepted too (DU15).
 *
 * Picking a file asks the server, writing nothing, what it would do. The replace dry run is asked
 * rather than the merge one because it also names the sources the file leaves out; the merge plan
 * is the same plan without those (DU14). With nothing to overwrite the file is merged at once.
 * Otherwise a dialog names the sources that will be replaced, and an unchecked option also deletes
 * the ones the file does not name. `onImported` reloads the list.
 */
export function useSourceExchange(
  service: SourceExchangeService,
  fileName: string,
  onImported: () => void,
) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const deleteOthersId = useId();
  // The file picked and what the dry run said it would do, while the dialog asks to confirm.
  const [pending, setPending] = useState<{ file: File; plan: SourceReplacePlan } | null>(null);
  const [deleteOthers, setDeleteOthers] = useState(false);
  const [importing, setImporting] = useState(false);

  const exportAll = async () => {
    try {
      const blob = await service.exportAll();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${fileName}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success(t("forms.importExport.exportAllSuccess"));
    } catch {
      toast.error(t("forms.importExport.exportAllFailed"));
    }
  };

  const runImport = async (file: File, mode: SourceImportMode, plan: SourceReplacePlan) => {
    await service.importFile(file, mode);
    toast.success(
      t("forms.importExport.importDone", {
        created: plan.create.length,
        replaced: plan.overwrite.length,
        deleted: mode === "replace" ? plan.remove.length : 0,
      }),
    );
    onImported();
  };

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    let plan: SourceReplacePlan;
    try {
      plan = await service.previewImport(file, "replace");
    } catch (error) {
      toast.error(refusalOf(error) ?? t("forms.importExport.importPreviewFailed"));
      return;
    }
    if (plan.invalid.length === 0 && plan.overwrite.length === 0) {
      try {
        await runImport(file, "merge", plan);
      } catch (error) {
        toast.error(refusalOf(error) ?? t("forms.importExport.importFileFailed"));
      }
      return;
    }
    setDeleteOthers(false);
    setPending({ file, plan });
  };

  const confirmImport = async () => {
    if (!pending) return;
    setImporting(true);
    try {
      await runImport(pending.file, deleteOthers ? "replace" : "merge", pending.plan);
      setPending(null);
    } catch (error) {
      toast.error(refusalOf(error) ?? t("forms.importExport.importFileFailed"));
    } finally {
      setImporting(false);
    }
  };

  const openImport = () => inputRef.current?.click();

  const plan = pending?.plan;
  const blocked = (plan?.invalid.length ?? 0) > 0;
  const confirmLabel = deleteOthers
    ? t("forms.importExport.importConfirmDelete")
    : t("forms.importExport.importConfirm");
  const importDialog = (
    <Dialog open={pending !== null} onOpenChange={(open) => !open && !importing && setPending(null)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <IconAlertTriangle className="size-5 text-amber-500" />
            {t("forms.importExport.importTitle")}
          </DialogTitle>
          <DialogDescription>{t("forms.importExport.importWarning")}</DialogDescription>
        </DialogHeader>
        {plan && (
          <div className="space-y-3 text-sm">
            {plan.overwrite.length > 0 && (
              <div>
                <p className="font-medium">{t("forms.importExport.importReplaced", { count: plan.overwrite.length })}</p>
                <p className="flex flex-wrap gap-1 pt-1">
                  {plan.overwrite.map((name) => (
                    <span
                      key={name}
                      className="rounded bg-amber-100 px-1.5 py-0.5 font-medium text-amber-900 dark:bg-amber-900/40 dark:text-amber-100"
                    >
                      {name}
                    </span>
                  ))}
                </p>
              </div>
            )}
            {plan.create.length > 0 && (
              <div>
                <p className="font-medium">{t("forms.importExport.importCreated", { count: plan.create.length })}</p>
                <p className="break-words text-muted-foreground">{plan.create.join(", ")}</p>
              </div>
            )}
            {plan.remove.length > 0 && (
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={deleteOthersId}
                    checked={deleteOthers}
                    disabled={importing}
                    onCheckedChange={(checked) => setDeleteOthers(checked === true)}
                  />
                  <label htmlFor={deleteOthersId} className="cursor-pointer">
                    {t("forms.importExport.importDeleteOthers", { count: plan.remove.length })}
                  </label>
                </div>
                {deleteOthers && (
                  <p className="break-words font-medium text-destructive">{plan.remove.join(", ")}</p>
                )}
              </div>
            )}
            {blocked && (
              <div role="alert" className="rounded-md border border-destructive/50 p-2 text-destructive">
                <p className="font-medium">{t("forms.importExport.importInvalid")}</p>
                <p className="break-words">{plan.invalid.join(", ")}</p>
              </div>
            )}
          </div>
        )}
        <DialogFooter>
          <GradientButton type="button" variant="outline" onClick={() => setPending(null)} disabled={importing}>
            {t("forms.importExport.importCancel")}
          </GradientButton>
          {blocked ? (
            <GradientButton type="button" variant={deleteOthers ? "destructive" : "default"} disabled>
              {confirmLabel}
            </GradientButton>
          ) : (
            <GradientButton
              type="button"
              variant={deleteOthers ? "destructive" : "default"}
              loading={importing}
              onClick={confirmImport}
            >
              {confirmLabel}
            </GradientButton>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  // Every page renders `input` once, so the dialog rides along with it.
  const input = (
    <>
      <input
        ref={inputRef}
        type="file"
        accept=".zip,.json"
        className="hidden"
        aria-label={t("forms.importExport.importFile")}
        title={t("forms.importExport.importFile")}
        onChange={handleFile}
      />
      {importDialog}
    </>
  );

  const menuItems = (
    <>
      <DropdownMenuItem onClick={exportAll}>
        <IconPackageExport className="size-4 mr-2" />
        {t("forms.importExport.exportAll")}
      </DropdownMenuItem>
      <DropdownMenuItem onClick={openImport}>
        <IconFileImport className="size-4 mr-2" />
        {t("forms.importExport.importFile")}
      </DropdownMenuItem>
    </>
  );

  /** The empty list has no menu, so the import is a button under the blank slate. */
  const emptyImport = (
    <div className="flex justify-center pb-6">
      <GradientButton type="button" variant="outline" size="sm" onClick={openImport}>
        <IconFileImport className="size-4" />
        {t("forms.importExport.importFile")}
      </GradientButton>
    </div>
  );

  return { input, menuItems, emptyImport };
}
