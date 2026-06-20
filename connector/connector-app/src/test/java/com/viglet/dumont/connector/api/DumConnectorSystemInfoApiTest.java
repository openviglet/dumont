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

package com.viglet.dumont.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DumConnectorSystemInfoApiTest {

    private DumConnectorSystemInfoApi apiWithProvider(String provider) {
        DumConnectorSystemInfoApi api = new DumConnectorSystemInfoApi();
        ReflectionTestUtils.setField(api, "indexingProvider", provider);
        ReflectionTestUtils.setField(api, "turingUrl", "http://localhost:2700");
        ReflectionTestUtils.setField(api, "turingSolrEndpoint", "http://localhost:8983");
        ReflectionTestUtils.setField(api, "solrUrl", "http://localhost:8983/solr");
        ReflectionTestUtils.setField(api, "solrCollection", "dumont");
        ReflectionTestUtils.setField(api, "elasticsearchUrl", "http://localhost:9200");
        ReflectionTestUtils.setField(api, "elasticsearchIndex", "dumont");
        ReflectionTestUtils.setField(api, "elasticsearchUsername", "elastic");
        return api;
    }

    @Test
    void turingProviderReportsTuringEndpoints() {
        Map<String, Object> info = apiWithProvider("turing").getSystemInfo();

        assertThat(info).containsEntry("appName", "Viglet Dumont DEP").containsEntry("status", "UP");
        assertThat(info).containsKeys("javaVersion", "osName", "memory", "disk", "indexing");
        @SuppressWarnings("unchecked")
        Map<String, Object> indexing = (Map<String, Object>) info.get("indexing");
        assertThat(indexing).containsKey("turingUrl").containsKey("turingSolrEndpoint");
    }

    @Test
    void solrProviderReportsSolrEndpoints() {
        @SuppressWarnings("unchecked")
        Map<String, Object> indexing =
                (Map<String, Object>) apiWithProvider("solr").getSystemInfo().get("indexing");
        assertThat(indexing).containsKey("solrUrl").containsKey("solrCollection");
    }

    @Test
    void elasticsearchProviderReportsEsEndpoints() {
        @SuppressWarnings("unchecked")
        Map<String, Object> indexing = (Map<String, Object>) apiWithProvider("elasticsearch")
                .getSystemInfo().get("indexing");
        assertThat(indexing).containsKey("elasticsearchUrl").containsKey("elasticsearchIndex")
                .containsKey("elasticsearchUsername");
    }

    @Test
    void unknownProviderReportsOnlyProviderName() {
        @SuppressWarnings("unchecked")
        Map<String, Object> indexing =
                (Map<String, Object>) apiWithProvider("custom").getSystemInfo().get("indexing");
        assertThat(indexing).containsKey("provider").doesNotContainKey("turingUrl");
    }

    @Test
    void variablesIncludeSystemPropertiesAndEnv() {
        Map<String, String> variables = apiWithProvider("turing").getVariables();
        assertThat(variables).containsKey("java.version");
        assertThat(variables.keySet()).anyMatch(k -> k.startsWith("env."));
    }
}
