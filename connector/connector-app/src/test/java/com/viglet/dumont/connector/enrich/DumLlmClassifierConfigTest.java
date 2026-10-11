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

package com.viglet.dumont.connector.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifierFactory;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumExtractorFactory;
import com.viglet.dumont.connector.config.DumTuringCsrfService;

/** The LLM classifier reaches plugins only when the documented flag turns it on (roadmap D106). */
class DumLlmClassifierConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DumLlmClassifierConfig.class)
            .withBean(RestClient.class, () -> RestClient.builder().baseUrl("http://localhost:1").build())
            .withBean(DumTuringCsrfService.class, () -> mock(DumTuringCsrfService.class));

    @Test
    void theFlagOnExposesAFactoryBuildingTuringClassifiers() {
        runner.withPropertyValues("dumont.enrichment.llm.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(DumClassifierFactory.class);
            DumClassifier classifier = context.getBean(DumClassifierFactory.class)
                    .create("Answer one CBO family or NONE.", "jobs-cbo");
            assertThat(classifier).isInstanceOf(DumTuringClassifier.class);
        });
    }

    @Test
    void theFlagOnAlsoExposesAFactoryBuildingTuringExtractors() {
        // D131: the open-vocabulary sibling rides on the same flag.
        runner.withPropertyValues("dumont.enrichment.llm.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(DumExtractorFactory.class);
            assertThat(context.getBean(DumExtractorFactory.class)
                    .create("List the skills this job requires.", "jobs-skills"))
                    .isInstanceOf(DumTuringExtractor.class);
        });
    }

    @Test
    void theFlagOffOrUnsetExposesNothing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DumClassifierFactory.class)
                .doesNotHaveBean(DumExtractorFactory.class));
        runner.withPropertyValues("dumont.enrichment.llm.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DumClassifierFactory.class)
                        .doesNotHaveBean(DumExtractorFactory.class));
    }
}
