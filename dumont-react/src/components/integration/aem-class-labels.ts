export interface AemClassOption {
  fqcn: string;
  label: string;
  description: string;
  category: "extractor" | "model";
}

export const AEM_EXTRACTOR_CLASSES: AemClassOption[] = [
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentId",
    label: "Content ID",
    description: "Extracts the unique content identifier.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentUrl",
    label: "Content URL",
    description: "Extracts the content URL.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtTypeName",
    label: "Type Name",
    description: "Extracts the content type name.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtSiteName",
    label: "Site Name",
    description: "Extracts the site name.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtCreationDate",
    label: "Creation Date",
    description: "Extracts the creation/publication date.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtModificationDate",
    label: "Modification Date",
    description: "Extracts the last modification date.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    label: "HTML to Text",
    description: "Converts HTML content to plain text.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentTags",
    label: "Content Tags",
    description: "Extracts AEM content tags (cq:tags).",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtSourceApps",
    label: "Source Apps",
    description: "Extracts the source application name.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtPageComponents",
    label: "Page Components",
    description: "Extracts text from all page components.",
    category: "extractor",
  },
  {
    fqcn: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtLocale",
    label: "Locale Detector",
    description: "Detects content locale from the path.",
    category: "extractor",
  },
];

export const AEM_MODEL_CLASSES: AemClassOption[] = [
  {
    fqcn: "com.viglet.dumont.connector.aem.sample.ext.DumAemExtSampleModelJson",
    label: "Sample Model JSON",
    description: "Processes page model.json for field extraction.",
    category: "model",
  },
];

const ALL_CLASSES = [...AEM_EXTRACTOR_CLASSES, ...AEM_MODEL_CLASSES];

const labelMap = new Map<string, AemClassOption>();
ALL_CLASSES.forEach((c) => labelMap.set(c.fqcn, c));

/**
 * Returns a friendly label for a Java FQCN, or the short class name if unknown.
 */
export function getClassLabel(fqcn: string | undefined | null): string {
  if (!fqcn) return "";
  const known = labelMap.get(fqcn);
  if (known) return known.label;
  // Fallback: extract short class name
  const parts = fqcn.split(".");
  return parts[parts.length - 1];
}

/**
 * Returns the known class option, or undefined.
 */
export function getClassOption(fqcn: string): AemClassOption | undefined {
  return labelMap.get(fqcn);
}
