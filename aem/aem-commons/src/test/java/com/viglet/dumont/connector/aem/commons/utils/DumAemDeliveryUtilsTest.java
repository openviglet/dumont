/*
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

package com.viglet.dumont.connector.aem.commons.utils;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_MODIFIED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATED;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_LAST_REPLICATION_ACTION_PUBLISH;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_TEMPLATE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemFetchStrategy;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.delivery.DumAemPageDelivery;

/** Covers the publish-tier fetch strategy (roadmap D61). */
class DumAemDeliveryUtilsTest {

    private static DumAemConfiguration config() {
        return DumAemConfiguration.builder()
                .url("http://author.invalid:4502")
                .publishURLPrefix("http://publish.invalid:4503")
                .deliveryURLPrefix("https://delivery-p1-e1.adobeaemcloud.com")
                .rootPath("/content/wknd/en")
                .contentType(CQ_PAGE)
                .fetchStrategy(DumAemFetchStrategy.DELIVERY)
                .build();
    }

    @Test
    void deliveryPrefixPrefersDeliveryThenPublishThenSourceUrl() {
        assertThat(DumAemDeliveryUtils.deliveryUrlPrefix(config()))
                .isEqualTo("https://delivery-p1-e1.adobeaemcloud.com");
        assertThat(DumAemDeliveryUtils.deliveryUrlPrefix(
                config().toBuilder().deliveryURLPrefix(null).build()))
                .isEqualTo("http://publish.invalid:4503");
        assertThat(DumAemDeliveryUtils.deliveryUrlPrefix(
                config().toBuilder().deliveryURLPrefix(null).publishURLPrefix(null).build()))
                .isEqualTo("http://author.invalid:4502");
    }

    @Test
    void contentNodeIsParsedByTheSameObjectTheSlingPathProduces() {
        JSONObject modelJson = new JSONObject()
                .put("title", "Adventures")
                .put("templateName", "wknd/templates/page-content")
                .put("lastModifiedDate", 1_700_000_000_000L);

        DumAemObjectGeneric generic = new DumAemObjectGeneric("/content/wknd/en",
                DumAemPageDelivery.toContentNode("/content/wknd/en", modelJson));

        assertThat(generic.getType()).isEqualTo(CQ_PAGE);
        assertThat(generic.getTitle()).isEqualTo("Adventures");
        assertThat(generic.getTemplate()).isEqualTo("wknd/templates/page-content");
        assertThat(generic.getLastModified()).isNotNull();
        assertThat(generic.getLastModified().getTimeInMillis()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void deliveredContentIsStampedDeliveredBecauseTheDeliveryTierOnlyServesPublished() {
        JSONObject node = DumAemPageDelivery.toContentNode("/content/wknd/en", new JSONObject());
        JSONObject jcrContent = node.getJSONObject(JCR_CONTENT);

        assertThat(jcrContent.getString(CQ_LAST_REPLICATION_ACTION)).isEqualTo("Activate");
        assertThat(jcrContent.getString(CQ_LAST_REPLICATION_ACTION_PUBLISH)).isEqualTo("Activate");
        assertThat(new DumAemObjectGeneric("/content/wknd/en", node, null).isDelivered()).isTrue();
    }

    @Test
    void aModelWithoutADateOrTemplateOmitsThemRatherThanInventingAValue() {
        JSONObject jcrContent = DumAemPageDelivery
                .toContentNode("/content/wknd/en", new JSONObject().put("title", "Adventures"))
                .getJSONObject(JCR_CONTENT);

        assertThat(jcrContent.has(CQ_TEMPLATE)).isFalse();
        assertThat(jcrContent.has(CQ_LAST_MODIFIED)).isFalse();
        assertThat(jcrContent.has(CQ_LAST_REPLICATED)).isFalse();
    }

    @Test
    void childrenBecomeMarkerNodesUnderTheirRelativeNameSoTheExistingTraversalKeepsWalking() {
        JSONObject modelJson = new JSONObject().put(":children", new JSONObject()
                .put("/content/wknd/en/adventures", new JSONObject())
                .put("/content/wknd/en/adventures/surf-camp", new JSONObject())
                .put("/content/wknd/en/magazine", new JSONObject())
                // Not below the page — a model echoing its own or a sibling's path.
                .put("/content/wknd/en", new JSONObject())
                .put("/content/wknd/fr/magazine", new JSONObject()));

        JSONObject node = DumAemPageDelivery.toContentNode("/content/wknd/en", modelJson);

        assertThat(node.keySet()).contains("adventures", "magazine");
        assertThat(node.keySet()).doesNotContain("surf-camp", "fr");
        assertThat(node.getJSONObject("adventures").getString("jcr:primaryType"))
                .isEqualTo(CQ_PAGE);
    }

    @Test
    void aHierarchyRootIsDiscoveredInOneRequest() {
        String root = "{\":children\":{"
                + "\"/content/wknd/en/adventures\":{},"
                + "\"/content/wknd/en/adventures/surf-camp\":{},"
                + "\"/content/wknd/en/magazine\":{}}}";
        try (MockedStatic<DumAemCommonsUtils> mocked = stubResponses(url -> root)) {
            List<String> discovered = DumAemDeliveryUtils.queryAllPaths(config());

            assertThat(discovered).containsExactlyInAnyOrder(
                    "/content/wknd/en/adventures",
                    "/content/wknd/en/adventures/surf-camp",
                    "/content/wknd/en/magazine");
            mocked.verify(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)));
        }
    }

