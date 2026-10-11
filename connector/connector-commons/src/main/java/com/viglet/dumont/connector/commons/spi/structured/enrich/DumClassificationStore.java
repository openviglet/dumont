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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Where a {@link DumClassificationEnricher} keeps what it already paid for (roadmap
 * <strong>D107</strong>): the answers it got, so each distinct input is classified once, and the calls
 * it made, so a daily cap holds across runs.
 *
 * <p>
 * {@link DumEnrichmentCache} is the in-memory store, and the enricher's default. {@code connector-app}
 * registers a persistent one as a bean, which a plugin asks for as an optional dependency so the
 * cache and the cap outlive a restart.
 *
 * <p>
 * Keys are opaque to the store: the enricher hashes its name, its label set and the input with
 * SHA-256, so a changed label set is a miss and two enrichers never read each other's answers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumClassificationStore {

    /** An answer the classifier gave; a {@code null} label is a cached "no label". */
    record Answer(String label, Instant classifiedAt) {

        public Optional<String> asOptional() {
            return Optional.ofNullable(label);
        }
    }

    /** The answer stored under {@code key}, if any and still valid. */
    Optional<Answer> find(String key);

    /** Store {@code answer} under {@code key}, replacing any earlier one. */
    void put(String key, Answer answer);

    /** How many calls {@link #recordCall} recorded for {@code scope} after {@code since}. */
    long callsSince(String scope, Instant since);

    /** Record that the enricher named {@code scope} called its classifier at {@code at}. */
    void recordCall(String scope, Instant at);

    /** SHA-256 of {@code text} in UTF-8, as 64 hex characters: the strong hash keys are made of. */
    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
