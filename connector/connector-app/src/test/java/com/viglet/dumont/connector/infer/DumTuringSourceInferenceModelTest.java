/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.viglet.dumont.connector.infer;

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

class DumTuringSourceInferenceModelTest {

    private static final String CSRF_JSON =
            "{\"token\":\"t-123\",\"headerName\":\"X-CSRF-TOKEN\"}";
    private static final String PROMPT = "classify the source structure";
    private static final String SAMPLE = "URL: https://uni.edu\nSignals: tables=1\n<table></table>";

    private MockRestServiceServer server;
    private DumTuringSourceInferenceModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://turing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        model = new DumTuringSourceInferenceModel(restClient, new DumTuringCsrfService(restClient));
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
    void returnsTheRawCompletionFromASuccessfulResponse() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":true,\"error\":null,\"content\":\"{\\\"shape\\\":\\\"TABULAR_LISTING\\\"}\",\"canRegenerate\":true}",
                MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("{\"shape\":\"TABULAR_LISTING\"}"), model.infer(PROMPT, SAMPLE));
        server.verify();
    }

    @Test
    void unsuccessfulResponseYieldsNoAnswer() {
        expectCsrf();
        expectSummary(withSuccess(
                "{\"success\":false,\"error\":\"no LLM configured\",\"content\":null,\"canRegenerate\":false}",
                MediaType.APPLICATION_JSON));

        assertTrue(model.infer(PROMPT, SAMPLE).isEmpty());
    }

    @Test
    void transportFailureYieldsNoAnswer() {
        expectCsrf();
        expectSummary(withServerError());

        assertTrue(model.infer(PROMPT, SAMPLE).isEmpty(),
                "an unreachable Turing degrades to an UNKNOWN draft, not an exception");
    }

    @Test
    void blankSampleSkipsTheCallEntirely() {
        assertTrue(model.infer(PROMPT, "  ").isEmpty());
        server.verify();
    }
}
