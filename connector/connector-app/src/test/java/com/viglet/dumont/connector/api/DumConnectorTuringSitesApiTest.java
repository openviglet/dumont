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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorTuringSitesApi.SitesUnavailable;

/** D342: "Turing is down" is never answered as "no sites". */
class DumConnectorTuringSitesApiTest {

    @Test
    void anUnreachableTuringIsA502NamingIt() {
        DumConnectorTuringSitesApi api = new DumConnectorTuringSitesApi(
                RestClient.builder().baseUrl("http://localhost:1").build());

        ResponseEntity<Object> response = api.listSites();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isInstanceOfSatisfying(SitesUnavailable.class,
                body -> assertThat(body.reason()).isEqualTo("unreachable"));
    }

    @Test
    void aRefusedKeyAndAMissingEndpointAreNamed() {
        assertThat(DumConnectorTuringSitesApi.unavailable(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null)))
                .extracting(SitesUnavailable::reason, SitesUnavailable::status)
                .containsExactly("unauthorized", 401);
        assertThat(DumConnectorTuringSitesApi.unavailable(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null)).reason())
                .isEqualTo("not_found");
        assertThat(DumConnectorTuringSitesApi.unavailable(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "", null, null, null)))
                .extracting(SitesUnavailable::reason, SitesUnavailable::status)
                .containsExactly("error", 500);
    }
}
