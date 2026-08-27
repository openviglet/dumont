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

package com.viglet.dumont.connector.plugin.aem.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;

/** Covers the IMS token exchange behind the journal consumer (roadmap D62). */
@DisplayName("DumAemImsTokenService Tests (D62)")
class DumAemImsTokenServiceTest {

    private static final String IMS = "https://ims.invalid/ims/token/v3";

    private record Fixture(DumAemImsTokenService service, MockRestServiceServer server) {
    }

    private static Fixture fixture() {
        DumAemJournalProperties properties = new DumAemJournalProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setImsUrl(IMS);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new DumAemImsTokenService(properties, builder.build()), server);
    }

    @Test
    @DisplayName("a long-lived token is exchanged once and served from cache")
    void cachesALongLivedToken() {
        Fixture fixture = fixture();
        fixture.server().expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(IMS))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"t-1\",\"expires_in\":86400}",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().accessToken()).contains("t-1");
        assertThat(fixture.service().accessToken()).contains("t-1");
        fixture.server().verify();
    }

    @Test
    @DisplayName("invalidate() forces the next call to re-exchange")
    void invalidateForcesReExchange() {
        Fixture fixture = fixture();
        fixture.server().expect(ExpectedCount.twice(), MockRestRequestMatchers.requestTo(IMS))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"t-1\",\"expires_in\":86400}",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().accessToken()).contains("t-1");
        fixture.service().invalidate();
        assertThat(fixture.service().accessToken()).contains("t-1");
        fixture.server().verify();
    }

    @Test
    @DisplayName("a token expiring inside the refresh margin is used but not cached")
    void aShortLivedTokenIsNotCached() {
        Fixture fixture = fixture();
        fixture.server().expect(ExpectedCount.twice(), MockRestRequestMatchers.requestTo(IMS))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"t-short\",\"expires_in\":60}",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().accessToken()).contains("t-short");
        assertThat(fixture.service().accessToken()).contains("t-short");
        fixture.server().verify();
    }

    @Test
    @DisplayName("a failed exchange is empty, so the caller skips the poll instead of guessing")
    void aFailedExchangeIsEmpty() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(IMS))
                .andRespond(MockRestResponseCreators.withServerError());

        assertThat(fixture.service().accessToken()).isEmpty();
    }

    @Test
    @DisplayName("a response without access_token is empty rather than a blank bearer")
    void aResponseWithoutTokenIsEmpty() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(IMS))
                .andRespond(MockRestResponseCreators.withSuccess("{\"expires_in\":86400}",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().accessToken()).isEmpty();
    }
}
