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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;

/** The shipped CBO 2002 family list as a D19 taxonomy, and the family slugs (trabalharei TRA22). */
class DumCboFamiliesTest {

    @Test
    void loadsEveryFamilyOfTheShippedList() {
        DumTaxonomy cbo = DumCboFamilies.load();

        assertThat(cbo.name()).isEqualTo(DumCboFamilies.TAXONOMY);
        assertThat(cbo.version()).isEqualTo(1);
        assertThat(cbo.table()).hasSize(617);
        assertThat(cbo.canonical("7825")).contains("Motoristas de veículos de cargas em geral");
        assertThat(cbo.canonical("4110")).contains("Agentes, assistentes e auxiliares administrativos");
        assertThat(cbo.canonical("2124")).contains("Analistas de tecnologia da informação");
        assertThat(cbo.canonical("3171")).contains("Técnicos de desenvolvimento de sistemas e aplicações");
        // Whitespace runs in the official titles are collapsed.
        assertThat(cbo.canonical("0202")).contains("Capitães da polícia militar");
        assertThat(cbo.table().keySet()).allMatch(code -> code.matches("\\d{4}"));
        assertThat(DumCboFamilies.load()).isSameAs(cbo);
    }

    @Test
    void theLibraryTracksTheLatestVersion() {
        assertThat(DumJobOccupationEnricher.library().taxonomy(DumRecipeRef.latest(DumCboFamilies.TAXONOMY)))
                .containsSame(DumCboFamilies.load());
    }

    @Test
    void slugsAreTheOfficialTitleFolded() {
        assertThat(DumCboFamilies.slug("Motoristas de veículos de cargas em geral"))
                .isEqualTo("motoristas-de-veiculos-de-cargas-em-geral");
        assertThat(DumCboFamilies.slug("Agentes, assistentes e auxiliares administrativos"))
                .isEqualTo("agentes-assistentes-e-auxiliares-administrativos");
        assertThat(DumCboFamilies.slug("Caixas e bilheteiros (exceto caixa de banco)"))
                .isEqualTo("caixas-e-bilheteiros-exceto-caixa-de-banco");
        assertThat(DumCboFamilies.slug("Secretárias(os) executivas(os) e afins"))
                .isEqualTo("secretarias-os-executivas-os-e-afins");
        assertThat(DumCboFamilies.slugOf(DumCboFamilies.load(), "3222"))
                .isEqualTo("tecnicos-e-auxiliares-de-enfermagem");
        assertThat(DumCboFamilies.slugOf(DumCboFamilies.load(), "9999")).isNull();
        assertThat(DumCboFamilies.load().table().values().stream().map(DumCboFamilies::slug).distinct())
                .hasSize(617);
    }

    @Test
    void rejectsAMalformedList() {
        String header = "{\"name\":\"cbo-familias\",\"version\":2,";
        assertThatThrownBy(() -> DumCboFamilies.read(header
                + "\"familias\":[{\"code\":\"411\",\"title\":\"X\"}]}"))
                .hasMessageContaining("not four digits");
        assertThatThrownBy(() -> DumCboFamilies.read(header + "\"familias\":["
                + "{\"code\":\"4110\",\"title\":\"X\"},{\"code\":\"4110\",\"title\":\"Y\"}]}"))
                .hasMessageContaining("listed twice");
        assertThatThrownBy(() -> DumCboFamilies.read(header + "\"familias\":["
                + "{\"code\":\"4110\",\"title\":\"Caixas\"},{\"code\":\"4211\",\"title\":\"caixas\"}]}"))
                .hasMessageContaining("share the slug");
        assertThatThrownBy(() -> DumCboFamilies.read(header + "\"count\":2,"
                + "\"familias\":[{\"code\":\"4110\",\"title\":\"X\"}]}"))
                .hasMessageContaining("declares 2 rows");
        assertThat(DumCboFamilies.read(header + "\"familias\":[{\"code\":\"4110\",\"title\":\" X \"}]}")
                .canonical("4110")).contains("X");
    }
}
