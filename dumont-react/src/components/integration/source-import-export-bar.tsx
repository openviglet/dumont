import { GradientButton } from "@/components/ui/gradient-button";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { IconDownload, IconUpload } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useRef } from "react";
import { useTranslation } from "react-i18next";
import {
  exportSourceToJson,
  importSourceFromJson,
} from "./source-import-export";

interface SourceImportExportBarProps {
  source?: TurIntegrationAemSource;
  onImport: (data: Partial<TurIntegrationAemSource>) => void;
  showExport?: boolean;
}

export function SourceImportExportBar({
  source,
  onImport,
  showExport = true,
}: Readonly<SourceImportExportBarProps>) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const json = event.target?.result as string;
      const parsed = importSourceFromJson(json);
      if (parsed) {
        onImport(parsed);
        toast.success(t("forms.importExport.importSuccess"));
      } else {
        toast.error(t("forms.importExport.importFailed"));
      }
    };
    reader.readAsText(file);

    // reset so the same file can be re-imported
    e.target.value = "";
  };

  const handleExport = () => {
    if (!source) return;
    const json = exportSourceToJson(source);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${source.name || "aem-source"}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success(t("forms.importExport.exportSuccess"));
  };

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        className="hidden"
        onChange={handleFileChange}
      />
      <GradientButton
        type="button"
        variant="outline"
        size="sm"
        onClick={handleImportClick}
      >
        <IconUpload className="size-4" />
        {t("forms.importExport.import")}
      </GradientButton>
      {showExport && source?.name && (
        <GradientButton
          type="button"
          variant="outline"
          size="sm"
          onClick={handleExport}
        >
          <IconDownload className="size-4" />
          {t("forms.importExport.export")}
        </GradientButton>
      )}
    </>
  );
}
