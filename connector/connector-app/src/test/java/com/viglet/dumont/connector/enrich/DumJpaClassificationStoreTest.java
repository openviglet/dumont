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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationStore.Answer;
import com.viglet.dumont.connector.persistence.model.DumClassificationAnswerModel;
import com.viglet.dumont.connector.persistence.model.DumClassificationCallModel;
import com.viglet.dumont.connector.persistence.repository.DumClassificationAnswerRepository;
import com.viglet.dumont.connector.persistence.repository.DumClassificationCallRepository;

/** The persistent classification store keeps answers and calls in their tables (roadmap D107). */
class DumJpaClassificationStoreTest {

    private static final Instant AT = Instant.parse("2026-10-11T10:00:00Z");

    private final DumClassificationAnswerRepository answers = mock(DumClassificationAnswerRepository.class);
    private final DumClassificationCallRepository calls = mock(DumClassificationCallRepository.class);
    private final DumJpaClassificationStore store = new DumJpaClassificationStore(answers, calls);

    @Test
    void readsAStoredAnswerIncludingNoLabel() {
        when(answers.findById("k")).thenReturn(Optional.of(
                DumClassificationAnswerModel.builder().answerKey("k").label(null).classifiedAt(AT).build()));

        Answer answer = store.find("k").orElseThrow();
        assertNull(answer.label(), "a stored no-label is a hit with no label");
        assertEquals(AT, answer.classifiedAt());
        assertTrue(store.find("missing").isEmpty());
    }

    @Test
    void writesAnAnswerUnderItsKey() {
        store.put("k", new Answer("Humanities", AT));

        ArgumentCaptor<DumClassificationAnswerModel> saved =
                ArgumentCaptor.forClass(DumClassificationAnswerModel.class);
        verify(answers).save(saved.capture());
        assertEquals("k", saved.getValue().getAnswerKey());
        assertEquals("Humanities", saved.getValue().getLabel());
        assertEquals(AT, saved.getValue().getClassifiedAt());
    }

    @Test
    void recordsACallAndDropsTheOnesNoCapReads() {
        store.recordCall("area-llm", AT);

        verify(calls).deleteByScopeAndCalledAtBefore("area-llm",
                AT.minus(DumJpaClassificationStore.CALL_RETENTION));
        ArgumentCaptor<DumClassificationCallModel> saved = ArgumentCaptor.forClass(DumClassificationCallModel.class);
        verify(calls).save(saved.capture());
        assertEquals("area-llm", saved.getValue().getScope());
        assertEquals(AT, saved.getValue().getCalledAt());
    }

    @Test
    void countsCallsPerScope() {
        when(calls.countByScopeAndCalledAtAfter(any(), any())).thenReturn(3L);
        assertEquals(3L, store.callsSince("area-llm", AT));
        verify(calls).countByScopeAndCalledAtAfter("area-llm", AT);
    }
}
