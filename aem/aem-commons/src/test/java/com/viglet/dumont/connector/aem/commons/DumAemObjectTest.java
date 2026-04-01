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

package com.viglet.dumont.connector.aem.commons;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEnv;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

@DisplayName("DumAemObject Tests")
class DumAemObjectTest {

    private static final String TEST_PATH = "/content/mysite/en/home";
    private static final String AUTHOR_URL_PREFIX = "https://author.example.com";
    private static final String PUBLISH_URL_PREFIX = "https://publish.example.com";
    private static final String AUTHOR_SN_SITE = "author-site";
    private static final String PUBLISH_SN_SITE = "publish-site";

    private DumAemConfiguration configuration;
    private JSONObject jcrNode;

    @BeforeEach
    void setUp() {
        jcrNode = new JSONObject();
        jcrNode.put("jcr:primaryType", "cq:Page");

        configuration = DumAemConfiguration.builder()
                .authorURLPrefix(AUTHOR_URL_PREFIX)
                .publishURLPrefix(PUBLISH_URL_PREFIX)
                .authorSNSite(AUTHOR_SN_SITE)
                .publishSNSite(PUBLISH_SN_SITE)
                .build();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create object with path, jcrNode and environment")
        void shouldCreateObjectWithPathJcrNodeAndEnvironment() {
            DumAemObject object = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.AUTHOR);

            assertEquals(TEST_PATH, object.getPath());
            assertEquals(DumAemEnv.AUTHOR, object.getEnvironment());
            assertNotNull(object.getJcrNode());
        }

        @Test
        @DisplayName("Should create object from DumAemObjectGeneric")
        void shouldCreateObjectFromDumAemObjectGeneric() {
            DumAemObjectGeneric generic = new DumAemObjectGeneric(TEST_PATH, jcrNode);
            DumAemObject object = new DumAemObject(generic, DumAemEnv.PUBLISHING);

            assertEquals(TEST_PATH, object.getPath());
            assertEquals(DumAemEnv.PUBLISHING, object.getEnvironment());
        }
    }

    @Nested
    @DisplayName("URL Prefix Tests")
    class UrlPrefixTests {

        @Test
        @DisplayName("Should return author URL prefix for AUTHOR environment")
        void shouldReturnAuthorUrlPrefixForAuthorEnvironment() {
            DumAemObject object = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.AUTHOR);

            String urlPrefix = object.getUrlPrefix(configuration);

            assertEquals(AUTHOR_URL_PREFIX, urlPrefix);
        }

        @Test
        @DisplayName("Should return publish URL prefix for PUBLISHING environment")
        void shouldReturnPublishUrlPrefixForPublishingEnvironment() {
            DumAemObject object = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.PUBLISHING);

            String urlPrefix = object.getUrlPrefix(configuration);

            assertEquals(PUBLISH_URL_PREFIX, urlPrefix);
        }
    }

    @Nested
    @DisplayName("SN Site Tests")
    class SnSiteTests {

        @Test
        @DisplayName("Should return author SN site for AUTHOR environment")
        void shouldReturnAuthorSnSiteForAuthorEnvironment() {
            DumAemObject object = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.AUTHOR);

            String snSite = object.getSNSite(configuration);

            assertEquals(AUTHOR_SN_SITE, snSite);
        }

        @Test
        @DisplayName("Should return publish SN site for PUBLISHING environment")
        void shouldReturnPublishSnSiteForPublishingEnvironment() {
            DumAemObject object = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.PUBLISHING);

            String snSite = object.getSNSite(configuration);

            assertEquals(PUBLISH_SN_SITE, snSite);
        }
    }

    @Nested
    @DisplayName("Environment Tests")
    class EnvironmentTests {

        @Test
        @DisplayName("Should get correct environment")
        void shouldGetCorrectEnvironment() {
            DumAemObject authorObject = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.AUTHOR);
            DumAemObject publishObject = new DumAemObject(TEST_PATH, jcrNode, DumAemEnv.PUBLISHING);

            assertEquals(DumAemEnv.AUTHOR, authorObject.getEnvironment());
            assertEquals(DumAemEnv.PUBLISHING, publishObject.getEnvironment());
        }
    }
}
