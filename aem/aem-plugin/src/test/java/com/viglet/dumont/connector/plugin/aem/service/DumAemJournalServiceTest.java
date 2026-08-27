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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.plugin.aem.DumAemPluginProcess;
import com.viglet.dumont.connector.plugin.aem.api.DumAemPathList;
import com.viglet.dumont.connector.plugin.aem.conf.DumAemJournalProperties;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginSystem;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginSystemRepository;

/** Covers the Adobe I/O journal consumer and its cursor (roadmap D62). */
@DisplayName("DumAemJournalService Tests (D62)")
class DumAemJournalServiceTest {

    private static final String SOURCE = "wknd";
    private static final String JOURNAL = "https://events.invalid/events/organizations/1/2/3";

    private DumAemJournalProperties properties;
    private DumAemImsTokenService tokenService;
    private DumAemPluginProcess process;
    private DumAemPluginSystemRepository systemRepository;
    private Map<String, DumAemPluginSystem> rows;

    @BeforeEach
    void setUp() {
        properties = new DumAemJournalProperties();
        properties.setEnabled(true);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setBatchSize(2);
        properties.setSources(Map.of(SOURCE, JOURNAL));

        tokenService = mock(DumAemImsTokenService.class);
        when(tokenService.accessToken()).thenReturn(Optional.of("token-123"));

        process = mock(DumAemPluginProcess.class);
        rows = new HashMap<>();
        systemRepository = mock(DumAemPluginSystemRepository.class);
        when(systemRepository.findByConfig(any()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0, String.class))));
        when(systemRepository.save(any(DumAemPluginSystem.class))).thenAnswer(call -> {
            DumAemPluginSystem row = call.getArgument(0);
            rows.put(row.getConfig(), row);
            return row;
        });
    }

    private record Fixture(DumAemJournalService service, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DumAemJournalService service = new DumAemJournalService(properties, tokenService,
                new DumAemEventDispatcher(process), systemRepository, builder.build());
        return new Fixture(service, server);
    }

    private static String page(String lastPosition, String... paths) {
        StringBuilder events = new StringBuilder();
        for (String path : paths) {
            if (!events.isEmpty()) {
                events.append(',');
            }
            events.append("""
                    {"position":"p-%s","event":{"type":"aem.sites.page.published",
                     "data":{"resource_path":"%s"}}}""".formatted(path.hashCode(), path));
        }
        return """
                {"events":[%s],"_page":{"last":"%s","count":%d}}"""
                .formatted(events, lastPosition, paths.length);
    }

    private static String emptyPage() {
        return "{\"events\":[],\"_page\":{\"count\":0}}";
    }

    @Test
    @DisplayName("dispatches each pulled event and persists the page's last position")
    void dispatchesAndAdvancesCursor() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(
                JOURNAL + "?limit=2"))
                .andExpect(MockRestRequestMatchers.header("x-api-key", "client-id"))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer token-123"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        page("pos-2", "/content/wknd/en", "/content/wknd/fr"),
                        MediaType.APPLICATION_JSON));
        fixture.server().expect(MockRestRequestMatchers.requestTo(
                JOURNAL + "?limit=2&since=pos-2"))
                .andRespond(MockRestResponseCreators.withSuccess(emptyPage(),
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isEqualTo(2);
        verify(process, org.mockito.Mockito.times(2))
                .sentToIndexStandaloneAsync(eq(SOURCE), any(DumAemPathList.class));
        assertThat(fixture.service().cursor(SOURCE)).contains("pos-2");
        fixture.server().verify();
    }

    @Test
    @DisplayName("resumes from the stored position rather than replaying the whole journal")
    void resumesFromStoredCursor() {
        rows.put(DumAemJournalService.cursorKey(SOURCE),
                new DumAemPluginSystem(DumAemJournalService.cursorKey(SOURCE), "pos-7"));
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(
                JOURNAL + "?limit=2&since=pos-7"))
                .andRespond(MockRestResponseCreators.withSuccess(emptyPage(),
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isZero();
        fixture.server().verify();
    }

    @Test
    @DisplayName("a failed page leaves the cursor alone, so the events replay next poll")
    void failedPageDoesNotAdvanceCursor() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(JOURNAL + "?limit=2"))
                .andRespond(MockRestResponseCreators.withServerError());

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isZero();
        assertThat(fixture.service().cursor(SOURCE)).isEmpty();
        verify(tokenService).invalidate();
        fixture.server().verify();
    }

    @Test
    @DisplayName("a page without a position is not committed, so it is re-read rather than skipped")
    void pageWithoutPositionIsNotCommitted() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(JOURNAL + "?limit=2"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"events":[{"event":{"type":"aem.sites.page.published",
                         "data":{"resource_path":"/content/wknd/en"}}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isEqualTo(1);
        assertThat(fixture.service().cursor(SOURCE)).isEmpty();
        fixture.server().verify();
    }

    @Test
    @DisplayName("without an IMS token nothing is requested and nothing is committed")
    void noTokenSkipsThePoll() {
        when(tokenService.accessToken()).thenReturn(Optional.empty());
        Fixture fixture = fixture();

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isZero();
        assertThat(fixture.service().cursor(SOURCE)).isEmpty();
        verify(process, never()).sentToIndexStandaloneAsync(any(), any());
        fixture.server().verify();
    }

    @Test
    @DisplayName("follows pages up to the per-poll ceiling, draining a backlog across polls")
    void followsPagesUpToTheCeiling() {
        properties.setMaxPagesPerPoll(2);
        Fixture fixture = fixture();
        AtomicInteger served = new AtomicInteger();
        List<String> bodies = List.of(
                page("pos-a", "/content/wknd/a", "/content/wknd/b"),
                page("pos-b", "/content/wknd/c", "/content/wknd/d"));
        fixture.server().expect(org.springframework.test.web.client.ExpectedCount.times(2),
                MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.startsWith(JOURNAL)))
                .andRespond(request -> MockRestResponseCreators
                        .withSuccess(bodies.get(served.getAndIncrement()), MediaType.APPLICATION_JSON)
                        .createResponse(request));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isEqualTo(4);
        assertThat(fixture.service().cursor(SOURCE)).contains("pos-b");
        fixture.server().verify();
    }

    @Test
    @DisplayName("unwraps an event nested one level under body")
    void unwrapsNestedBody() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(JOURNAL + "?limit=2"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        """
                        {"events":[{"position":"p1","event":{"body":{
                         "type":"aem.sites.page.published",
                         "data":{"resource_path":"/content/wknd/en"}}}}],
                         "_page":{"last":"pos-1","count":1}}""",
                        MediaType.APPLICATION_JSON));
        fixture.server().expect(MockRestRequestMatchers.requestTo(
                JOURNAL + "?limit=2&since=pos-1"))
                .andRespond(MockRestResponseCreators.withSuccess(emptyPage(),
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isEqualTo(1);
        verify(process).sentToIndexStandaloneAsync(eq(SOURCE), any(DumAemPathList.class));
    }

    @Test
    @DisplayName("the journal is off unless enabled, credentialed and given a source")
    void usabilityRequiresAllThree() {
        assertThat(properties.isUsable()).isTrue();
        DumAemJournalProperties bare = new DumAemJournalProperties();
        bare.setEnabled(true);
        assertThat(bare.isUsable()).isFalse();
        bare.setClientId("id");
        bare.setClientSecret("secret");
        assertThat(bare.isUsable()).isFalse();
        bare.setSources(Map.of(SOURCE, JOURNAL));
        assertThat(bare.isUsable()).isTrue();
        bare.setEnabled(false);
        assertThat(bare.isUsable()).isFalse();
    }

    @Test
    @DisplayName("an HTTP status other than 2xx is a failed page, not a silent skip")
    void nonSuccessStatusIsAFailedPage() {
        Fixture fixture = fixture();
        fixture.server().expect(MockRestRequestMatchers.requestTo(JOURNAL + "?limit=2"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(fixture.service().poll(SOURCE, JOURNAL)).isZero();
        assertThat(fixture.service().cursor(SOURCE)).isEmpty();
    }
}
