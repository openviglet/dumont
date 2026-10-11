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

package com.viglet.dumont.connector.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One answer a classification enricher paid for (roadmap <strong>D107</strong>), so each distinct
 * input is classified once across runs and restarts.
 *
 * <p>
 * The key is the SHA-256 the enricher computes over its name, its label set and the input, so a
 * changed label set is a miss. A {@code null} {@link #label} is a cached "no label". No tenant
 * column: an answer depends on the input and the label set alone.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "dum_classification_answer")
public class DumClassificationAnswerModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SHA-256 (hex) of the enricher name, label set and input. */
    @Id
    @Column(name = "answer_key", nullable = false, length = 64)
    private String answerKey;

    /** The label, or {@code null} for "no label". */
    @Column(name = "label", length = 512)
    private String label;

    @Column(name = "classified_at", nullable = false)
    private Instant classifiedAt;
}
