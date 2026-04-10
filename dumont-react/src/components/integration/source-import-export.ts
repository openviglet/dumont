import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationAemAttributeSpec } from "@/models/integration/integration-aem-attribute-spec.model";
import type { TurIntegrationAemPluginModel } from "@/models/integration/integration-aem-plugin-model.model";
import type { TurIntegrationAemTargetAttribute } from "@/models/integration/integration-aem-target-attribute.model";
import type { TurIntegrationAemSourceAttribute } from "@/models/integration/integration-aem-source-attribute.model";
import type { TurIntegrationAemLocalePath } from "@/models/integration/integration-aem-locale-path.model";

// --- Exchange format (matches backend DumAemExchange / wknd.json) ---

interface ExchangeSourceAttr {
  name?: string;
  className?: string;
  text?: string;
}

interface ExchangeTargetAttr {
  name: string;
  sourceAttrs: ExchangeSourceAttr[];
}

interface ExchangeModel {
  type: string;
  subType?: string;
  className?: string;
  targetAttrs: ExchangeTargetAttr[];
}

interface ExchangeAttrib {
  name: string;
  className?: string;
  text?: string;
  type: string;
  mandatory?: boolean;
  multiValued?: boolean;
  description?: string;
  facet?: boolean;
  facetName?: Record<string, string>;
}

interface ExchangeLocalePath {
  locale: string;
  path: string;
}

interface ExchangeSource {
  id?: string;
  name: string;
  defaultLocale?: string;
  localeClass?: string;
  deltaClass?: string;
  endpoint?: string;
  username?: string;
  password?: string;
  oncePattern?: string;
  author?: boolean;
  publish?: boolean;
  authorSNSite?: string;
  publishSNSite?: string;
  authorURLPrefix?: string;
  publishURLPrefix?: string;
  rootPath?: string;
  contentType?: string;
  subType?: string;
  attributes?: ExchangeAttrib[];
  // cf.json uses targetAttrDefinitions instead of attributes
  targetAttrDefinitions?: ExchangeAttrib[];
  models?: ExchangeModel[];
  localePaths?: ExchangeLocalePath[];
}

interface ExchangeRoot {
  sources?: ExchangeSource[];
  // cf.json format (flat, no sources wrapper)
  targetAttrDefinitions?: ExchangeAttrib[];
  models?: ExchangeModel[];
}

// --- Import ---

function mapAttrib(attr: ExchangeAttrib): TurIntegrationAemAttributeSpec {
  return {
    id: "",
    name: attr.name,
    className: attr.className ?? "",
    text: attr.text ?? "",
    type: attr.type ?? "TEXT",
    mandatory: attr.mandatory ?? false,
    multiValued: attr.multiValued ?? false,
    description: attr.description ?? "",
    facet: attr.facet ?? false,
    facetNames: attr.facetName ?? {},
  };
}

function mapSourceAttr(sa: ExchangeSourceAttr): TurIntegrationAemSourceAttribute {
  return {
    id: "",
    name: sa.name ?? "",
    className: sa.className ?? "",
    text: sa.text ?? "",
  };
}

function mapTargetAttr(ta: ExchangeTargetAttr): TurIntegrationAemTargetAttribute {
  return {
    id: "",
    name: ta.name,
    sourceAttrs: (ta.sourceAttrs ?? []).map(mapSourceAttr),
  };
}

function mapModel(m: ExchangeModel): TurIntegrationAemPluginModel {
  return {
    id: "",
    type: m.type,
    subType: m.subType ?? "",
    className: m.className ?? "",
    targetAttrs: (m.targetAttrs ?? []).map(mapTargetAttr),
  };
}

function mapLocalePath(lp: ExchangeLocalePath): TurIntegrationAemLocalePath {
  return { id: "", locale: lp.locale, path: lp.path };
}

