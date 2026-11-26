import { GradientButton } from "@/components/ui/gradient-button";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { IconFileZip, IconPackageExport } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef } from "react";
import { useTranslation } from "react-i18next";

/** What a source service offers to move every source of its kind between instances. */
export interface SourceExchangeService {
  exportAll(): Promise<Blob>;
  importAll(file: File): Promise<void>;
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

  const input = (
    <input
      ref={inputRef}
      type="file"
      accept=".zip"
      className="hidden"
      aria-label={t("forms.importExport.importZip")}
      title={t("forms.importExport.importZip")}
      onChange={handleFile}
    />
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

  return { input, menuItems, emptyImport };
}
