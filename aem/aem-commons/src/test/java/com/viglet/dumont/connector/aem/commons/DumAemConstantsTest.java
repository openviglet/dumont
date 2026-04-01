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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DumAemConstants Tests")
class DumAemConstantsTest {

    @Test
    @DisplayName("Should have correct CQ_TAGS constant")
    void shouldHaveCorrectCqTagsConstant() {
        assertEquals("cq:tags", DumAemConstants.CQ_TAGS);
    }

    @Test
    @DisplayName("Should have correct DEFAULT constant")
    void shouldHaveCorrectDefaultConstant() {
        assertEquals("default", DumAemConstants.DEFAULT);
    }

    @Test
    @DisplayName("Should have correct TEXT constant")
    void shouldHaveCorrectTextConstant() {
        assertEquals("text", DumAemConstants.TEXT);
    }

    @Test
    @DisplayName("Should have correct JCR constant")
    void shouldHaveCorrectJcrConstant() {
        assertEquals("jcr:", DumAemConstants.JCR);
    }

    @Test
    @DisplayName("Should have correct JSON constant")
    void shouldHaveCorrectJsonConstant() {
        assertEquals(".json", DumAemConstants.JSON);
    }

    @Test
    @DisplayName("Should have correct SLING constant")
    void shouldHaveCorrectSlingConstant() {
        assertEquals("sling:", DumAemConstants.SLING);
    }

    @Test
    @DisplayName("Should have correct DATE_FORMAT constant")
    void shouldHaveCorrectDateFormatConstant() {
        assertEquals("yyyy-MM-dd'T'HH:mm:ss'Z'", DumAemConstants.DATE_FORMAT);
    }

    @Test
    @DisplayName("Should have correct HTML constant")
    void shouldHaveCorrectHtmlConstant() {
        assertEquals(".html", DumAemConstants.HTML);
    }

    @Test
    @DisplayName("Should have correct ACTIVATE constant")
    void shouldHaveCorrectActivateConstant() {
        assertEquals("Activate", DumAemConstants.ACTIVATE);
    }

    @Test
    @DisplayName("Should have correct CQ_LAST_REPLICATED_PUBLISH constant")
    void shouldHaveCorrectCqLastReplicatedPublishConstant() {
        assertEquals("cq:lastReplicated_publish", DumAemConstants.CQ_LAST_REPLICATED_PUBLISH);
    }

    @Test
    @DisplayName("Should have correct CQ_LAST_REPLICATED constant")
    void shouldHaveCorrectCqLastReplicatedConstant() {
        assertEquals("cq:lastReplicated", DumAemConstants.CQ_LAST_REPLICATED);
    }

    @Test
    @DisplayName("Should have correct JCR_CONTENT constant")
    void shouldHaveCorrectJcrContentConstant() {
        assertEquals("jcr:content", DumAemConstants.JCR_CONTENT);
    }

    @Test
    @DisplayName("Should have correct ONCE constant")
    void shouldHaveCorrectOnceConstant() {
        assertEquals("once", DumAemConstants.ONCE);
    }

    @Test
    @DisplayName("Should have correct CONTENT_FRAGMENT constant")
    void shouldHaveCorrectContentFragmentConstant() {
        assertEquals("contentFragment", DumAemConstants.CONTENT_FRAGMENT);
    }

    @Test
    @DisplayName("Should have correct CQ_LAST_REPLICATION_ACTION constant")
    void shouldHaveCorrectCqLastReplicationActionConstant() {
        assertEquals("cq:lastReplicationAction", DumAemConstants.CQ_LAST_REPLICATION_ACTION);
    }

    @Test
    @DisplayName("Should have correct CQ_LAST_REPLICATION_ACTION_PUBLISH constant")
    void shouldHaveCorrectCqLastReplicationActionPublishConstant() {
        assertEquals("cq:lastReplicationAction_publish", DumAemConstants.CQ_LAST_REPLICATION_ACTION_PUBLISH);
    }

    @Test
    @DisplayName("Should have correct CQ_LAST_MODIFIED constant")
    void shouldHaveCorrectCqLastModifiedConstant() {
        assertEquals("cq:lastModified", DumAemConstants.CQ_LAST_MODIFIED);
    }

    @Test
    @DisplayName("Should have correct CQ_MODEL constant")
    void shouldHaveCorrectCqModelConstant() {
        assertEquals("cq:model", DumAemConstants.CQ_MODEL);
    }

    @Test
    @DisplayName("Should have correct CQ_TEMPLATE constant")
    void shouldHaveCorrectCqTemplateConstant() {
        assertEquals("cq:template", DumAemConstants.CQ_TEMPLATE);
    }

    @Test
    @DisplayName("Should have correct DATA_FOLDER constant")
    void shouldHaveCorrectDataFolderConstant() {
        assertEquals("data", DumAemConstants.DATA_FOLDER);
    }

    @Test
    @DisplayName("Should have correct DATE_JSON_FORMAT constant")
    void shouldHaveCorrectDateJsonFormatConstant() {
        assertEquals("E MMM dd yyyy HH:mm:ss 'GMT'Z", DumAemConstants.DATE_JSON_FORMAT);
    }

    @Test
    @DisplayName("Should have correct EMPTY_VALUE constant")
    void shouldHaveCorrectEmptyValueConstant() {
        assertEquals("", DumAemConstants.EMPTY_VALUE);
    }

    @Test
    @DisplayName("Should have correct STATIC_FILE constant")
    void shouldHaveCorrectStaticFileConstant() {
        assertEquals("static-file", DumAemConstants.STATIC_FILE);
    }

    @Test
    @DisplayName("Should have correct SITE constant")
    void shouldHaveCorrectSiteConstant() {
        assertEquals("site", DumAemConstants.SITE);
    }

    @Test
    @DisplayName("Should have correct DATA_MASTER constant")
    void shouldHaveCorrectDataMasterConstant() {
        assertEquals("data/master", DumAemConstants.DATA_MASTER);
    }

    @Test
    @DisplayName("Should have correct METADATA constant")
    void shouldHaveCorrectMetadataConstant() {
        assertEquals("metadata", DumAemConstants.METADATA);
    }

    @Test
    @DisplayName("Should have correct STATIC_FILE_SUB_TYPE constant")
    void shouldHaveCorrectStaticFileSubTypeConstant() {
        assertEquals("STATIC_FILE", DumAemConstants.STATIC_FILE_SUB_TYPE);
    }

    @Test
    @DisplayName("Should have correct CQ_PAGE constant")
    void shouldHaveCorrectCqPageConstant() {
        assertEquals("cq:Page", DumAemConstants.CQ_PAGE);
    }

    @Test
    @DisplayName("Should have correct DAM_ASSET constant")
    void shouldHaveCorrectDamAssetConstant() {
        assertEquals("dam:Asset", DumAemConstants.DAM_ASSET);
    }

    @Test
    @DisplayName("Should have correct CQ constant")
    void shouldHaveCorrectCqConstant() {
        assertEquals("cq:", DumAemConstants.CQ);
    }

    @Test
    @DisplayName("Should have correct AEM constant")
    void shouldHaveCorrectAemConstant() {
        assertEquals("AEM", DumAemConstants.AEM);
    }

    @Test
    @DisplayName("Should have correct REP constant")
    void shouldHaveCorrectRepConstant() {
        assertEquals("rep:", DumAemConstants.REP);
    }
}
