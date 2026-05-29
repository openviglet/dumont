/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;

class DumEDSCrawlStateServiceTest {

    // No repo wiring needed for isUnchanged() — pure-logic helper.
    private final DumEDSCrawlStateService service = new DumEDSCrawlStateService(null);

    @Test
    void isUnchangedFalseWhenPriorIsNull() {
        assertThat(service.isUnchanged(null, "2026-05-01T00:00:00Z")).isFalse();
    }

    @Test
    void isUnchangedFalseWhenEitherSideBlank() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified(null);
        assertThat(service.isUnchanged(prior, "2026-05-01")).isFalse();

        prior.setLastModified("2026-05-01");
        assertThat(service.isUnchanged(prior, null)).isFalse();
        assertThat(service.isUnchanged(prior, "")).isFalse();
        assertThat(service.isUnchanged(prior, "   ")).isFalse();
    }

    @Test
    void isUnchangedTrueOnExactMatch() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified("2026-05-01T12:00:00Z");

        assertThat(service.isUnchanged(prior, "2026-05-01T12:00:00Z")).isTrue();
    }

    @Test
    void isUnchangedFalseOnDifferentStamp() {
        DumEDSCrawlState prior = new DumEDSCrawlState();
        prior.setLastModified("2026-05-01T12:00:00Z");

        assertThat(service.isUnchanged(prior, "2026-05-02T12:00:00Z")).isFalse();
    }
}
