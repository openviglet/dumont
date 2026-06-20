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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

class DumAemQueryBuilderUtilsTest {

    private static DumAemConfiguration config() {
        return DumAemConfiguration.builder()
                .url("http://aem.invalid:4502")
                .rootPath("/content/wknd")
                .contentType("cq:Page")
                .build();
    }

    @Test
    void queryAllPathsExtractsJcrPathsFromHits() {
        String json = "{\"total\":2,\"hits\":[{\"jcr:path\":\"/content/wknd/a\"},"
                + "{\"jcr:path\":\"/content/wknd/b\"}]}";
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of(json));
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);

            List<String> paths = DumAemQueryBuilderUtils.queryAllPaths(config());

            assertThat(paths).containsExactly("/content/wknd/a", "/content/wknd/b");
        }
    }

    @Test
    void queryAllPathsExtractsFromPathKeyAndStringHits() {
        String json = "{\"total\":2,\"hits\":[{\"path\":\"/content/wknd/c\"},\"/content/wknd/d\"]}";
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of(json));
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);

            assertThat(DumAemQueryBuilderUtils.queryAllPaths(config()))
                    .containsExactlyInAnyOrder("/content/wknd/c", "/content/wknd/d");
        }
    }

    @Test
    void discoverPathsReturnsZeroWhenTotalIsZero() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of("{\"total\":0,\"hits\":[]}"));
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);

            long total = DumAemQueryBuilderUtils.discoverPaths(config(), paths -> { /* none */ });

            assertThat(total).isZero();
        }
    }

    @Test
    void discoverPathsReturnsZeroWhenNoResponse() {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.empty());

            assertThat(DumAemQueryBuilderUtils.queryAllPaths(config())).isEmpty();
        }
    }

    @Test
    void discoverPathsStreamsEveryPageWhenTotalExceedsPageSize() {
        // total=600 forces a second page (slim page size is 500): offset 0 then 500.
        String page1 = "{\"total\":600,\"hits\":[{\"jcr:path\":\"/content/wknd/a\"},\"/content/wknd/s\"]}";
        String page2 = "{\"total\":600,\"hits\":[{\"path\":\"/content/wknd/b\"}]}";
        List<String> collected = new java.util.ArrayList<>();

        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenReturn(Optional.of(page1)).thenReturn(Optional.of(page2));
            mocked.when(() -> DumAemCommonsUtils.isResponseBodyJSONObject(anyString()))
                    .thenReturn(true);

            long total = DumAemQueryBuilderUtils.discoverPaths(config(), collected::addAll);

            assertThat(total).isEqualTo(3);
            assertThat(collected).containsExactly("/content/wknd/a", "/content/wknd/s",
                    "/content/wknd/b");
        }
    }

    @Test
    void queryAllPathsReturnsEmptyWhenQueryThrows() throws Exception {
        try (MockedStatic<DumAemCommonsUtils> mocked = mockStatic(DumAemCommonsUtils.class)) {
            mocked.when(() -> DumAemCommonsUtils.getResponseBody(anyString(), any(), eq(false)))
                    .thenThrow(new java.io.IOException("boom"));

            assertThat(DumAemQueryBuilderUtils.queryAllPaths(config())).isEmpty();
        }
    }
}
