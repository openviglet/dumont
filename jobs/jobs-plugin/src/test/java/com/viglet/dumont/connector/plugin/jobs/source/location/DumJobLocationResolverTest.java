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

package com.viglet.dumont.connector.plugin.jobs.source.location;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationResolver.Resolution;

/** One canonical IBGE city and UF per job, whatever the partner wrote (trabalharei TRA20). */
class DumJobLocationResolverTest {

    private static final DumJobLocationResolver RESOLVER =
            DumJobLocationResolver.of(DumIbgeMunicipalities.load());

    // --- the shipped taxonomy -----------------------------------------------------------------

    @Test
    void theShippedListIsTheWholeIbgeListAsAVersionedTaxonomy() {
        DumTaxonomy taxonomy = DumIbgeMunicipalities.load();
        assertThat(taxonomy.name()).isEqualTo(DumIbgeMunicipalities.TAXONOMY);
        assertThat(taxonomy.version()).isEqualTo(1);
        assertThat(taxonomy.table()).hasSize(5571)
                .containsEntry("3550308", "São Paulo")
                .containsEntry("5300108", "Brasília");
        assertThat(RESOLVER.size()).isEqualTo(5571);
        assertThat(DumIbgeMunicipalities.ufOf("3550308")).isEqualTo("SP");
        assertThat(DumIbgeMunicipalities.ufOf("5300108")).isEqualTo("DF");
        assertThat(DumIbgeMunicipalities.ufOf("9900000")).isNull();
    }

    @Test
    void everyAliasPointsAtAShippedMunicipality() {
        DumTaxonomy taxonomy = DumIbgeMunicipalities.load();
        assertThat(taxonomy.table()).containsKeys(
                DumJobLocationResolver.ALIASES.values().toArray(String[]::new));
    }

    // --- the table ----------------------------------------------------------------------------

