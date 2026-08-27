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

package com.viglet.dumont.connector.aem.commons.delivery;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CONTENT_FRAGMENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DAM_ASSET;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATA_FOLDER;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.JCR_CONTENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemFetchStrategy;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.utils.DumAemDeliveryUtils;

/** The two delivery surfaces D61 left, and the routing between all three (roadmap D67). */
@DisplayName("Delivery surfaces Tests (D67)")
class DumAemDeliverySurfaceTest {

    private static DumAemConfiguration.DumAemConfigurationBuilder base() {
        return DumAemConfiguration.builder()
                .url("http://author.invalid:4502")
                .deliveryURLPrefix("https://delivery-p1-e1.adobeaemcloud.com")
                .rootPath("/content/dam/wknd")
                .fetchStrategy(DumAemFetchStrategy.DELIVERY);
    }

    private static DumAemConfiguration fragmentSource() {
        return base().contentType(DAM_ASSET).subType(CONTENT_FRAGMENT).build();
    }

    private static DumAemConfiguration assetSource() {
        return base().contentType(DAM_ASSET).subType("static-file").build();
    }

    private static DumAemConfiguration pageSource() {
        return base().contentType(CQ_PAGE).rootPath("/content/wknd/en").build();
    }

    // --- Routing --------------------------------------------------------------------------------

    @Test
    @DisplayName("the surface follows the source's own content type, with no new setting")
    void theSurfaceFollowsTheContentType() {
        assertThat(DumAemDeliveryUtils.surfaceFor(fragmentSource()).getName())
                .isEqualTo("content-fragment");
        assertThat(DumAemDeliveryUtils.surfaceFor(assetSource()).getName()).isEqualTo("asset");
        assertThat(DumAemDeliveryUtils.surfaceFor(pageSource()).getName()).isEqualTo("page");
        // An asset source with no sub-type is still an asset source, not a fragment one.
        assertThat(DumAemDeliveryUtils.surfaceFor(base().contentType(DAM_ASSET).build()).getName())
                .isEqualTo("asset");
    }

    // --- Content Fragments ----------------------------------------------------------------------

    @Test
    @DisplayName("a fragment lands where the existing extraction already looks for it")
    void aFragmentIsTranslatedToTheDataMasterShape() {
        JSONObject fragment = new JSONObject()
                .put("path", "/content/dam/wknd/en/adventure")
                .put("title", "Surf Camp")
                .put("modelId", "wknd/models/adventure")
                .put("modified", new JSONObject().put("at", "2026-08-27T12:00:00Z"))
                .put("fields", new JSONObject().put("headline", "Ride it").put("price", 1200));

        JSONObject node = DumAemContentFragmentDelivery.toContentNode(fragment);
        JSONObject jcrContent = node.getJSONObject(JCR_CONTENT);
        JSONObject data = jcrContent.getJSONObject(DATA_FOLDER);

        assertThat(node.getString("jcr:primaryType")).isEqualTo(DAM_ASSET);
        assertThat(jcrContent.getBoolean(CONTENT_FRAGMENT)).isTrue();
        assertThat(data.getString("cq:model")).isEqualTo("wknd/models/adventure");
        // The field values go under data/master, which is the address setDataPath("data/master")
        // reads — so an extension written against an author-indexed fragment needs no change.
        assertThat(data.getJSONObject("master").getString("headline")).isEqualTo("Ride it");
        assertThat(data.getJSONObject("master").getInt("price")).isEqualTo(1200);

        DumAemObjectGeneric generic =
                new DumAemObjectGeneric("/content/dam/wknd/en/adventure", node);
        assertThat(generic.getType()).isEqualTo(DAM_ASSET);
        assertThat(generic.isContentFragment()).isTrue();
        assertThat(generic.isDelivered()).isTrue();
        assertThat(generic.getModel()).isEqualTo("wknd/models/adventure");
        assertThat(generic.getLastModified()).isNotNull();
    }

    @Test
    @DisplayName("a fragment with no date keeps no date, rather than gaining today's")
    void aFragmentWithoutADateLeavesItUnset() {
        JSONObject jcrContent = DumAemContentFragmentDelivery
                .toContentNode(new JSONObject().put("path", "/content/dam/wknd/a"))
                .getJSONObject(JCR_CONTENT);

        assertThat(jcrContent.has("cq:lastModified")).isFalse();
    }

    @Test
    @DisplayName("fragment discovery follows the cursor and stops when it runs out")
    void fragmentDiscoveryFollowsTheCursor() {
        try (MockedStatic<DumAemCommonsUtils> ignored = stub(url -> url.contains("cursor=c2")
                ? """
                  {"items":[{"path":"/content/dam/wknd/c"}]}"""
                : """
                  {"items":[{"path":"/content/dam/wknd/a"},{"path":"/content/dam/wknd/b"}],
                   "cursor":"c2"}""")) {
            List<String> discovered = new ArrayList<>();
            long total = new DumAemContentFragmentDelivery()
                    .discoverPaths(fragmentSource(), "/content/dam/wknd", discovered::addAll);

            assertThat(total).isEqualTo(3);
            assertThat(discovered).containsExactly("/content/dam/wknd/a", "/content/dam/wknd/b",
                    "/content/dam/wknd/c");
        }
    }

