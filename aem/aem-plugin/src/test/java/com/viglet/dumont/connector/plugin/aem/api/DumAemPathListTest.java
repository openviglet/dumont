/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.dumont.connector.plugin.aem.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;

@DisplayName("DumAemPathList Tests")
class DumAemPathListTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            List<String> paths = Arrays.asList("/content/path1", "/content/path2");

            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(paths)
                    .event(DumAemEvent.PUBLISHING)
                    .recursive(true)
                    .build();

            assertEquals(2, pathList.getPaths().size());
            assertEquals(DumAemEvent.PUBLISHING, pathList.getEvent());
            assertTrue(pathList.getRecursive());
        }

        @Test
        @DisplayName("Should create instance with empty paths")
        void shouldCreateInstanceWithEmptyPaths() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(Collections.emptyList())
                    .build();

            assertNotNull(pathList.getPaths());
            assertTrue(pathList.getPaths().isEmpty());
        }

        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(null)
                    .event(null)
                    .recursive(null)
                    .build();

            assertNull(pathList.getPaths());
            assertNull(pathList.getEvent());
            assertNull(pathList.getRecursive());
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {

        @Test
        @DisplayName("Should get paths")
        void shouldGetPaths() {
            List<String> paths = Arrays.asList("/content/mysite/en", "/content/mysite/fr");
            DumAemPathList pathList = DumAemPathList.builder()
                    .paths(paths)
                    .build();

            assertEquals(2, pathList.getPaths().size());
            assertTrue(pathList.getPaths().contains("/content/mysite/en"));
            assertTrue(pathList.getPaths().contains("/content/mysite/fr"));
        }

        @Test
        @DisplayName("Should get event")
        void shouldGetEvent() {
            DumAemPathList pathList = DumAemPathList.builder()
                    .event(DumAemEvent.UNPUBLISHING)
                    .build();

            assertEquals(DumAemEvent.UNPUBLISHING, pathList.getEvent());
        }

        @Test
        @DisplayName("Should get recursive flag")
        void shouldGetRecursiveFlag() {
            DumAemPathList pathListTrue = DumAemPathList.builder()
                    .recursive(true)
                    .build();

            DumAemPathList pathListFalse = DumAemPathList.builder()
                    .recursive(false)
                    .build();

            assertTrue(pathListTrue.getRecursive());
            assertFalse(pathListFalse.getRecursive());
        }
    }
}