export function importSourceFromJson(json: string): Partial<TurIntegrationAemSource> | null {
  try {
    const data: ExchangeRoot & ExchangeSource = JSON.parse(json);

    // Format 1: { sources: [...] } (wknd.json)
    if (data.sources && data.sources.length > 0) {
      const src = data.sources[0];
      return mapExchangeSource(src);
    }

    // Format 2: { targetAttrDefinitions: [...], models: [...] } (cf.json - specs only)
    if (data.targetAttrDefinitions || data.models) {
      return {
        attributeSpecifications: (data.targetAttrDefinitions ?? []).map(mapAttrib),
        models: (data.models ?? []).map(mapModel),
      };
    }

    // Format 3: flat source object (single source without wrapper)
    if (data.name && (data.endpoint || data.attributes || data.contentType)) {
      return mapExchangeSource(data as ExchangeSource);
    }

    return null;
  } catch {
    return null;
  }
}

function mapExchangeSource(src: ExchangeSource): Partial<TurIntegrationAemSource> {
  return {
    name: src.name,
    endpoint: src.endpoint ?? "",
    username: src.username ?? "",
    password: src.password ?? "",
    rootPath: src.rootPath ?? "",
    contentType: src.contentType ?? "",
    subType: src.subType ?? "",
    oncePattern: src.oncePattern ?? "",
    defaultLocale: src.defaultLocale ?? "",
    localeClass: src.localeClass ?? "",
    deltaClass: src.deltaClass ?? "",
    author: src.author ?? false,
    publish: src.publish ?? false,
    authorSNSite: src.authorSNSite ?? "",
    publishSNSite: src.publishSNSite ?? "",
    authorURLPrefix: src.authorURLPrefix ?? "",
    publishURLPrefix: src.publishURLPrefix ?? "",
    localePaths: (src.localePaths ?? []).map(mapLocalePath),
    attributeSpecifications: (src.attributes ?? src.targetAttrDefinitions ?? []).map(mapAttrib),
    models: (src.models ?? []).map(mapModel),
  };
}

// --- Export ---

export function exportSourceToJson(source: TurIntegrationAemSource): string {
  const exchangeSource: ExchangeSource = {
    name: source.name,
    defaultLocale: source.defaultLocale || undefined,
    localeClass: source.localeClass || undefined,
    deltaClass: source.deltaClass || undefined,
    endpoint: source.endpoint,
    username: source.username,
    password: source.password,
    oncePattern: source.oncePattern || undefined,
    author: source.author,
    publish: source.publish,
    authorSNSite: source.authorSNSite || undefined,
    publishSNSite: source.publishSNSite || undefined,
    authorURLPrefix: source.authorURLPrefix || undefined,
    publishURLPrefix: source.publishURLPrefix || undefined,
    rootPath: source.rootPath,
    contentType: source.contentType,
    subType: source.subType || undefined,
    localePaths: (source.localePaths ?? []).map((lp) => ({
      locale: lp.locale,
      path: lp.path,
    })),
    attributes: (source.attributeSpecifications ?? []).map((spec) => ({
      name: spec.name,
      className: spec.className || undefined,
      text: spec.text || undefined,
      type: spec.type,
      mandatory: spec.mandatory || undefined,
      multiValued: spec.multiValued || undefined,
      description: spec.description || undefined,
      facet: spec.facet || undefined,
      facetName: Object.keys(spec.facetNames ?? {}).length > 0 ? spec.facetNames : undefined,
    })),
    models: (source.models ?? []).map((model) => ({
      type: model.type,
      subType: model.subType || undefined,
      className: model.className || undefined,
      targetAttrs: (model.targetAttrs ?? []).map((ta) => ({
        name: ta.name,
        sourceAttrs: (ta.sourceAttrs ?? []).map((sa) => ({
          name: sa.name || undefined,
          className: sa.className || undefined,
          text: sa.text || undefined,
        })),
      })),
    })),
  };

  const exchange: ExchangeRoot = { sources: [exchangeSource] };
  return JSON.stringify(exchange, null, 2);
}
