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

package com.viglet.dumont.connector.plugin.aem.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService;
import com.viglet.dumont.connector.plugin.aem.service.DumAemExtractorScannerService.ExtractorClass;

class DumAemSourceWizardApiTest {

    private final DumAemExtractorScannerService scanner =
            mock(DumAemExtractorScannerService.class);
    private final DumAemSourceWizardApi api = new DumAemSourceWizardApi(scanner);

    @Test
    void listExtractorsDelegatesToScanner() {
        when(scanner.list("attribute", "title")).thenReturn(List.of());
        assertThat(api.listExtractors("attribute", "title")).isEmpty();
    }

    @Test
    void testConnectionReturnsFailureWhenUnreachable() {
        DumAemConnectionRequest request = DumAemConnectionRequest.builder()
                .endpoint("http://localhost:1").username("admin").password("admin").build();

        ResponseEntity<Map<String, Object>> response = api.testConnection(request);

        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void browseReturnsFailureWhenUnreachable() {
        DumAemBrowseRequest request = DumAemBrowseRequest.builder()
                .endpoint("http://localhost:1").username("admin").password("admin")
                .path("/content").build();

        ResponseEntity<Map<String, Object>> response = api.browse(request);

        assertThat(response.getBody()).containsEntry("success", false);
    }
}
