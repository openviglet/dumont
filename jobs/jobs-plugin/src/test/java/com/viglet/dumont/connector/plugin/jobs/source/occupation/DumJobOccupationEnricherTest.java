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

package com.viglet.dumont.connector.plugin.jobs.source.occupation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobOccupationCacheEntry;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobOccupationCacheRepository;
import com.viglet.dumont.connector.plugin.jobs.source.InMemoryCallRepository;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher.Summary;

/** Dictionary, persistent cache, daily cap and closed-label fallback of the occupation step (TRA22). */
class DumJobOccupationEnricherTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String MOTORISTAS = "motoristas-de-veiculos-de-cargas-em-geral";
    private static final String DESENHISTAS = "desenhistas-projetistas-e-modelistas-de-produtos-e-servicos-diversos";

    /** A classifier that answers from a function of the title and counts its calls. */
    private static final class CountingClassifier implements DumClassifier {
        final List<String> inputs = new ArrayList<>();
        final Function<String, Optional<String>> answer;

        CountingClassifier(Function<String, Optional<String>> answer) {
            this.answer = answer;
        }

        @Override
        public Optional<String> classify(String input) {
            inputs.add(input);
            return answer.apply(input);
        }
    }

    /** A {@link DumJobOccupationCacheRepository} mock backed by a map. */
    private static final class InMemoryCache {
        final Map<String, DumJobOccupationCacheEntry> rows = new HashMap<>();
        final DumJobOccupationCacheRepository repository = mock(DumJobOccupationCacheRepository.class);

        InMemoryCache() {
            lenient().when(repository.findById(anyString()))
                    .thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<String>getArgument(0))));
            lenient().when(repository.save(any(DumJobOccupationCacheEntry.class))).thenAnswer(inv -> {
                DumJobOccupationCacheEntry entry = inv.getArgument(0);
                rows.put(entry.getTitleHash(), entry);
                return entry;
            });
        }
    }

    private final DumStructuredSource source = source("jooble-br");
    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryCache cache = new InMemoryCache();
    private final InMemoryCallRepository calls = new InMemoryCallRepository();

    private static DumStructuredSource source(String name) {
        DumStructuredSource source = mock(DumStructuredSource.class);
        when(source.getSourceName()).thenReturn(name);
        return source;
    }

    private static DumStructuredRecord job(String id, String title) {
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(id, DumJobsSchema.schema());
        if (title != null) {
            rec.set(DumJobsSchema.TITLE, title);
        }
        return rec.build();
    }

    private DumJobOccupationEnricher enricher(DumClassifier classifier, int dailyCap) {
        return new DumJobOccupationEnricher(DumJobOccupationEnricher.library(),
                DumRecipeRef.latest(DumCboFamilies.TAXONOMY), classifier, cache.repository, calls.repository,
                dailyCap, clock);
    }

    private static CountingClassifier answering(String answer) {
        return new CountingClassifier(title -> Optional.of(answer));
    }

    @Test
    void theDictionaryAnswersWithoutTheClassifier() {
        CountingClassifier classifier = answering("4110");
        DumJobOccupationEnricher enricher = enricher(classifier, 10);

        assertThat(enricher.enrich(source, job("a", "Motorista Entregador")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, MOTORISTAS));
        assertThat(classifier.inputs).isEmpty();
        assertThat(cache.rows).isEmpty();
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(1, 1, 0, 0, 0, 0));
    }

    @Test
    void withNoClassifierAnUnknownTitleStaysAbsent() {
        DumJobOccupationEnricher enricher = DumJobOccupationEnricher.dictionaryOnly();

        assertThat(enricher.hasClassifier()).isFalse();
        assertThat(enricher.enrich(source, job("a", "Projetista de Moldes"))).isEmpty();
        assertThat(enricher.enrich(source, job("b", null))).isEmpty();
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(2, 0, 0, 0, 2, 0));
    }

    @Test
    void aCachedTitleIsNotClassifiedTwice() {
        CountingClassifier classifier = answering("3188");
        DumJobOccupationEnricher enricher = enricher(classifier, 10);

        assertThat(enricher.enrich(source, job("a", "Projetista de Moldes Pleno")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, DESENHISTAS));
        // The same title once normalized: other case, accents, seniority.
        assertThat(enricher.enrich(source, job("b", "PROJETISTA DE MOLDES SÊNIOR")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, DESENHISTAS));

        assertThat(classifier.inputs).containsExactly("Projetista de Moldes Pleno");
        String key = DumJobOccupationEnricher.hash("projetista de moldes");
        assertThat(cache.rows).containsOnlyKeys(key);
        assertThat(cache.rows.get(key).getCode()).isEqualTo("3188");
        assertThat(cache.rows.get(key).getTaxonomyVersion()).isEqualTo(1);
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(2, 0, 1, 1, 0, 0));
    }

    @Test
    void thePersistentCacheSurvivesARestart() {
        enricher(answering("3188"), 10).enrich(source, job("a", "Projetista de Moldes"));
        CountingClassifier afterRestart = answering("9999");

        assertThat(enricher(afterRestart, 10).enrich(source, job("b", "Projetista de Moldes")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, DESENHISTAS));
        assertThat(afterRestart.inputs).isEmpty();
    }

    @Test
    void aNoFamilyAnswerIsCachedAndLeavesTheFieldAbsent() {
        CountingClassifier classifier = answering(DumJobOccupationLabels.NONE);
        DumJobOccupationEnricher enricher = enricher(classifier, 10);

        assertThat(enricher.enrich(source, job("a", "Vaga Temporária Natal"))).isEmpty();
        assertThat(enricher.enrich(source, job("b", "Vaga Temporária Natal"))).isEmpty();

        assertThat(classifier.inputs).hasSize(1);
        assertThat(cache.rows.values()).singleElement().extracting(DumJobOccupationCacheEntry::getCode)
                .isNull();
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(2, 0, 1, 0, 2, 0));
    }

    @Test
    void anAnswerOutsideTheListIsNeverPublished() {
        DumJobOccupationEnricher enricher = enricher(answering("Resposta: 1235"), 10);

        assertThat(enricher.enrich(source, job("a", "Projetista de Moldes"))).isEmpty();
        assertThat(cache.rows.values()).singleElement().extracting(DumJobOccupationCacheEntry::getCode)
                .isNull();
    }

    @Test
    void aFailedCallIsNotCachedSoTheNextRecordAsksAgain() {
        CountingClassifier classifier = new CountingClassifier(title -> {
            throw new IllegalStateException("Turing unreachable");
        });
        DumJobOccupationEnricher enricher = enricher(classifier, 10);

        assertThat(enricher.enrich(source, job("a", "Projetista de Moldes"))).isEmpty();
        assertThat(enricher.enrich(source, job("b", "Projetista de Moldes"))).isEmpty();

        assertThat(classifier.inputs).hasSize(2);
        assertThat(cache.rows).isEmpty();
    }

    @Test
    void whenTheDailyCapIsReachedNoCallIsMadeAndTheFieldStaysAbsent() {
        CountingClassifier classifier = answering("3188");
        DumJobOccupationEnricher enricher = enricher(classifier, 2);

        enricher.enrich(source, job("a", "Projetista de Moldes"));
        enricher.enrich(source, job("b", "Modelista de Calçados"));
        Map<String, Object> third = enricher.enrich(source, job("c", "Projetista de Embalagens"));

        assertThat(third).isEmpty();
        assertThat(classifier.inputs).hasSize(2);
        assertThat(calls.calls(DumJobOccupationEnricher.LLM_CALLS_ID)).containsExactly(NOW, NOW);
        // A refused call writes no cache row, so the title is asked again once the cap allows.
        assertThat(cache.rows).hasSize(2);
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(3, 0, 0, 2, 1, 1));
        // A cached title still answers while the cap is reached.
        assertThat(enricher.enrich(source, job("d", "Projetista de Moldes")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, DESENHISTAS));
        assertThat(classifier.inputs).hasSize(2);
    }

    @Test
    void theCapHoldsAcrossRestartsAndRollsOverAfterADay() {
        enricher(answering("3188"), 2).enrich(source, job("a", "Projetista de Moldes"));
        enricher(answering("3188"), 2).enrich(source, job("b", "Modelista de Calçados"));
        CountingClassifier third = answering("3188");
        DumJobOccupationEnricher restarted = enricher(third, 2);

        assertThat(restarted.enrich(source, job("c", "Projetista de Embalagens"))).isEmpty();
        assertThat(third.inputs).isEmpty();

        clock.advance(Duration.ofHours(25));
        DumJobOccupationEnricher nextDay = enricher(third, 2);
        assertThat(nextDay.enrich(source, job("c", "Projetista de Embalagens")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, DESENHISTAS));
        assertThat(third.inputs).hasSize(1);
    }

    @Test
    void aZeroCapAllowsNoCall() {
        CountingClassifier classifier = answering("3188");

        assertThat(enricher(classifier, 0).enrich(source, job("a", "Projetista de Moldes"))).isEmpty();
        assertThat(classifier.inputs).isEmpty();
    }

    @Test
    void aCachedAnswerFromAnotherTaxonomyVersionIsAMiss() {
        DumTaxonomy v1 = DumTaxonomy.builder(DumCboFamilies.TAXONOMY, 1).map("3188", "Desenhistas").build();
        DumRecipeLibrary library = new DumRecipeLibrary().register(v1);
        CountingClassifier classifier = answering("3188");
        DumJobOccupationEnricher enricher = new DumJobOccupationEnricher(library,
                DumRecipeRef.latest(DumCboFamilies.TAXONOMY), classifier, cache.repository, calls.repository, 10,
                clock);

        assertThat(enricher.enrich(source, job("a", "Projetista de Moldes")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, "desenhistas"));
        library.register(DumTaxonomy.builder(DumCboFamilies.TAXONOMY, 2).map("3188", "Projetistas").build());
        assertThat(enricher.enrich(source, job("b", "Projetista de Moldes")))
                .isEqualTo(Map.of(DumJobsSchema.OCCUPATION, "projetistas"));

        assertThat(classifier.inputs).hasSize(2);
    }

    @Test
    void summariesAreKeptPerSourceAndClearedByLogSummary() {
        DumJobOccupationEnricher enricher = DumJobOccupationEnricher.dictionaryOnly();
        enricher.enrich(source, job("a", "Pedreiro"));
        enricher.enrich(source("bne-br"), job("b", "Analista"));

        assertThat(enricher.logSummary("jooble-br")).isEqualTo(new Summary(1, 1, 0, 0, 0, 0));
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(0, 0, 0, 0, 0, 0));
        assertThat(enricher.summary("bne-br")).isEqualTo(new Summary(1, 0, 0, 0, 1, 0));
        enricher.reset("bne-br");
        assertThat(enricher.summary("bne-br")).isEqualTo(new Summary(0, 0, 0, 0, 0, 0));
    }

    @Test
    void theLabelSetIsTheWholeFamilyListAndAnswersAreCheckedAgainstIt() {
        DumTaxonomy cbo = DumCboFamilies.load();
        String prompt = DumJobOccupationLabels.systemPrompt(cbo);

        assertThat(prompt).contains("7825 - Motoristas de veículos de cargas em geral")
                .contains(DumJobOccupationLabels.NONE);
        assertThat(cbo.table().keySet()).allMatch(code -> prompt.contains("\n" + code + " - "));
        assertThat(prompt.indexOf("\n0101 - ")).isLessThan(prompt.indexOf("\n9922 - "));

        assertThat(DumJobOccupationLabels.code("7825", cbo)).contains("7825");
        assertThat(DumJobOccupationLabels.code(" Código: 4110.\n", cbo)).contains("4110");
        assertThat(DumJobOccupationLabels.code(MOTORISTAS, cbo)).contains("7825");
        assertThat(DumJobOccupationLabels.code("9999", cbo)).isEmpty();
        assertThat(DumJobOccupationLabels.code("none", cbo)).isEmpty();
        assertThat(DumJobOccupationLabels.code("Motoristas em geral", cbo)).isEmpty();
        assertThat(DumJobOccupationLabels.code(null, cbo)).isEmpty();
    }
}
