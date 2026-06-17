/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.source;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentTypeField;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.navigator.AemNodeNavigator;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemContentDefinitionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} that adapts a single AEM source <em>environment</em> (author or
 * publish) to the structured-source SPI (roadmap <strong>D21</strong>).
 *
 * <p>
 * It wraps the connector's existing extraction <em>as-is</em>, gaining the typed contract plus the
 * inherited grounding / dry-run preview (D14) / field-coverage &amp; drift (D15) / provenance (D16)
 * surface without a rewrite:
 * <ul>
 * <li>{@link #schema()} wraps the source's {@link DumAemAttributeSpecification}s (name / type /
 * multiValued / facet / mandatory — already 1:1 with {@link DumStructuredField}).</li>
 * <li>{@link #discover()} delegates to the existing path / QueryBuilder enumeration
 * ({@link AemNodeNavigator}).</li>
 * <li>{@link #extract(String)} reuses {@link DumAemService#getTargetAttrValueMap} (the
 * {@code DumAemAttrProcess} field extraction, including the monolithic content-extractor) decoupled
 * from the author/publish job-item pipeline — it extracts the <em>one</em> environment this adapter
 * is bound to. The owning plugin drives both environments and preserves delta (checksum) + deindex
 * semantics via {@code finishIndexing}.</li>
 * </ul>
 *
 * <p>
 * The published document id is the value of the source's own {@code id} attribute (the legacy
 * identity), falling back to the content path. The per-record delta checksum and locale that legacy
 * stamps on each job item are computed here during {@link #extract(String)} and exposed via
 * {@link #checksum(String)} / {@link #locale(String)} so the plugin can reproduce them at parity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumAemStructuredSource implements DumStructuredSource {

    private static final String ID_ATTRIBUTE = "id";

    private final DumAemSource source;
    @Getter
    private final DumAemEnv environment;
    private final DumAemSessionService sessionService;
    private final DumAemObjectService objectService;
    private final DumAemService aemService;
    private final AemNodeNavigator navigator;
    private final DumAemContentDefinitionService contentDefinitionService;
    private final boolean queryBuilderEnabled;

    private DumAemSession session;
    private DumStructuredSchema schema;
    // Per-content-type schemas (D25), keyed by resolved content type; empty for an untyped source.
    private Map<String, DumStructuredSchema> schemasByType;
    // Per-record carriers (parity): the delta checksum and resolved locale legacy stamps per job
    // item. Populated during extract(id) and read back by the plugin in toJobItem.
    private final Map<String, String> checksums = new ConcurrentHashMap<>();
    private final Map<String, Locale> locales = new ConcurrentHashMap<>();

    public DumAemStructuredSource(DumAemSource source, DumAemEnv environment,
            DumAemSessionService sessionService, DumAemObjectService objectService,
            DumAemService aemService, AemNodeNavigator navigator,
            DumAemContentDefinitionService contentDefinitionService, boolean queryBuilderEnabled) {
        this.source = source;
        this.environment = environment;
        this.sessionService = sessionService;
        this.objectService = objectService;
        this.aemService = aemService;
        this.navigator = navigator;
        this.contentDefinitionService = contentDefinitionService;
        this.queryBuilderEnabled = queryBuilderEnabled;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    @Override
    public String getProviderName() {
        return AEM;
    }

    @Override
    public Collection<String> getSiteNames() {
        DumAemConfiguration config = session().getConfiguration();
        String site = environment == DumAemEnv.AUTHOR ? config.getAuthorSNSite()
                : config.getPublishSNSite();
        return StringUtils.isNotBlank(site) ? List.of(site) : List.of();
    }

    @Override
    public Locale getLocale() {
        return source.getDefaultLocale() != null ? source.getDefaultLocale() : Locale.US;
    }

    /** The {@code site} attribute legacy stamps on every document, when configured. */
    public Optional<String> contentSiteName() {
        return Optional.ofNullable(session().getConfiguration().getSiteName())
                .filter(StringUtils::isNotBlank);
    }

    /** The per-record delta checksum captured during {@link #extract(String)} (parity with legacy). */
    public Optional<String> checksum(String recordId) {
        return Optional.ofNullable(checksums.get(recordId));
    }

    /** The per-record resolved locale captured during {@link #extract(String)} (parity with legacy). */
    public Optional<Locale> locale(String recordId) {
        return Optional.ofNullable(locales.get(recordId));
    }

    /**
     * The source-level schema: the union of the source's {@link DumAemAttributeSpecification}s and
     * every declared content type's fields (roadmap <strong>D25</strong>). Per-record grounding binds
     * to the resolved <em>content type's</em> schema; this union is the superset published as attribute
     * specs and used for the D14 baseline / aggregate D15 coverage, so an untyped source is unchanged.
     */
    @Override
    public synchronized DumStructuredSchema schema() {
        ensureSchemas();
        return schema;
    }

    /** Build the union schema and the per-content-type schemas once, lazily. */
    private synchronized void ensureSchemas() {
        if (schema != null) {
            return;
        }
        Map<String, DumStructuredField> union = new LinkedHashMap<>();
        for (DumAemAttributeSpecification spec : source.getAttributeSpecifications()) {
            if (StringUtils.isBlank(spec.getName()) || union.containsKey(spec.getName())) {
                continue;
            }
            union.put(spec.getName(), DumStructuredField.builder(spec.getName())
                    .type(spec.getType() != null ? spec.getType() : TurSEFieldType.STRING)
                    .multiValued(spec.isMultiValued())
                    .facet(spec.isFacet())
                    .mandatory(spec.isMandatory())
                    .description(spec.getDescription())
                    .build());
        }
        Map<String, DumStructuredSchema> byType = new LinkedHashMap<>();
        for (DumAemContentType<?> contentType : declaredContentTypes()) {
            DumStructuredSchema.Builder typeSchema = DumStructuredSchema.builder();
            for (DumAemContentTypeField field : contentType.fields()) {
                DumStructuredField structuredField = toField(field);
                typeSchema.field(structuredField);
                union.putIfAbsent(structuredField.name(), structuredField);
            }
            byType.put(contentType.name(), typeSchema.build());
        }
        schemasByType = byType;
        DumStructuredSchema.Builder unionBuilder = DumStructuredSchema.builder();
        union.values().forEach(unionBuilder::field);
        schema = unionBuilder.build();
    }

    private static DumStructuredField toField(DumAemContentTypeField field) {
        return DumStructuredField.builder(field.name())
                .type(field.type())
                .multiValued(field.multiValued())
                .facet(field.facet())
                .mandatory(field.mandatory())
                .description(field.description())
                .build();
    }

    /**
     * The content types the source's content extractor declares (roadmap <strong>D25</strong>),
     * resolved through the custom-class cache from the content mapping's class name. Empty when the
     * extension declares none or is not a {@link DumAemExtModelJsonBase} — the untyped case.
     */
    private List<? extends DumAemContentType<?>> declaredContentTypes() {
        String className = Optional.ofNullable(session())
                .map(DumAemSession::getModel)
                .map(DumAemModel::getClassName)
                .orElse(null);
        if (StringUtils.isBlank(className)) {
            return List.of();
        }
        return DumCustomClassCache.getCustomClassMap(className)
                .filter(DumAemExtModelJsonBase.class::isInstance)
                .map(extension -> ((DumAemExtModelJsonBase<?>) extension).declaredContentTypes())
                .orElseGet(List::of);
    }

    @Override
    public List<String> discover() {
        DumAemSession aemSession = session();
        if (queryBuilderEnabled) {
            return navigator.collectAccessiblePathsViaQueryBuilder(aemSession);
        }
        DumAemConfiguration config = aemSession.getConfiguration();
        String rootPath = config.getRootPath();
        return DumAemCommonsUtils.getInfinityJson(rootPath, config, false)
                .map(infinityJson -> navigator.collectAccessiblePaths(aemSession, rootPath, infinityJson))
                .orElseGet(() -> {
                    log.warn("AEM source '{}' root path '{}' not accessible", source.getName(), rootPath);
                    return List.of();
                });
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        DumAemSession aemSession = session();
        DumAemConfiguration config = aemSession.getConfiguration();
        Optional<JSONObject> authorJson = DumAemCommonsUtils.getInfinityJson(contentId, config, false);
        if (authorJson.isEmpty()) {
            return Optional.empty();
        }
        DumAemObjectGeneric generic = objectService.getDumAemObjectGeneric(contentId,
                authorJson.get(), DumAemEvent.INDEXING);
        if (!isEligible(generic, config)) {
            return Optional.empty();
        }
        DumAemObject aemObject;
        if (environment == DumAemEnv.AUTHOR) {
            aemObject = new DumAemObject(generic, DumAemEnv.AUTHOR);
        } else {
            // Publish indexes only delivered content; stale/undelivered removal is handled by the
            // tracking-driven deindex sweep in finishIndexing (delta semantics), not here.
            if (!generic.isDelivered()) {
                return Optional.empty();
            }
            aemObject = publishObject(contentId, generic, config);
        }
        DumAemAttrMap attributes = aemService.getTargetAttrValueMap(aemSession, aemObject);
        // Content type the extension resolved for this page (D25): surfaced under the reserved attr,
        // stripped here so it never publishes, and used to pick the record's per-type schema.
        ensureSchemas();
        String contentType = firstValue(attributes.remove(DumAemExtModelJsonBase.CONTENT_TYPE_ATTR))
                .orElse(null);
        DumStructuredSchema recordSchema = contentType != null
                ? schemasByType.getOrDefault(contentType, schema)
                : schema;
        String recordId = firstValue(attributes.get(ID_ATTRIBUTE)).orElse(contentId);
        DumStructuredRecord record = toRecord(recordId, recordSchema, contentType, attributes);
        checksums.put(recordId, String.valueOf(contentDefinitionService
                .getDeltaDate(aemObject, config, aemSession.getContentMapping()).getTime()));
        locales.put(recordId, DumAemCommonsUtils.getLocaleFromAemObject(config, aemObject));
        return Optional.of(record);
    }

    /**
     * Build the publish-side {@link DumAemObject}, mirroring the legacy job pipeline: when a
     * {@code publishURLPrefix} is configured, re-fetch the content's infinity.json from publish so
     * extraction reflects what is live; otherwise reuse the author payload. Either way the object is
     * wrapped {@link DumAemEnv#PUBLISHING} so delivery is preserved.
     */
    private DumAemObject publishObject(String path, DumAemObjectGeneric authorGeneric,
            DumAemConfiguration config) {
        if (StringUtils.isBlank(config.getPublishURLPrefix())) {
            return new DumAemObject(authorGeneric, DumAemEnv.PUBLISHING);
        }
        return DumAemCommonsUtils.getInfinityJson(path, config, false, DumAemEnv.PUBLISHING)
                .map(publishJson -> new DumAemObject(objectService.getDumAemObjectGeneric(path,
                        publishJson, DumAemEvent.PUBLISHING), DumAemEnv.PUBLISHING))
                .orElseGet(() -> {
                    log.warn("AEM source '{}' publish infinity.json unavailable for {} — "
                            + "falling back to author payload", source.getName(), path);
                    return new DumAemObject(authorGeneric, DumAemEnv.PUBLISHING);
                });
    }

    /** Within the configured root path and a valid content type (mirrors the legacy eligibility gate). */
    private boolean isEligible(DumAemObjectGeneric generic, DumAemConfiguration config) {
        String rootPath = config.getRootPath();
        if (rootPath != null && !generic.getPath().startsWith(rootPath)) {
            return false;
        }
        String contentType = config.getContentType();
        return contentType != null && !aemService.isNotValidType(session().getModel(), generic, contentType);
    }

    /**
     * Project the extracted attribute map onto the record's schema (the resolved content type's
     * schema when typed, the source union otherwise), dropping unknown/blank fields, and tag the
     * record with its content type (roadmap <strong>D25</strong>) so the plugin can bucket coverage.
     */
    private DumStructuredRecord toRecord(String recordId, DumStructuredSchema recordSchema,
            String contentType, DumAemAttrMap attributes) {
        DumStructuredRecord.Builder record = DumStructuredRecord.builder(recordId, recordSchema)
                .contentType(contentType);
        for (DumStructuredField field : recordSchema.fields()) {
            TurMultiValue values = attributes.get(field.name());
            if (values == null || values.isEmpty()) {
                continue;
            }
            try {
                record.set(field.name(), new ArrayList<>(values), DumFieldProvenance.aem(field.name()));
            } catch (IllegalArgumentException e) {
                log.warn("AEM source '{}' id '{}' field '{}' skipped: {}", source.getName(), recordId,
                        field.name(), e.getMessage());
            }
        }
        return record.build();
    }

    private static Optional<String> firstValue(TurMultiValue values) {
        if (values == null) {
            return Optional.empty();
        }
        return values.stream().filter(StringUtils::isNotBlank).findFirst();
    }

    private synchronized DumAemSession session() {
        if (session == null) {
            session = sessionService.getDumAemSession(source, false);
        }
        return session;
    }
}
