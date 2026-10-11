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

package com.viglet.dumont.connector.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumCanonicalKey;
import com.viglet.dumont.connector.commons.spi.structured.resolve.DumResolutionIndex;
import com.viglet.dumont.connector.persistence.model.DumResolutionMemberModel;
import com.viglet.dumont.connector.persistence.repository.DumResolutionMemberRepository;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** The persistent resolution index keeps members across a restart (roadmap D109). */
class DumJpaResolutionIndexTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("code")
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .field(DumStructuredField.builder("starts").type(TurSEFieldType.DATE).build())
            .field(DumStructuredField.builder("seats").type(TurSEFieldType.INT).build())
            .build();

    /** The table, shared by every index built over it, the way a restart finds it. */
    private final List<DumResolutionMemberModel> table = new ArrayList<>();
    private final DumJpaResolutionIndexFactory factory = new DumJpaResolutionIndexFactory(repository(table),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    void resolutionMembersSurviveAConnectorRestart() {
        FixtureSource catalogA = new FixtureSource("catalog-a", Map.of("a-1", rec("a-1", "CS101", "Algorithms",
                LocalDate.of(2026, 11, 3), null)));
        FixtureSource catalogB = new FixtureSource("catalog-b", Map.of("b-7", rec("b-7", "cs101", null, null, 40)));
        new CatalogPlugin(new CapturingContext(), factory, catalogA, catalogB).crawl();

        // A restart: a new plugin and a new index over the same table; only catalog-b runs.
        CapturingContext context = new CapturingContext();
        new CatalogPlugin(context, factory, catalogA, catalogB).indexAll("catalog-b");

        assertEquals(1, context.items.size());
        Map<String, Object> attributes = context.items.get(0).getAttributes();
        assertEquals("cs101", attributes.get("id"));
        assertEquals("Algorithms", attributes.get("title"), "catalog-a's member came back from the table");
        assertEquals(LocalDate.of(2026, 11, 3), attributes.get("starts"), "a date is still a date");
        assertEquals(40, attributes.get("seats"), "an int is still an int");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) attributes.get(DumProvenance.FIELD);
        assertTrue(tokens.contains("title|catalog-a|css|h1"), "the stored provenance cites its source");
        assertTrue(tokens.contains("seats|catalog-b|css|.seats"));
    }

    @Test
    void membersKeepTheirFirstFiledOrderAndHoldState() {
        DumResolutionIndex index = factory.forProvider("P", source -> Optional.of(SCHEMA));
        index.put("cs101", "b", "b-7", rec("b-7", "CS101", "Second", null, null));
        index.put("cs101", "a", "a-1", rec("a-1", "CS101", "First", null, null));
        index.put("cs101", "b", "b-7", rec("b-7", "CS101", "Second again", null, null));

        List<DumResolutionIndex.Member> members = factory.forProvider("P", source -> Optional.of(SCHEMA))
                .members("cs101");
        assertEquals(List.of("b", "a"), members.stream().map(DumResolutionIndex.Member::source).toList(),
                "a re-filed member keeps its place");
        assertEquals("Second again", members.get(0).rec().values().get("title"));

        index.hold("cs101", "a", "a-1");
        assertFalse(index.members("cs101").get(1).live());
        assertEquals(Optional.empty(), index.canonicalIdOf("a", "a-1"));
        assertEquals(Optional.of("cs101"), index.canonicalIdOf("b", "b-7"));

        index.drop("cs101");
        assertTrue(index.members("cs101").isEmpty());
    }

    @Test
    void providersAndGoneSourcesAreKeptApart() {
        factory.forProvider("P", source -> Optional.of(SCHEMA)).put("cs101", "a", "a-1",
                rec("a-1", "CS101", "Algorithms", null, null));

        assertTrue(factory.forProvider("Q", source -> Optional.of(SCHEMA)).members("cs101").isEmpty());
        assertTrue(factory.forProvider("P", source -> Optional.empty()).members("cs101").isEmpty(),
                "a member of a source the provider no longer has is skipped");
    }

    private static DumStructuredRecord rec(String id, String code, String title, LocalDate starts, Integer seats) {
        return DumStructuredRecord.builder(id, SCHEMA)
                .set("code", code, DumFieldProvenance.css(".code"))
                .set("title", title, DumFieldProvenance.css("h1"))
                .set("starts", starts, DumFieldProvenance.css(".starts"))
                .set("seats", seats, DumFieldProvenance.css(".seats"))
                .build();
    }

    /** A repository over a list: the methods the index calls, nothing more. */
    private static DumResolutionMemberRepository repository(List<DumResolutionMemberModel> table) {
        return (DumResolutionMemberRepository) Proxy.newProxyInstance(
                DumResolutionMemberRepository.class.getClassLoader(),
                new Class<?>[] { DumResolutionMemberRepository.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "findByProviderAndCanonicalIdOrderByMemberOrder" -> table.stream()
                            .filter(row -> row.getProvider().equals(args[0]) && row.getCanonicalId().equals(args[1]))
                            .sorted(Comparator.comparingLong(DumResolutionMemberModel::getMemberOrder)).toList();
                    case "findByProviderAndSource" -> table.stream()
                            .filter(row -> row.getProvider().equals(args[0]) && row.getSource().equals(args[1]))
                            .toList();
                    case "findByProviderAndSourceAndRecordId" -> table.stream()
                            .filter(row -> row.getProvider().equals(args[0]) && row.getSource().equals(args[1])
                                    && row.getRecordId().equals(args[2]))
                            .toList();
                    case "findFirstByProviderOrderByMemberOrderDesc" -> table.stream()
                            .filter(row -> row.getProvider().equals(args[0]))
                            .max(Comparator.comparingLong(DumResolutionMemberModel::getMemberOrder));
                    case "deleteByProviderAndCanonicalId" -> {
                        table.removeIf(row -> row.getProvider().equals(args[0]) && row.getCanonicalId().equals(args[1]));
                        yield null;
                    }
                    case "save" -> {
                        saveRow(table, (DumResolutionMemberModel) args[0]);
                        yield args[0];
                    }
                    case "saveAll" -> {
                        ((Iterable<?>) args[0]).forEach(row -> saveRow(table, (DumResolutionMemberModel) row));
                        yield args[0];
                    }
                    case "toString" -> "fake DumResolutionMemberRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static void saveRow(List<DumResolutionMemberModel> table, DumResolutionMemberModel row) {
        if (!table.contains(row)) {
            table.add(row);
        }
    }

    private static final class FixtureSource implements DumStructuredSource {
        private final String name;
        private final Map<String, DumStructuredRecord> records;

        FixtureSource(String name, Map<String, DumStructuredRecord> records) {
            this.name = name;
            this.records = new LinkedHashMap<>(records);
        }

        @Override
        public String getSourceName() {
            return name;
        }

        @Override
        public String getProviderName() {
            return "CATALOG";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return List.copyOf(records.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.ofNullable(records.get(contentId));
        }
    }

    private static final class CatalogPlugin extends DumStructuredSourcePlugin implements DumStructuredSourceProvider {
        private final Map<String, DumStructuredSource> sources;

        CatalogPlugin(DumConnectorContext context, DumJpaResolutionIndexFactory factory, FixtureSource... fixtures) {
            this(context, factory, sourcesOf(fixtures));
        }

        private CatalogPlugin(DumConnectorContext context, DumJpaResolutionIndexFactory factory,
                Map<String, DumStructuredSource> sources) {
            super(context, DumEnrichmentChain.empty(), null, null, null, DumCanonicalKey.field("code"),
                    factory.forProvider("CATALOG", name -> Optional.ofNullable(sources.get(name))
                            .map(DumStructuredSource::schema)));
            this.sources = sources;
        }

        private static Map<String, DumStructuredSource> sourcesOf(FixtureSource... fixtures) {
            Map<String, DumStructuredSource> sources = new LinkedHashMap<>();
            for (FixtureSource fixture : fixtures) {
                sources.put(fixture.getSourceName(), fixture);
            }
            return sources;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return this;
        }

        @Override
        public String getProviderName() {
            return "CATALOG";
        }

        @Override
        public Collection<String> sourceNames() {
            return sources.keySet();
        }

        @Override
        public Optional<DumStructuredSource> resolve(String sourceName) {
            return Optional.ofNullable(sources.get(sourceName));
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<TurSNJobItem> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            return items.add(item.turSNJobItem());
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // nothing to finish
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}
