/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemContentMapping;

/**
 * Verifies the shared XF/CF dependency-harvest seam (roadmap <strong>D28</strong>): the gating flags
 * and the self-reference/extension/JCR filtering that keeps only canonical Experience Fragment /
 * Content Fragment roots.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemDependencyService Tests (D28)")
class DumAemDependencyServiceTest {

    @Mock
    private DumAemContentDefinitionService contentDefinitionService;

    private final DumAemContentMapping mapping = DumAemContentMapping.builder().build();

    private DumAemObjectGeneric objectWith(String currentPath, String... contentValues) {
        JSONObject jcrNode = new JSONObject();
        int i = 0;
        for (String value : contentValues) {
            jcrNode.put("ref" + i++, value);
        }
        DumAemObjectGeneric generic = mock(DumAemObjectGeneric.class);
        lenient().when(generic.getPath()).thenReturn(currentPath);
        lenient().when(generic.getJcrNode()).thenReturn(jcrNode);
        return generic;
    }

    private DumAemConfiguration config() {
        DumAemConfiguration config = mock(DumAemConfiguration.class);
        // No publish prefix and an unreachable author URL so the model.json fetch returns empty
        // without contacting a live AEM — the harvest then reflects only the infinity.json refs.
        lenient().when(config.getPublishURLPrefix()).thenReturn(null);
        lenient().when(config.getUrl()).thenReturn("http://127.0.0.1:1");
        return config;
    }

    @Test
    @DisplayName("returns empty when dependency indexing is disabled (no model.json request)")
    void disabledReturnsEmpty() {
        DumAemDependencyService service = new DumAemDependencyService(contentDefinitionService, false);

        Set<String> deps = service.collectDependencies(
                objectWith("/content/site/en/page", "/content/experience-fragments/site/header"),
                config(), mapping);

        assertTrue(deps.isEmpty());
    }

    @Test
    @DisplayName("returns empty when the custom dependencyClass opts out")
    void optOutReturnsEmpty() {
        DumAemDependencyService service = new DumAemDependencyService(contentDefinitionService, true);
        when(contentDefinitionService.shouldIndexDependencies(any(), any(), any())).thenReturn(false);

        Set<String> deps = service.collectDependencies(
                objectWith("/content/site/en/page", "/content/experience-fragments/site/header"),
                config(), mapping);

        assertTrue(deps.isEmpty());
    }

    @Test
    @DisplayName("keeps only canonical XF/CF roots, dropping pages, renditions and JCR sub-trees")
    void keepsOnlyExperienceAndContentFragmentRoots() {
        DumAemDependencyService service = new DumAemDependencyService(contentDefinitionService, true);
        when(contentDefinitionService.shouldIndexDependencies(any(), any(), any())).thenReturn(true);

        Set<String> deps = service.collectDependencies(
                objectWith("/content/site/en/page",
                        "/content/experience-fragments/site/header", // kept
                        "/content/dam/site/article-fragment", // kept
                        "/content/site/en/another-page", // dropped: not XF/CF
                        "/content/experience-fragments/site/header.html", // dropped: extension
                        "/content/dam/site/image.jpg", // dropped: extension
                        "/content/experience-fragments/site/footer/jcr:content/root", // dropped: jcr
                        "/content/dam/site/_jcr_content/data"), // dropped: url-encoded jcr
                config(), mapping);

        assertEquals(Set.of("/content/experience-fragments/site/header",
                "/content/dam/site/article-fragment"), deps);
    }
}
