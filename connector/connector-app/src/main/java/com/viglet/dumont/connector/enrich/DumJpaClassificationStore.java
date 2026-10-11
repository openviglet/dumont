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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationStore;
import com.viglet.dumont.connector.persistence.model.DumClassificationAnswerModel;
import com.viglet.dumont.connector.persistence.model.DumClassificationCallModel;
import com.viglet.dumont.connector.persistence.repository.DumClassificationAnswerRepository;
import com.viglet.dumont.connector.persistence.repository.DumClassificationCallRepository;

/**
 * The persistent {@link DumClassificationStore} (roadmap <strong>D107</strong>): answers in
 * {@code dum_classification_answer} and calls in {@code dum_classification_call}, so a
 * {@code DumClassificationEnricher}'s cache and daily cap survive a restart. A plugin asks for it as
 * an optional dependency and hands it to the enricher's {@code store}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class DumJpaClassificationStore implements DumClassificationStore {

    /** Calls older than this are dropped when a new one is recorded: no cap looks further back. */
    static final Duration CALL_RETENTION = Duration.ofDays(2);

    private final DumClassificationAnswerRepository answers;
    private final DumClassificationCallRepository calls;

    public DumJpaClassificationStore(DumClassificationAnswerRepository answers,
            DumClassificationCallRepository calls) {
        this.answers = answers;
        this.calls = calls;
    }

    @Override
    public Optional<Answer> find(String key) {
        return answers.findById(key).map(row -> new Answer(row.getLabel(), row.getClassifiedAt()));
    }

    @Override
    public void put(String key, Answer answer) {
        answers.save(DumClassificationAnswerModel.builder().answerKey(key).label(answer.label())
                .classifiedAt(answer.classifiedAt() != null ? answer.classifiedAt() : Instant.now())
                .build());
    }

    @Override
    public long callsSince(String scope, Instant since) {
        return calls.countByScopeAndCalledAtAfter(scope, since);
    }

    @Override
    @Transactional
    public void recordCall(String scope, Instant at) {
        calls.deleteByScopeAndCalledAtBefore(scope, at.minus(CALL_RETENTION));
        calls.save(DumClassificationCallModel.builder().scope(scope).calledAt(at).build());
    }
}
