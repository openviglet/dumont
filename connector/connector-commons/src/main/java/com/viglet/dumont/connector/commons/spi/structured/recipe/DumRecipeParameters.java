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

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The <em>per-source</em> parameters a recipe is materialized against (roadmap <strong>D19</strong>).
 *
 * <p>
 * A recipe captures everything shared across institutions — the schema, the row selector, the column
 * rules, the taxonomy applications. What changes per source is small and lives here: the source's
 * id, the Turing SN sites it publishes to, its locale, and the listing URL(s) to crawl. The leverage
 * D19 promises is exactly this split: 50 SIGAA federals become one recipe plus 50 of these tiny
 * parameter sets, not 50 near-identical configs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumRecipeParameters(String sourceName, List<String> siteNames, Locale locale,
        List<String> listingUrls) {

    public DumRecipeParameters {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Recipe parameters need a source name");
        }
        if (listingUrls == null || listingUrls.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recipe parameters need at least one listing URL for source '" + sourceName + "'");
        }
        siteNames = siteNames == null ? List.of() : List.copyOf(siteNames);
        listingUrls = List.copyOf(listingUrls);
        locale = locale == null ? Locale.US : locale;
    }

    public static Builder builder(String sourceName) {
        return new Builder(sourceName);
    }

    public static final class Builder {
        private final String sourceName;
        private final List<String> siteNames = new ArrayList<>();
        private final List<String> listingUrls = new ArrayList<>();
        private Locale locale = Locale.US;

        private Builder(String sourceName) {
            this.sourceName = sourceName;
        }

        public Builder site(String siteName) {
            this.siteNames.add(siteName);
            return this;
        }

        public Builder listing(String url) {
            this.listingUrls.add(url);
            return this;
        }

        public Builder listings(Iterable<String> urls) {
            urls.forEach(this.listingUrls::add);
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public DumRecipeParameters build() {
            return new DumRecipeParameters(sourceName, siteNames, locale, listingUrls);
        }
    }
}
