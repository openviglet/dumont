import { SourceArchiveService } from "@/services/integration/source-archive.service";
import { toast } from "@viglet/viglet-design-system";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * "Export all" and "Import ZIP" for a source list (D362), shared by the AEM, DB and web-crawler
 * lists so the three behave the same: the export downloads the zip the plugin builds, the import
 * uploads one back and then reloads the list through `onImported`.
 */
export function useSourceArchive(
  sourceUrl: string,
  fallbackFilename: string,
  onImported: () => Promise<void> | void,
) {
  const { t } = useTranslation();
  const service = useMemo(() => new SourceArchiveService(sourceUrl), [sourceUrl]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);

  const openImport = useCallback(() => fileInputRef.current?.click(), []);

  const exportAll = useCallback(async () => {
    setExporting(true);
    try {
      const { blob, filename } = await service.exportAll();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename ?? fallbackFilename;
      a.click();
      URL.revokeObjectURL(url);
      toast.success(t("forms.importExport.exportSuccess"));
    } catch (error) {
      console.error("Export all sources failed", error);
      toast.error(t("forms.importExport.exportAllFailed"));
    } finally {
      setExporting(false);
    }
  }, [service, fallbackFilename, t]);

  const importFile = useCallback(async (file: File) => {
    setImporting(true);
    try {
      await service.importArchive(file);
      toast.success(t("forms.importExport.importSuccess"));
      await onImported();
    } catch (error) {
      console.error("Import sources failed", error);
      toast.error(t("forms.importExport.importArchiveFailed"));
    } finally {
      setImporting(false);
    }
  }, [service, onImported, t]);

  const onFileChange = useCallback(async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Cleared before any await, so picking the same file twice in a row still fires a change.
    event.target.value = "";
    if (file) await importFile(file);
  }, [importFile]);

  return { fileInputRef, openImport, onFileChange, importFile, exportAll, exporting, importing };
}
