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

package com.viglet.dumont.connector.plugin.aem.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.aem.export.bean.DumAemSourceExchange;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;

/** The export and the import agree on every field they carry (roadmap D65). */
@DisplayName("DumAemSourceExchangeMapper Tests (D65)")
class DumAemSourceExchangeMapperTest {

    /** Collections travel through their own exchange beans, not the scalar mapper. */
    private static final List<String> COLLECTION_FIELDS = List.of("localePaths",
            "attributeSpecifications", "models");

    private final DumAemSourceExchangeMapper mapper = new DumAemSourceExchangeMapperImpl();

    private static DumAemSource fullyConfigured() {
        return DumAemSource.builder()
                .id("src-1")
                .name("wknd")
                .endpoint("http://author.invalid:4502")
                .username("admin")
                .password("secret")
                .rootPath("/content/wknd/en")
                .contentType("cq:Page")
                .subType("cq:PageContent")
                .oncePattern(".*/once/.*")
                .defaultLocale(Locale.US)
                .localeClass("com.example.Locale")
                .deltaClass("com.example.Delta")
                .dependencyClass("com.example.Dependency")
                .indexConditionClass("com.example.Condition")
                .author(true)
                .publish(true)
                .authorSNSite("Author")
                .publishSNSite("Publish")
                .authorURLPrefix("http://author.invalid")
                .publishURLPrefix("http://publish.invalid")
                .fetchStrategy("DELIVERY")
                .deliveryURLPrefix("https://delivery-p1-e1.adobeaemcloud.com")
                .build();
    }

    @Test
    @DisplayName("a round trip is field-for-field equal on every scalar")
    void aRoundTripIsFieldForFieldEqual() throws ReflectiveOperationException {
        DumAemSource original = fullyConfigured();

        DumAemSource restored = mapper.toEntity(mapper.toExchange(original));

        int compared = 0;
        for (Field field : DumAemSource.class.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || Collection.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            assertThat(field.get(restored))
                    .as("field '%s' survives the round trip", field.getName())
                    .isEqualTo(field.get(original));
            compared++;
        }
        // A guard on the guard: if the entity is ever stripped down, this test must not silently
        // start proving nothing.
        assertThat(compared).as("scalar fields compared").isGreaterThanOrEqualTo(21);
    }

    @Test
    @DisplayName("the entity's collections are the ones the mapper deliberately leaves alone")
    void collectionsAreTheDeclaredExceptions() {
        List<String> collections = java.util.Arrays.stream(DumAemSource.class.getDeclaredFields())
                .filter(field -> Collection.class.isAssignableFrom(field.getType()))
                .map(Field::getName)
                .toList();

        // If a new collection appears, the mapper's ignore list and the exporter's own builders
        // both need it — MapStruct would fail the build, and this names why.
        assertThat(collections).containsExactlyInAnyOrderElementsOf(COLLECTION_FIELDS);
    }

    @Test
    @DisplayName("the id travels, so re-importing the same file updates rather than duplicating")
    void theIdTravels() {
        assertThat(mapper.toExchange(fullyConfigured()).getId()).isEqualTo("src-1");
        assertThat(mapper.toEntity(mapper.toExchange(fullyConfigured())).getId())
                .isEqualTo("src-1");
    }

    @Test
    @DisplayName("an unrecognised fetch strategy is normalised on import, not carried through")
    void anUnrecognisedFetchStrategyIsNormalised() {
        DumAemSourceExchange exchange = mapper.toExchange(fullyConfigured());
        exchange.setFetchStrategy("nonsense");

        assertThat(mapper.toEntity(exchange).getFetchStrategy()).isEqualTo("SLING");
    }

    @Test
    @DisplayName("a blank strategy on an older export becomes SLING rather than null")
    void aBlankStrategyBecomesSling() {
        DumAemSourceExchange exchange = mapper.toExchange(fullyConfigured());
        exchange.setFetchStrategy(null);

        assertThat(mapper.toEntity(exchange).getFetchStrategy()).isEqualTo("SLING");
    }

    @Test
    @DisplayName("the fields the old hand-written export dropped are the ones now carried")
    void theFieldsTheOldExportDroppedNowTravel() {
        DumAemSourceExchange exchange = mapper.toExchange(fullyConfigured());

        // Every one of these was absent from the exported JSON before D65, which is why an
        // imported source had no scope and no credentials.
        assertThat(exchange.getRootPath()).isEqualTo("/content/wknd/en");
        assertThat(exchange.getContentType()).isEqualTo("cq:Page");
        assertThat(exchange.getSubType()).isEqualTo("cq:PageContent");
        assertThat(exchange.getUsername()).isEqualTo("admin");
        assertThat(exchange.getPassword()).isEqualTo("secret");
        assertThat(exchange.getOncePattern()).isEqualTo(".*/once/.*");
        assertThat(exchange.getDeltaClass()).isEqualTo("com.example.Delta");
        assertThat(exchange.getDependencyClass()).isEqualTo("com.example.Dependency");
        assertThat(exchange.getAuthorURLPrefix()).isEqualTo("http://author.invalid");
        assertThat(exchange.getPublishURLPrefix()).isEqualTo("http://publish.invalid");
    }
}
