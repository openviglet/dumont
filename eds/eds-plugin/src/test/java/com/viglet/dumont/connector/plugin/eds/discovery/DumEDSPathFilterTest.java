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

package com.viglet.dumont.connector.plugin.eds.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DumEDSPathFilterTest {

    private DumEDSPathFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DumEDSPathFilter();
    }

    @Test
    void matchesExactPath() {
        assertThat(filter.isExcluded("/footer", List.of("/footer"))).isTrue();
        assertThat(filter.isExcluded("/footer-not-quite", List.of("/footer"))).isFalse();
    }

    @Test
    void singleStarMatchesSegment() {
        assertThat(filter.isExcluded("/pt-br/baixe-o-app", List.of("/*/baixe-o-app"))).isTrue();
        assertThat(filter.isExcluded("/pt-br/pf/baixe-o-app", List.of("/*/baixe-o-app"))).isFalse();
    }

    @Test
    void doubleStarMatchesAnyDepth() {
        assertThat(filter.isExcluded("/pt-br/pf/baixe-o-app", List.of("/**/baixe-o-app"))).isTrue();
        assertThat(filter.isExcluded("/pt-br/baixe-o-app", List.of("/**/baixe-o-app"))).isTrue();
        assertThat(filter.isExcluded("/fragments/header", List.of("/fragments/**"))).isTrue();
        assertThat(filter.isExcluded("/fragments/deeply/nested/header", List.of("/fragments/**")))
                .isTrue();
    }

    @Test
    void defaultExcludePatternsFilterOutTrashPages() {
        List<String> patterns = List.of(
                "/footer",
                "/spreadsheets/**",
                "/**/baixe-o-app",
                "/**/pagina-de-erro",
                "/fragments/**",
                "/content/dam/**");

        assertThat(filter.isExcluded("/footer", patterns)).isTrue();
        assertThat(filter.isExcluded("/pt-br/pf/baixe-o-app", patterns)).isTrue();
        assertThat(filter.isExcluded("/pt-br/pj/baixe-o-app", patterns)).isTrue();
        assertThat(filter.isExcluded("/pt-br/pf/pagina-de-erro", patterns)).isTrue();
        assertThat(filter.isExcluded("/fragments/header", patterns)).isTrue();
        assertThat(filter.isExcluded("/spreadsheets/products", patterns)).isTrue();
        assertThat(filter.isExcluded("/content/dam/images/foo.jpg", patterns)).isTrue();

        assertThat(filter.isExcluded("/pt-br/pf/conta-digital", patterns)).isFalse();
        assertThat(filter.isExcluded("/pt-br/pj", patterns)).isFalse();
        assertThat(filter.isExcluded("/pt-br/sala-de-imprensa", patterns)).isFalse();
    }

    @Test
    void normalisesPathsWithoutLeadingSlash() {
        assertThat(filter.isExcluded("footer", List.of("footer"))).isTrue();
        assertThat(filter.isExcluded("/footer", List.of("footer"))).isTrue();
        assertThat(filter.isExcluded("footer", List.of("/footer"))).isTrue();
    }

    @Test
    void ignoresNullAndBlankPatterns() {
        assertThat(filter.isExcluded("/anything", null)).isFalse();
        assertThat(filter.isExcluded("/anything", List.of())).isFalse();
        assertThat(filter.isExcluded("/anything", java.util.Arrays.asList("", "  ", null)))
                .isFalse();
    }

    @Test
    void filterMethodReturnsKeptPaths() {
        List<String> all = List.of("/pt-br/pf", "/footer", "/pt-br/pf/baixe-o-app",
                "/pt-br/pj", "/pt-br/pf/pagina-de-erro");
        List<String> patterns = List.of("/footer", "/**/baixe-o-app", "/**/pagina-de-erro");

        List<String> kept = filter.filter(all, patterns);

        assertThat(kept).containsExactly("/pt-br/pf", "/pt-br/pj");
    }
}
