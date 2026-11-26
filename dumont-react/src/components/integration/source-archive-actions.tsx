import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { GradientButton } from "@/components/ui/gradient-button";
import { IconFileZip, IconPackageExport } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { useSourceArchive } from "./use-source-archive";

type SourceArchive = ReturnType<typeof useSourceArchive>;

/**
 * The hidden file picker the "Import ZIP" actions open (D362). The ref and the handler come as
 * their own props, not inside the hook's object: reading a ref off an object during render is what
 * the React Compiler rule refuses.
 */
export function SourceArchiveInput({ inputRef, onChange, accept = ".zip" }: Readonly<{
  inputRef: React.RefObject<HTMLInputElement | null>;
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  accept?: string;
}>) {
  const { t } = useTranslation();
  return (
    <input
      ref={inputRef}
      type="file"
      accept={accept}
      className="hidden"
      aria-label={t("forms.importExport.importArchive")}
      title={t("forms.importExport.importArchive")}
      onChange={onChange}
    />
  );
}

/** "Export all (ZIP)" and "Import ZIP", as items of a source list's action menu (D362). */
export function SourceArchiveMenuItems({ archive }: Readonly<{ archive: SourceArchive }>) {
  const { t } = useTranslation();
  return (
    <>
      <DropdownMenuItem onClick={archive.exportAll} disabled={archive.exporting}>
        <IconPackageExport className="size-4 mr-2" />
        {t("forms.importExport.exportAll")}
      </DropdownMenuItem>
      <DropdownMenuItem onClick={archive.openImport} disabled={archive.importing}>
        <IconFileZip className="size-4 mr-2" />
        {t("forms.importExport.importArchive")}
      </DropdownMenuItem>
    </>
  );
}

/** "Import ZIP" beside the New action of an empty source list: an empty instance is where it is needed most. */
export function SourceArchiveImportButton({ archive }: Readonly<{ archive: SourceArchive }>) {
  const { t } = useTranslation();
  return (
    <GradientButton type="button" variant="outline" onClick={archive.openImport} loading={archive.importing}>
      <IconFileZip className="size-4" />
      {t("forms.importExport.importArchive")}
    </GradientButton>
  );
}
