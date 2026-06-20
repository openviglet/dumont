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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorSourceInferenceApi.InferIndexRequest;
import com.viglet.dumont.connector.api.DumConnectorSourceInferenceApi.InferRequest;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

class DumConnectorSourceInferenceApiTest {

    private DumConnectorSourceInferenceApi api() {
        return new DumConnectorSourceInferenceApi(mock(RestClient.class),
                mock(DumTuringCsrfService.class), mock(DumConnectorContext.class),
                mock(DumCoverageHistoryStore.class), mock(DumProvenanceHistoryStore.class));
    }

    @Test
    void inferRejectsMissingUrl() {
        assertThat(api().infer(new InferRequest("  ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api().infer(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void indexRejectsIncompleteRequest() {
        assertThat(api().index(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api().index(new InferIndexRequest("http://x", "", null, null, null))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void previewRejectsIncompleteRequest() {
        assertThat(api().preview(null, 20).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api().preview(new InferIndexRequest("", "s", null, null, null), 20)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