    @Test
    @DisplayName("a listing item outside the root is not discovered")
    void aFragmentOutsideTheRootIsDropped() {
        JSONObject response = new JSONObject(
                """
                {"items":[{"path":"/content/dam/wknd/a"},{"path":"/content/dam/other/b"}]}""");

        assertThat(DumAemContentFragmentDelivery.fragmentPaths(response, "/content/dam/wknd"))
                .containsExactly("/content/dam/wknd/a");
    }

    // --- Assets ---------------------------------------------------------------------------------

    @Test
    @DisplayName("an asset's metadata lands under jcr:content/metadata")
    void anAssetIsTranslatedToTheMetadataShape() {
        JSONObject asset = new JSONObject()
                .put("path", "/content/dam/wknd/logo.png")
                .put("dc:title", "Logo")
                .put("repo:modifyDate", "2026-08-27T12:00:00Z")
                .put("metadata", new JSONObject().put("dc:description", "The mark"));

        JSONObject node = DumAemAssetDelivery.toContentNode(asset);
        JSONObject jcrContent = node.getJSONObject(JCR_CONTENT);

        assertThat(node.getString("jcr:primaryType")).isEqualTo(DAM_ASSET);
        assertThat(jcrContent.getString("jcr:title")).isEqualTo("Logo");
        assertThat(jcrContent.getJSONObject(METADATA).getString("dc:description"))
                .isEqualTo("The mark");
        assertThat(new DumAemObjectGeneric("/content/dam/wknd/logo.png", node).isDelivered())
                .isTrue();
    }

    @Test
    @DisplayName("the asset reads tolerate the field spellings this API has shipped under")
    void assetFieldSpellingsAreTolerated() {
        // repo:path instead of path, name instead of a title, a flat modified string.
        JSONObject response = new JSONObject(
                """
                {"results":[{"repo:path":"/content/dam/wknd/a.png","name":"a.png",
                 "modified":"2026-08-27T12:00:00Z"}]}""");

        assertThat(DumAemAssetDelivery.assetPaths(response, "/content/dam/wknd"))
                .containsExactly("/content/dam/wknd/a.png");

        JSONObject node = DumAemAssetDelivery.toContentNode(response.getJSONArray("results")
                .getJSONObject(0));
        assertThat(node.getJSONObject(JCR_CONTENT).getString("jcr:title")).isEqualTo("a.png");
        assertThat(node.getJSONObject(JCR_CONTENT).has("cq:lastModified")).isTrue();
    }

    @Test
    @DisplayName("an unreadable asset response yields nothing, never an invented node")
    void anUnreadableAssetResponseYieldsNothing() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.postResponseBody(anyString(), anyString(), any()))
                    .thenReturn(Optional.empty());

            assertThat(new DumAemAssetDelivery()
                    .getContentNode("/content/dam/wknd/a.png", assetSource())).isEmpty();
            assertThat(new DumAemAssetDelivery().queryAllPaths(assetSource())).isEmpty();
        }
    }

    @Test
    @DisplayName("asset discovery posts its query and follows the cursor")
    void assetDiscoveryPostsAndPages() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);
            mocked.when(() -> DumAemCommonsUtils.postResponseBody(anyString(), anyString(), any()))
                    .thenAnswer(call -> {
                        String body = call.getArgument(1, String.class);
                        return Optional.of(body.contains("c2")
                                ? "{\"items\":[{\"path\":\"/content/dam/wknd/b.png\"}]}"
                                : "{\"items\":[{\"path\":\"/content/dam/wknd/a.png\"}],"
                                        + "\"cursor\":\"c2\"}");
                    });

            assertThat(new DumAemAssetDelivery().queryAllPaths(assetSource()))
                    .containsExactly("/content/dam/wknd/a.png", "/content/dam/wknd/b.png");
        }
    }

    @Test
    @DisplayName("a configured search path replaces the default mount")
    void aConfiguredSearchPathIsUsed() {
        DumAemConfiguration configured = assetSource().toBuilder()
                .assetSearchPath("/custom/assets/query").build();
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);
            mocked.when(() -> DumAemCommonsUtils.postResponseBody(anyString(), anyString(), any()))
                    .thenReturn(Optional.of("{\"items\":[]}"));

            new DumAemAssetDelivery().queryAllPaths(configured);

            mocked.verify(() -> DumAemCommonsUtils.postResponseBody(
                    eq("https://delivery-p1-e1.adobeaemcloud.com/custom/assets/query"),
                    anyString(), any()));
        }
    }

    /** Stubs the shared GET seam, answering per requested URL. */
    private static MockedStatic<DumAemCommonsUtils> stub(Function<String, String> bodyByUrl) {
        MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class);
        mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                .thenAnswer(call -> Optional.of(bodyByUrl.apply(call.getArgument(0, String.class))));
        mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                .thenReturn(true);
        return mocked;
    }
}
