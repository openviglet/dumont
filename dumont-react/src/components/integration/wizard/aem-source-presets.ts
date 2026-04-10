import type { TurIntegrationAemAttributeSpec } from "@/models/integration/integration-aem-attribute-spec.model";
import type { TurIntegrationAemPluginModel } from "@/models/integration/integration-aem-plugin-model.model";

export interface AemSourcePreset {
  key: string;
  contentType: string;
  subType: string;
  localeClass: string;
  deltaClass: string;
  attributeSpecifications: Omit<TurIntegrationAemAttributeSpec, "id">[];
  models: Omit<TurIntegrationAemPluginModel, "id">[];
}

const COMMON_SPECS: Omit<TurIntegrationAemAttributeSpec, "id">[] = [
  {
    name: "id",
    type: "STRING",
    mandatory: true,
    multiValued: false,
    description: "Unique content identifier",
    facet: false,
    facetNames: { default: "Ids" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentId",
    text: "",
  },
  {
    name: "title",
    type: "TEXT",
    mandatory: true,
    multiValued: false,
    description: "Content title",
    facet: false,
    facetNames: { default: "Titles" },
    className: "",
    text: "",
  },
  {
    name: "url",
    type: "STRING",
    mandatory: true,
    multiValued: false,
    description: "Content URL",
    facet: false,
    facetNames: { default: "URLs" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentUrl",
    text: "",
  },
  {
    name: "type",
    type: "STRING",
    mandatory: true,
    multiValued: false,
    description: "Content type name",
    facet: false,
    facetNames: { default: "Content Types" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtTypeName",
    text: "",
  },
  {
    name: "site",
    type: "STRING",
    mandatory: true,
    multiValued: false,
    description: "Site name",
    facet: false,
    facetNames: { default: "Sites" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtSiteName",
    text: "",
  },
  {
    name: "publicationDate",
    type: "DATE",
    mandatory: true,
    multiValued: false,
    description: "Publication date",
    facet: false,
    facetNames: { default: "Publication Dates" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtCreationDate",
    text: "",
  },
  {
    name: "modificationDate",
    type: "DATE",
    mandatory: true,
    multiValued: false,
    description: "Modification date",
    facet: false,
    facetNames: { default: "Modification Dates" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtModificationDate",
    text: "",
  },
  {
    name: "source_apps",
    type: "STRING",
    mandatory: true,
    multiValued: true,
    description: "Source applications",
    facet: true,
    facetNames: { default: "Source Apps" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtSourceApps",
    text: "",
  },
];

const PAGE_EXTRA_SPECS: Omit<TurIntegrationAemAttributeSpec, "id">[] = [
  {
    name: "tags",
    type: "STRING",
    mandatory: true,
    multiValued: true,
    description: "Content tags",
    facet: true,
    facetNames: { default: "Tags" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentTags",
    text: "",
  },
  {
    name: "text",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Page text content (HTML to text)",
    facet: false,
    facetNames: { default: "Texts" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "abstract",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Short summary",
    facet: false,
    facetNames: { default: "Abstracts" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "description",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Content description",
    facet: false,
    facetNames: { default: "Descriptions" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "pageTitle",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Page title from model.json",
    facet: false,
    facetNames: { default: "Page Titles" },
    className: "",
    text: "",
  },
  {
    name: "pageDescription",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Page description from model.json",
    facet: false,
    facetNames: { default: "Page Descriptions" },
    className: "",
    text: "",
  },
  {
    name: "pageLanguage",
    type: "STRING",
    mandatory: false,
    multiValued: false,
    description: "Page language",
    facet: true,
    facetNames: { default: "Languages" },
    className: "",
    text: "",
  },
  {
    name: "pageTemplate",
    type: "STRING",
    mandatory: false,
    multiValued: false,
    description: "Page template name",
    facet: true,
    facetNames: { default: "Templates" },
    className: "",
    text: "",
  },
  {
    name: "pageLastModified",
    type: "DATE",
    mandatory: false,
    multiValued: false,
    description: "Page last modified date",
    facet: false,
    facetNames: { default: "Page Last Modified Dates" },
    className: "",
    text: "",
  },
  {
    name: "paragraphs",
    type: "TEXT",
    mandatory: false,
    multiValued: true,
    description: "Content paragraphs",
    facet: false,
    facetNames: { default: "Paragraphs" },
    className: "",
    text: "",
  },
  {
    name: "texts",
    type: "TEXT",
    mandatory: false,
    multiValued: true,
    description: "Text component content",
    facet: false,
    facetNames: { default: "Texts" },
    className: "",
    text: "",
  },
  {
    name: "pageImages",
    type: "STRING",
    mandatory: false,
    multiValued: true,
    description: "Image sources from page",
    facet: false,
    facetNames: { default: "Page Images" },
    className: "",
    text: "",
  },
];

const CF_EXTRA_SPECS: Omit<TurIntegrationAemAttributeSpec, "id">[] = [
  {
    name: "text",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Generic text content",
    facet: false,
    facetNames: { default: "Generic Text" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "abstract",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Short summary",
    facet: false,
    facetNames: { default: "Abstracts" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "description",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "Content description",
    facet: false,
    facetNames: { default: "Descriptions" },
    className: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
    text: "",
  },
  {
    name: "html",
    type: "TEXT",
    mandatory: false,
    multiValued: false,
    description: "HTML content",
    facet: false,
    facetNames: { default: "HTML content" },
    className: "",
    text: "",
  },
];

export const CQ_PAGE_PRESET: AemSourcePreset = {
  key: "cq-page",
  contentType: "cq:Page",
  subType: "cq:PageContent",
  localeClass: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtLocale",
  deltaClass: "",
  attributeSpecifications: [...COMMON_SPECS, ...PAGE_EXTRA_SPECS],
  models: [
    {
      type: "cq:Page",
      subType: "",
      className: "",
      targetAttrs: [
        {
          id: "",
          name: "title",
          sourceAttrs: [{ id: "", name: "jcr:title", className: "", text: "" }],
        },
        {
          id: "",
          name: "tags",
          sourceAttrs: [{ id: "", name: "cq:tags", className: "", text: "" }],
        },
        {
          id: "",
          name: "text",
          sourceAttrs: [
            {
              id: "",
              name: "",
              className:
                "com.viglet.dumont.connector.aem.commons.ext.DumAemExtPageComponents",
              text: "",
            },
          ],
        },
      ],
    },
  ],
};

export const CONTENT_FRAGMENT_PRESET: AemSourcePreset = {
  key: "content-fragment",
  contentType: "dam:Asset",
  subType: "content-fragment",
  localeClass: "com.viglet.dumont.connector.aem.commons.ext.DumAemExtLocale",
  deltaClass: "",
  attributeSpecifications: [...COMMON_SPECS, ...CF_EXTRA_SPECS],
  models: [
    {
      type: "dam:Asset",
      subType: "content-fragment",
      className: "",
      targetAttrs: [
        {
          id: "",
          name: "title",
          sourceAttrs: [{ id: "", name: "title", className: "", text: "" }],
        },
        {
          id: "",
          name: "abstract",
          sourceAttrs: [
            {
              id: "",
              name: "content",
              className:
                "com.viglet.dumont.connector.aem.commons.ext.DumAemExtHtml2Text",
              text: "",
            },
          ],
        },
        {
          id: "",
          name: "html",
          sourceAttrs: [{ id: "", name: "content", className: "", text: "" }],
        },
        {
          id: "",
          name: "description",
          sourceAttrs: [
            { id: "", name: "description", className: "", text: "" },
          ],
        },
      ],
    },
  ],
};

export const PRESETS: AemSourcePreset[] = [CQ_PAGE_PRESET, CONTENT_FRAGMENT_PRESET];

export function getPresetByKey(key: string): AemSourcePreset | undefined {
  return PRESETS.find((p) => p.key === key);
}
