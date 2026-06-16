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

package com.viglet.dumont.connector.enrich;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.config.DumTuringCsrfService;

class DumTuringClassifierTest {

    private static final String CSRF_JSON =
            "{\"token\":\"t-123\",\"headerName\":\"X-CSRF-TOKEN\"}";

    private RestClient restClient;
    private MockRestServiceServer server;
    private DumTuringClassifier classifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://turing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        classifier = new DumTuringClassifier(restClient, new DumTuringCsrfService(restClient),
                "Classify into one of [Exact Sciences, Humanities] or reply NONE.", "dumont-area");
    }

    private void expectCsrf() {
        server.expect(requestTo("http://turing.test/api/csrf"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CSRF_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectSummary(org.springframework.test.web.client.response.DefaultResponseCreator response) {
        server.expect(requestTo("http://turing.test/api/v2/summary"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(response);
    }

    @Test
    void returnsTheLabelFromASuccessfulResponse() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":true,\"error\":null,\"content\":\"Exact Sciences\",\"canRegenerate\":true}",
                MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("Exact Sciences"), classifier.classify("intro calculus"));
        server.verify();
    }

    @Test
    void trimsWhitespaceAroundTheLabel() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":true,\"error\":null,\"content\":\"  Humanities \\n\",\"canRegenerate\":true}",
                MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("Humanities"), classifier.classify("history survey"));
    }

    @Test
    void treatsNoneAsNoLabel() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":true,\"error\":null,\"content\":\"NONE\",\"canRegenerate\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(classifier.classify("ambiguous").isEmpty(),
                "a NONE answer omits the field, never fabricates one");
    }

    @Test
    void unsuccessfulResponseYieldsNoLabel() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":false,\"error\":\"no LLM configured\",\"content\":null,\"canRegenerate\":false}",
                MediaType.APPLICATION_JSON));

        assertTrue(classifier.classify("intro calculus").isEmpty());
    }

    @Test
    void transportFailureYieldsNoLabel() {
        expectCsrf();
        expectSummary(withServerError());

        assertTrue(classifier.classify("intro calculus").isEmpty(),
                "an unreachable Turing degrades to omission, not an exception");
    }

    @Test
    void blankInputSkipsTheCallEntirely() {
        // No server expectations -> any HTTP call would fail verification.
        assertTrue(classifier.classify("   ").isEmpty());
        server.verify();
    }
}