    /**
     * city | state | raw location | expected city | expected UF | remote | unresolved raw text. An
     * empty cell is {@code null}. Rows tagged by where the input comes from.
     */
    @ParameterizedTest(name = "[{index}] city={0} state={1} raw={2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            # Jooble fixtures: the mapper's city/state plus the raw location text
            Campinas              | SP           | Campinas, SP                  | Campinas              | SP |       |
            São Paulo             | SP           | São Paulo - SP                | São Paulo             | SP |       |
                                  |              | Remoto                        |                       |    | true  |
                                  |              | Brasil                        |                       |    |       |
            Belo Horizonte        | MG           | Belo Horizonte, Minas Gerais  | Belo Horizonte        | MG |       |
            Rio de Janeiro        | RJ           | Rio de Janeiro, RJ, Brasil    | Rio de Janeiro        | RJ |       |
            Curitiba              | PR           | Híbrido - Curitiba, PR        | Curitiba              | PR |       |
            # Adzuna fixtures: location.area already split (state is the UF the mapper resolved)
            Florianópolis         | SC           |                               | Florianópolis         | SC |       |
                                  | PR           |                               |                       | PR |       |
            Fortaleza             | CE           |                               | Fortaleza             | CE |       |
            Salvador              | BA           |                               | Salvador              | BA |       |
            Porto Alegre          | RS           |                               | Porto Alegre          | RS |       |
            # BNE fixtures: City/State as sent (the mapper keeps City raw, State only when a UF)
            "Belo  Horizonte"     | mg           |                               | Belo Horizonte        | MG |       |
            Embu-Guaçu            | XX           |                               | Embu-Guaçu            | SP |       |
            SÃO JOSÉ DOS CAMPOS   | SP           |                               | São José dos Campos   | SP |       |
            Natal                 | RN           |                               | Natal                 | RN |       |
            Recife                | Pernambuco   |                               | Recife                | PE |       |
            # JSearch fixtures: job_city / job_state / job_location
            São Paulo             | SP           | São Paulo, SP                 | São Paulo             | SP |       |
            Campinas              | São Paulo    | Campinas, São Paulo           | Campinas              | SP |       |
            Belo Horizonte        | Minas Gerais |                               | Belo Horizonte        | MG |       |
            Campinas              | State of São Paulo |                         | Campinas              | SP |       |
            # Free-text forms
                                  |              | Sao Paulo, SP                 | São Paulo             | SP |       |
                                  |              | São Paulo - SP                | São Paulo             | SP |       |
                                  |              | Campinas/SP                   | Campinas              | SP |       |
                                  |              | Campinas-SP                   | Campinas              | SP |       |
                                  |              | Belo Horizonte (MG)           | Belo Horizonte        | MG |       |
                                  |              | Rio de Janeiro - Brasil       | Rio de Janeiro        | RJ |       |
                                  |              | São Paulo                     | São Paulo             | SP |       |
                                  |              | Vila Olímpia, São Paulo - SP  | São Paulo             | SP |       |
                                  |              | sao jose dos campos           | São José dos Campos   | SP |       |
                                  |              | Santa Barbara D'Oeste, SP     | Santa Bárbara d'Oeste | SP |       |
                                  |              | Minas Gerais                  |                       | MG |       |
            Campinas/SP           |              |                               | Campinas              | SP |       |
            # A state name in the city field is a city there (Paraná is in RN, not PR)
            Paraná                | RN           |                               | Paraná                | RN |       |
            # Aliases
                                  |              | Sampa                         | São Paulo             | SP |       |
            BH                    |              |                               | Belo Horizonte        | MG |       |
                                  |              | Rio                           | Rio de Janeiro        | RJ |       |
                                  |              | Floripa                       | Florianópolis         | SC |       |
                                  |              | Poa                           | Porto Alegre          | RS |       |
                                  |              | POA - RS                      | Porto Alegre          | RS |       |
                                  |              | CWB                           | Curitiba              | PR |       |
                                  |              | BSB                           | Brasília              | DF |       |
            # ... accents tell the alias "Poa" from the municipality Poá (SP)
                                  |              | Poá, SP                       | Poá                   | SP |       |
            # Ambiguous names: the city stays, the UF stays absent unless stated
                                  |              | Bom Jesus                     | Bom Jesus             |    |       |
            Bom Jesus             | RS           |                               | Bom Jesus             | RS |       |
                                  |              | Bom Jesus, PI                 | Bom Jesus             | PI |       |
                                  |              | Goianá                        | Goianá                | MG |       |
                                  |              | Goiana                        | Goiana                | PE |       |
            # A city that is not in the stated UF: the UF stays, the city goes
            Recife                | SP           |                               |                       | SP |       | Recife, SP
                                  |              | Bom Jesus/SP                  |                       | SP |       | Bom Jesus/SP
                                  |              | Rio, SP                       |                       | SP |       | Rio, SP
            # Remote markers: remote, no city; a UF stated next to it is kept
                                  |              | Home Office                   |                       |    | true  |
                                  |              | home-office                   |                       |    | true  |
                                  |              | Anywhere                      |                       |    | true  |
                                  |              | 100% remoto                   |                       |    | true  |
                                  |              | Teletrabalho                  |                       |    | true  |
                                  |              | Remoto - Brasil               |                       |    | true  |
                                  |              | São Paulo, SP (Remoto)        |                       | SP | true  |
            Remoto                |              |                               |                       |    | true  |
            # Unresolved: kept raw, never guessed
                                  |              | Xyzlândia                     |                       |    |       | Xyzlândia
                                  | XX           |                               |                       |    |       | XX
            Springfield           | Ohio         |                               |                       |    |       | Springfield, Ohio
            """)
    void resolves(String city, String state, String raw, String expectedCity, String expectedUf,
            Boolean remote, String unresolved) {
        Resolution place = RESOLVER.resolve(city, state, raw);

        assertThat(place.city()).as("city").isEqualTo(expectedCity);
        assertThat(place.uf()).as("uf").isEqualTo(expectedUf);
        assertThat(place.remote()).as("remote").isEqualTo(Boolean.TRUE.equals(remote));
        assertThat(place.unresolved()).as("unresolved").isEqualTo(unresolved);
    }

    @Test
    void nothingGivenIsNothingResolvedAndNothingUnresolved() {
        assertThat(RESOLVER.resolve(null, null, null))
                .isEqualTo(new Resolution(null, null, false, null));
        assertThat(RESOLVER.resolve(" ", "", "Brasil"))
                .isEqualTo(new Resolution(null, null, false, null));
    }

    @Test
    void aResolverReadsTheUfFromTheIbgeCodeOfAnyTaxonomyVersion() {
        DumTaxonomy tiny = DumTaxonomy.builder(DumIbgeMunicipalities.TAXONOMY, 2)
                .map("3509502", "Campinas").build();
        DumJobLocationResolver resolver = DumJobLocationResolver.of(tiny);
        assertThat(resolver.resolve("campinas", null, null))
                .isEqualTo(new Resolution("Campinas", "SP", false, null));
        assertThat(resolver.resolve("Recife", "PE", null))
                .isEqualTo(new Resolution(null, "PE", false, "Recife, PE"));
    }
}
