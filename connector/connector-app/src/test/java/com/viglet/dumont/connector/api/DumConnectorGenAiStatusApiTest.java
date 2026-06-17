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

package com.viglet.dumont.connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.api.DumConnectorGenAiStatusApi.GenAiStatus;

class DumConnectorGenAiStatusApiTest {

    private MockRestServiceServer server;
    private DumConnectorGenAiStatusApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://turing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        api = new DumConnectorGenAiStatusApi(builder.build());
    }

    private void expectChatEnabled(org.springframework.test.web.client.response.DefaultResponseCreator response) {
        server.expect(requestTo("http://turing.test/api/sn/news/chat/enabled"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(response);
    }

    @Test
    void reportsRagEnabledWhenTuringSaysSo() {
        expectChatEnabled(withSuccess(
                "{\"enabled\":true,\"reason\":\"NONE\",\"sessionCookieName\":\"sid\",\"sessionTtlSeconds\":3600}",
                MediaType.APPLICATION_JSON));

        GenAiStatus status = api.status("news");

        assertTrue(status.ragEnabled());
        assertEquals("NONE", status.reason());
        assertEquals("news", status.site());
        server.verify();
    }

    @Test
    void reportsRagDisabledWithTuringsDiagnosticReason() {
        expectChatEnabled(withSuccess(
                "{\"enabled\":false,\"reason\":\"RAG_DISABLED\",\"sessionCookieName\":\"sid\",\"sessionTtlSeconds\":3600}",
                MediaType.APPLICATION_JSON));

        GenAiStatus status = api.status("news");

        assertFalse(status.ragEnabled());
        assertEquals("RAG_DISABLED", status.reason());
    }

    @Test
    void degradesToUnreachableWhenTuringFails() {
        expectChatEnabled(withServerError());

        GenAiStatus status = api.status("news");

        // Never claim RAG is enabled when the answer is unknown.
        assertFalse(status.ragEnabled());
        assertEquals("UNREACHABLE", status.reason());
    }
}
