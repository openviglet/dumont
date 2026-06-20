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

package com.viglet.dumont.commons.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumCustomClassCacheTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DumCustomClassCache.setExternalResolver(null);
        DumCustomClassCache.clear();
    }

    @Test
    void instantiatesClassByNameAndCaches() {
        Optional<Object> first = DumCustomClassCache.getCustomClassMap("java.util.ArrayList");
        Optional<Object> second = DumCustomClassCache.getCustomClassMap("java.util.ArrayList");

        assertThat(first).containsInstanceOf(ArrayList.class);
        // cached: same instance returned on second resolution
        assertThat(second).get().isSameAs(first.get());
    }

    @Test
    void returnsEmptyForUnknownClass() {
        assertThat(DumCustomClassCache.getCustomClassMap("com.example.DoesNotExist")).isEmpty();
    }

    @Test
    void externalResolverIsConsultedFirst() {
        Object resolved = new Object();
        DumCustomClassCache.setExternalResolver(name -> Optional.of(resolved));

        assertThat(DumCustomClassCache.getCustomClassMap("anything")).get().isSameAs(resolved);
    }

    @Test
    void fallsBackToClassForNameWhenResolverEmpty() {
        DumCustomClassCache.setExternalResolver(name -> Optional.empty());

        assertThat(DumCustomClassCache.getCustomClassMap("java.util.HashMap"))
                .containsInstanceOf(java.util.HashMap.class);
    }

    @Test
    void clearDropsCachedInstances() {
        Object first = DumCustomClassCache.getCustomClassMap("java.util.ArrayList").orElseThrow();
        DumCustomClassCache.clear();
        Object afterClear =
                DumCustomClassCache.getCustomClassMap("java.util.ArrayList").orElseThrow();

        assertThat(afterClear).isNotSameAs(first);
    }
}
