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

package com.viglet.dumont.connector.plugin.aem.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance;
import com.viglet.dumont.connector.plugin.aem.DumAemAttrProcess;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.service.DumAemContentDefinitionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemContentMappingService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemDependencyService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemObjectService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSessionService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemSourceService;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSource;
import com.viglet.dumont.connector.plugin.aem.source.DumAemStructuredSourceProvider;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Golden-fixture extraction parity harness (roadmap <strong>D30</strong>).
 *
 * <p>
 * The D21 conversion "replaces the loop" for AEM, but its parity with the legacy job pipeline was
 * only reviewed structurally — there is no live AEM in CI. This harness drives <em>one</em>
 * {@code infinity.json} (a {@code cq:Page}, a Content Fragment and a static file) through both the
 * structured {@link DumAemStructuredSource#extract(String)} + {@link DumAemPlugin#toJobItem} path and
 * the legacy {@link com.viglet.dumont.connector.plugin.aem.service.DumAemJobService} extraction, then
 * asserts the published {@link TurSNJobItem} has the <em>identical shape</em>: content attributes,
 * document id, {@code environment}, delta {@code checksum} and resolved locale.
 *
 * <p>
 * Both paths share the same field extraction ({@code DumAemService#getTargetAttrValueMap}) and delta /
 * locale resolution, so the harness locks the wiring around it — including the eligibility gate and
 * the {@code configureObjectDataPath} content-fragment / static-file routing the legacy
 * {@code prepareIndexObject} applies before extraction. The structured side adds only Dumont-reserved
 * attributes (the {@code dum_provenance} token, D16); those are excluded from the content-attribute
 * comparison. Any future divergence between the two extraction paths fails this test.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@DisplayName("AEM extraction parity harness (D30): structured extract(id) == legacy DumAemJobService")
class DumAemExtractionParityTest {

    private static final String ROOT_PATH = "/content/portal";
    private static final String SITE_NAME = "MySite";
    private static final String AUTHOR_SITE = "author-site";
    private static final Locale LOCALE = Locale.of("pt", "BR");
    private static final String LAST_MODIFIED = "Tue Jan 16 2024 14:30:00 GMT+0000";

    // Real collaborators — both paths run the same extraction / delta / locale code.
    private DumAemAttrProcess attrProcess;
    private DumAemService aemService;
    private DumAemContentDefinitionService contentDefinitionService;
    private DumAemObjectService objectService;
    private DumAemDependencyService dependencyService;
    private com.viglet.dumont.connector.plugin.aem.service.DumAemJobService jobService;

    private DumConnectorContext connectorContext;
    private DumAemSessionService sessionService;
    private DumAemPlugin plugin;

    @BeforeEach
    void setUp() {
        attrProcess = new DumAemAttrProcess();
        aemService = new DumAemService(attrProcess);
        contentDefinitionService =
                new DumAemContentDefinitionService(mock(DumAemContentMappingService.class));
        objectService = new DumAemObjectService();
        // Dependency harvest disabled: dependencies are not part of the compared TurSNJobItem shape
        // and enabling it would issue a model.json HTTP request.
        dependencyService = new DumAemDependencyService(contentDefinitionService, false);
        connectorContext = mock(DumConnectorContext.class);
        when(connectorContext.addJobItem(any())).thenReturn(true);
        jobService = new com.viglet.dumont.connector.plugin.aem.service.DumAemJobService(aemService,
                connectorContext, contentDefinitionService, objectService, dependencyService);
        sessionService = mock(DumAemSessionService.class);
        // Only DumAemPlugin#toJobItem is exercised; the provider / source services are unused there.
        plugin = new DumAemPlugin(connectorContext, mock(DumAemStructuredSourceProvider.class),
                mock(DumAemSourceService.class), sessionService, objectService, null, null, null,
                false, 1, 0L, 0.5);
    }

    @Test
    @DisplayName("cq:Page extracts to an identical job item on both paths")
    void pageParity() {
        assertParity("/content/portal/en/home", pageJson(), "cq:Page", null);
    }

    @Test
    @DisplayName("Content Fragment (configureObjectDataPath → data/master) extracts identically")
    void contentFragmentParity() {
        // 'detail' lives only under jcr:content/data/master — extracted only because the data path
        // is resolved onto the extracted object (the bug fix); identical on both paths.
        assertParity("/content/portal/fragments/intro", contentFragmentJson(), "dam:Asset",
                "contentFragment");
    }

    @Test
    @DisplayName("Static file (configureObjectDataPath → metadata) extracts identically")
    void staticFileParity() {
        // 'detail' lives only under jcr:content/metadata — extracted only because the data path is
        // resolved onto the extracted object (the bug fix); identical on both paths.
        assertParity("/content/portal/files/brochure.pdf", staticFileJson(), "dam:Asset",
                "static-file");
    }

    /**
     * Drive {@code path} through both extraction paths and assert the published {@link TurSNJobItem}
     * has the same shape. {@code contentType} / {@code subType} select the eligibility + dataPath
     * routing; {@code contentFragment} flags the CF case so the legacy {@code configureObjectDataPath}
     * resolves {@code data/master}.
     */
    private void assertParity(String path, JSONObject infinityJson, String contentType,
            String subType) {
        DumAemConfiguration config = config(contentType, subType);
        DumAemModel model = model(contentType, subType);
        DumAemSession session = session(config, model);
        DumAemSource source = source();

        lenient().when(sessionService.getDumAemSession(source, false)).thenReturn(session);

        try (MockedStatic<DumAemCommonsUtils> statics =
                mockStatic(DumAemCommonsUtils.class, CALLS_REAL_METHODS)) {
            statics.when(() -> DumAemCommonsUtils.getInfinityJson(eq(path), any(), eq(false)))
                    .thenReturn(Optional.of(infinityJson));

            TurSNJobItem legacyItem = legacyJobItem(session, path, infinityJson);
            TurSNJobItem structuredItem = structuredJobItem(source, session, path);

            assertSameShape(legacyItem, structuredItem);

            // A field whose value lives only in the content type's data sub-tree (data/master for a
            // CF, metadata for a static file) is extracted only because configureObjectDataPath
            // resolves that sub-tree onto the extracted object — the bug this fixes. A cq:Page has no
            // such sub-tree, so the field is absent.
            Object expectedDetail = "dam:Asset".equals(contentType)
                    ? ("contentFragment".equals(subType) ? "cf-detail" : "sf-detail")
                    : null;
            assertEquals(expectedDetail, structuredItem.getAttributes().get("detail"),
                    "data-path-sourced field extracted on the structured path");
        }
    }

    /** The legacy path: {@code prepareIndexObject} → eligibility + dataPath + author index job. */
    private TurSNJobItem legacyJobItem(DumAemSession session, String path, JSONObject infinityJson) {
        DumAemObjectGeneric generic =
                new DumAemObjectGeneric(path, infinityJson, DumAemEvent.INDEXING);
        jobService.prepareIndexObject(session, generic);

        ArgumentCaptor<DumJobItemWithSession> captor =
                ArgumentCaptor.forClass(DumJobItemWithSession.class);
        org.mockito.Mockito.verify(connectorContext).addJobItem(captor.capture());
        return captor.getValue().turSNJobItem();
    }

    /** The structured path: {@code extract(id)} → {@code DumAemPlugin#toJobItem}. */
    private TurSNJobItem structuredJobItem(DumAemSource source, DumAemSession session, String path) {
        DumAemStructuredSource structuredSource = new DumAemStructuredSource(source, DumAemEnv.AUTHOR,
                sessionService, objectService, aemService, null, contentDefinitionService,
                dependencyService, false);
        Optional<DumStructuredRecord> record = structuredSource.extract(path);
        assertTrue(record.isPresent(), "structured extract(id) yielded no record for " + path);
        return plugin.toJobItem(structuredSource, record.get());
    }

    /**
     * The published-shape contract D30 locks: content attributes (minus the structured-only
     * {@code dum_provenance} token), document id, environment, delta checksum and resolved locale.
     */
    private void assertSameShape(TurSNJobItem legacy, TurSNJobItem structured) {
        assertEquals(TurSNJobAction.CREATE, legacy.getTurSNJobAction());
        assertEquals(legacy.getTurSNJobAction(), structured.getTurSNJobAction());

        assertEquals("AUTHOR", legacy.getEnvironment(), "legacy environment");
        assertEquals(legacy.getEnvironment(), structured.getEnvironment(), "environment parity");

        assertNotNull(legacy.getChecksum(), "legacy checksum");
        assertEquals(legacy.getChecksum(), structured.getChecksum(), "delta checksum parity");

        assertEquals(LOCALE, legacy.getLocale(), "legacy locale");
        assertEquals(legacy.getLocale(), structured.getLocale(), "resolved locale parity");

        assertNotNull(legacy.getId(), "legacy document id");
        assertEquals(legacy.getId(), structured.getId(), "document id parity");

        Map<String, Object> legacyAttrs = new HashMap<>(legacy.getAttributes());
        Map<String, Object> structuredAttrs = new HashMap<>(structured.getAttributes());
        // The structured path adds the reserved provenance token (D16); legacy has no equivalent.
        assertTrue(structuredAttrs.containsKey(DumProvenance.FIELD),
                "structured path should emit the dum_provenance token");
        structuredAttrs.remove(DumProvenance.FIELD);

        assertEquals(legacyAttrs, structuredAttrs, "content-attribute parity");
        // Sanity: the comparison is over real, non-empty content (not two empty maps).
        assertFalse(legacyAttrs.isEmpty(), "expected extracted content attributes");
        assertEquals("MySite", structuredAttrs.get("site"), "site attribute stamped on both paths");
        assertEquals(List.of("a", "b"), structuredAttrs.get("tags"), "multi-valued field parity");
        // configureObjectDataPath routing parity: a property living only under the CF/static
        // data/master | metadata sub-tree never leaks into either path's published attributes.
        assertFalse(legacyAttrs.containsKey("nestedOnly"), "legacy must not leak nested-only field");
        assertFalse(structuredAttrs.containsKey("nestedOnly"),
                "structured must not leak nested-only field");
    }

    // --- Fixtures -----------------------------------------------------------------------------

    private DumAemConfiguration config(String contentType, String subType) {
        return DumAemConfiguration.builder()
                .url("http://author.example")
                .rootPath(ROOT_PATH)
                .contentType(contentType)
                .subType(subType)
                .siteName(SITE_NAME)
                .defaultLocale(LOCALE)
                .providerName("AEM")
                .author(true)
                .publish(false)
                .authorSNSite(AUTHOR_SITE)
                .publishSNSite("publish-site")
                .build();
    }

    private DumAemModel model(String type, String subType) {
        return DumAemModel.builder()
                .type(type)
                .subType(subType)
                .targetAttrs(List.of(
                        target("id", "id"),
                        target("title", "jcr:title"),
                        target("tags", "tags"),
                        target("detail", "detail")))
                .build();
    }

    private DumAemTargetAttr target(String name, String sourceName) {
        DumAemTargetAttr targetAttr = DumAemTargetAttr.builder()
                .sourceAttrs(List.of(DumAemSourceAttr.builder().name(sourceName).build()))
                .build();
        targetAttr.setName(name);
        return targetAttr;
    }

    private DumAemSession session(DumAemConfiguration config, DumAemModel model) {
        return DumAemSession.builder()
                .configuration(config)
                .model(model)
                .contentMapping(DumAemContentMapping.builder().build())
                .event(DumAemEvent.INDEXING)
                .standalone(true)
                .providerName("AEM")
                .attributeSpecs(List.of(
                        spec("id", TurSEFieldType.STRING, false),
                        spec("title", TurSEFieldType.TEXT, false),
                        spec("tags", TurSEFieldType.STRING, true),
                        spec("detail", TurSEFieldType.TEXT, false)))
                .build();
    }

    private TurSNAttributeSpec spec(String name, TurSEFieldType type, boolean multiValued) {
        return TurSNAttributeSpec.builder().name(name).type(type).multiValued(multiValued).build();
    }

    private DumAemSource source() {
        return DumAemSource.builder()
                .name("portal")
                .defaultLocale(LOCALE)
                .attributeSpecifications(Set.of(
                        attrSpec("id", TurSEFieldType.STRING, false, false),
                        attrSpec("title", TurSEFieldType.TEXT, true, false),
                        attrSpec("tags", TurSEFieldType.STRING, false, true),
                        attrSpec("detail", TurSEFieldType.TEXT, false, false)))
                .build();
    }

    private DumAemAttributeSpecification attrSpec(String name, TurSEFieldType type, boolean mandatory,
            boolean multiValued) {
        DumAemAttributeSpecification spec = DumAemAttributeSpecification.builder()
                .type(type)
                .mandatory(mandatory)
                .multiValued(multiValued)
                .build();
        spec.setName(name);
        return spec;
    }

    /** Top-level {@code jcr:content} properties — read directly by the field extractor on both paths. */
    private JSONObject contentNode() {
        JSONObject content = new JSONObject();
        content.put("jcr:title", "Welcome");
        content.put("id", "doc-1");
        content.put("tags", new JSONArray(List.of("a", "b")));
        content.put("cq:lastModified", LAST_MODIFIED);
        return content;
    }

    private JSONObject pageJson() {
        JSONObject node = new JSONObject();
        node.put("jcr:primaryType", "cq:Page");
        node.put("jcr:content", contentNode());
        return node;
    }

    private JSONObject contentFragmentJson() {
        JSONObject content = contentNode();
        content.put("contentFragment", true);
        // CF attributes live under jcr:content/data/master — 'detail' is readable only once the data
        // path is resolved onto the extracted object; 'nestedOnly' is unmapped and must never leak.
        JSONObject master = new JSONObject();
        master.put("detail", "cf-detail");
        master.put("nestedOnly", "should-not-leak");
        JSONObject data = new JSONObject();
        data.put("master", master);
        content.put("data", data);
        JSONObject node = new JSONObject();
        node.put("jcr:primaryType", "dam:Asset");
        node.put("jcr:content", content);
        return node;
    }

    private JSONObject staticFileJson() {
        JSONObject content = contentNode();
        // Static-file attributes live under jcr:content/metadata — 'detail' is readable only once the
        // data path is resolved onto the extracted object; 'nestedOnly' is unmapped and must never leak.
        JSONObject metadata = new JSONObject();
        metadata.put("detail", "sf-detail");
        metadata.put("nestedOnly", "should-not-leak");
        content.put("metadata", metadata);
        JSONObject node = new JSONObject();
        node.put("jcr:primaryType", "dam:Asset");
        node.put("jcr:content", content);
        return node;
    }
}
