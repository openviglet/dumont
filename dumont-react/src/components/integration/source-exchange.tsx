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
import type { SourceReplacePlan } from "@/models/integration/source-replace-plan.model";
import { IconAlertTriangle, IconFileZip, IconPackageExport, IconReplace } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import axios from "axios";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";

/** What a source service offers to move every source of its kind between instances. */
export interface SourceExchangeService {
  exportAll(): Promise<Blob>;
  importAll(file: File): Promise<void>;
  previewReplace(file: File): Promise<SourceReplacePlan>;
  replaceAll(file: File): Promise<void>;
}

/** The server's reason for a refused import (a 400 from DU9/DU10), when it sent one. */
function refusalOf(error: unknown): string | undefined {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; detail?: string } | undefined;
    return data?.detail || data?.message || undefined;
  }
  return undefined;
}

/**
 * Export-all and import-ZIP for a source list (roadmap DU4). The zip is the one the plugin's
 * `GET .../export` writes and its `POST .../import` reads, so a file taken from one instance
 * is put back in on another unchanged. `onImported` reloads the list.
 */
export function useSourceExchange(
  service: SourceExchangeService,
  fileName: string,
  onImported: () => void,
) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const replaceInputRef = useRef<HTMLInputElement>(null);
  // DU12 - the file picked for "replace all" and what the dry run said it would do.
  const [pending, setPending] = useState<{ file: File; plan: SourceReplacePlan } | null>(null);
  const [replacing, setReplacing] = useState(false);

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

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    try {
      await service.importAll(file);
      toast.success(t("forms.importExport.importZipSuccess"));
      onImported();
    } catch {
      toast.error(t("forms.importExport.importZipFailed"));
    }
  };

  const openImport = () => inputRef.current?.click();
  const openReplace = () => replaceInputRef.current?.click();

  /** Nothing is written here: the dry run only fills the confirmation dialog. */
  const handleReplaceFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    try {
      setPending({ file, plan: await service.previewReplace(file) });
    } catch (error) {
      toast.error(refusalOf(error) ?? t("forms.importExport.replacePreviewFailed"));
    }
  };

  const confirmReplace = async () => {
    if (!pending) return;
    setReplacing(true);
    try {
      await service.replaceAll(pending.file);
      toast.success(t("forms.importExport.replaceSuccess"));
      setPending(null);
      onImported();
    } catch (error) {
      toast.error(refusalOf(error) ?? t("forms.importExport.replaceFailed"));
    } finally {
      setReplacing(false);
    }
  };

  const plan = pending?.plan;
  const blocked = (plan?.invalid.length ?? 0) > 0;
  const replaceDialog = (
    <Dialog open={pending !== null} onOpenChange={(open) => !open && !replacing && setPending(null)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-destructive">
            <IconAlertTriangle className="size-5" />
            {t("forms.importExport.replaceTitle")}
          </DialogTitle>
          <DialogDescription>{t("forms.importExport.replaceWarning")}</DialogDescription>
        </DialogHeader>
        {plan && (
          <div className="space-y-3 text-sm">
            <ul className="space-y-1">
              <li>{t("forms.importExport.replaceCreate", { count: plan.create.length })}</li>
              <li>{t("forms.importExport.replaceOverwrite", { count: plan.overwrite.length })}</li>
              <li className={plan.remove.length > 0 ? "font-semibold text-destructive" : undefined}>
                {t("forms.importExport.replaceRemove", { count: plan.remove.length })}
              </li>
            </ul>
            {plan.remove.length > 0 && (
              <div>
                <p className="font-medium">{t("forms.importExport.replaceRemovedNames")}</p>
                <p className="break-words text-muted-foreground">{plan.remove.join(", ")}</p>
              </div>
            )}
            {blocked && (
              <div role="alert" className="rounded-md border border-destructive/50 p-2 text-destructive">
                <p className="font-medium">{t("forms.importExport.replaceInvalid")}</p>
                <p className="break-words">{plan.invalid.join(", ")}</p>
              </div>
            )}
          </div>
        )}
        <DialogFooter>
          <GradientButton type="button" variant="outline" onClick={() => setPending(null)} disabled={replacing}>
            {t("forms.importExport.replaceCancel")}
          </GradientButton>
          {blocked ? (
            <GradientButton type="button" variant="destructive" disabled>
              {t("forms.importExport.replaceConfirm")}
            </GradientButton>
          ) : (
            <GradientButton type="button" variant="destructive" loading={replacing} onClick={confirmReplace}>
              {t("forms.importExport.replaceConfirm")}
            </GradientButton>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  const input = (
    <input
      ref={inputRef}
      type="file"
      accept=".zip,.json"
      className="hidden"
      aria-label={t("forms.importExport.importZip")}
      title={t("forms.importExport.importZip")}
      onChange={handleFile}
    />
  );
  // Every page renders `input` once, so the replace input and its dialog ride along with it.
  const inputs = (
    <>
      {input}
      <input
        ref={replaceInputRef}
        type="file"
        accept=".zip,.json"
        className="hidden"
        aria-label={t("forms.importExport.replaceAll")}
        title={t("forms.importExport.replaceAll")}
        onChange={handleReplaceFile}
      />
      {replaceDialog}
    </>
  );

  const menuItems = (
    <>
      <DropdownMenuItem onClick={exportAll}>
        <IconPackageExport className="size-4 mr-2" />
        {t("forms.importExport.exportAll")}
      </DropdownMenuItem>
      <DropdownMenuItem onClick={openImport}>
        <IconFileZip className="size-4 mr-2" />
        {t("forms.importExport.importZip")}
      </DropdownMenuItem>
      <DropdownMenuItem onClick={openReplace} className="text-destructive focus:text-destructive">
        <IconReplace className="size-4 mr-2" />
        {t("forms.importExport.replaceAll")}
      </DropdownMenuItem>
    </>
  );

  /** The empty list has no menu, so the import is a button under the blank slate. */
  const emptyImport = (
    <div className="flex justify-center pb-6">
      <GradientButton type="button" variant="outline" size="sm" onClick={openImport}>
        <IconFileZip className="size-4" />
        {t("forms.importExport.importZip")}
      </GradientButton>
    </div>
  );

  return { input: inputs, menuItems, emptyImport };
}