    @Test
    void aModelListingDirectChildrenOnlyIsDescendedPerChild() {
        try (MockedStatic<DumAemCommonsUtils> ignored = stubResponses(url -> {
            if (url.contains("/content/wknd/en.model.json")) {
                return "{\":children\":{\"/content/wknd/en/adventures\":{}}}";
            }
            if (url.contains("/content/wknd/en/adventures.model.json")) {
                return "{\":children\":{\"/content/wknd/en/adventures/surf-camp\":{}}}";
            }
            return "{}";
        })) {
            assertThat(DumAemDeliveryUtils.queryAllPaths(config())).containsExactly(
                    "/content/wknd/en/adventures",
                    "/content/wknd/en/adventures/surf-camp");
        }
    }

    @Test
    void aSelfReferencingModelDoesNotLoop() {
        try (MockedStatic<DumAemCommonsUtils> ignored = stubResponses(
                url -> "{\":children\":{\"/content/wknd/en/adventures\":{}}}")) {
            List<String> discovered = new ArrayList<>();
            long total = DumAemDeliveryUtils.discoverPaths(config(), "/content/wknd/en",
                    discovered::addAll);

            assertThat(total).isEqualTo(1);
            assertThat(discovered).containsExactly("/content/wknd/en/adventures");
        }
    }

    @Test
    void anUnreachableRootDiscoversNothing() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.empty());
            assertThat(DumAemDeliveryUtils.queryAllPaths(config())).isEmpty();
        }
    }

    @Test
    void anUnknownStrategyNameFallsBackToSlingRatherThanStoppingIndexing() {
        assertThat(DumAemFetchStrategy.from(null)).isEqualTo(DumAemFetchStrategy.SLING);
        assertThat(DumAemFetchStrategy.from("  ")).isEqualTo(DumAemFetchStrategy.SLING);
        assertThat(DumAemFetchStrategy.from("nonsense")).isEqualTo(DumAemFetchStrategy.SLING);
        assertThat(DumAemFetchStrategy.from(" delivery "))
                .isEqualTo(DumAemFetchStrategy.DELIVERY);
    }

    /** Stubs the shared HTTP seam, answering per requested URL. */
    private static MockedStatic<DumAemCommonsUtils> stubResponses(
            java.util.function.Function<String, String> bodyByUrl) {
        MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class);
        mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                .thenAnswer(invocation -> Optional
                        .of(bodyByUrl.apply(invocation.getArgument(0, String.class))));
        mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                .thenReturn(true);
        return mocked;
    }
}
