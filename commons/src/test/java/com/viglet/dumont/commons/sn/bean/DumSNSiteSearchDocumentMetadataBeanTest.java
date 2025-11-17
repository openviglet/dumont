/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.dumont.commons.sn.bean;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DumSNSiteSearchDocumentMetadataBean.
 *
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
class DumSNSiteSearchDocumentMetadataBeanTest {

    @Test
    void testDefaultConstructor() {
        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean();

        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        String href = "https://example.com/document/123";
        String text = "Example Document Title";

        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean(href, text);

        assertThat(metadata.getHref()).isEqualTo(href);
        assertThat(metadata.getText()).isEqualTo(text);
    }

    @Test
    void testBuilderPattern() {
        DumSNSiteSearchDocumentMetadataBean metadata = DumSNSiteSearchDocumentMetadataBean.builder()
                .href("https://viglet.com")
                .text("Viglet Dumont")
                .build();

        assertThat(metadata.getHref()).isEqualTo("https://viglet.com");
        assertThat(metadata.getText()).isEqualTo("Viglet Dumont");
    }

    @Test
    void testBuilderWithNullValues() {
        DumSNSiteSearchDocumentMetadataBean metadata = DumSNSiteSearchDocumentMetadataBean.builder()
                .href(null)
                .text(null)
                .build();

        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();
    }

    @Test
    void testSettersAndGetters() {
        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean();

        metadata.setHref("https://example.org/page");
        metadata.setText("Sample Page");

        assertThat(metadata.getHref()).isEqualTo("https://example.org/page");
        assertThat(metadata.getText()).isEqualTo("Sample Page");
    }

    @Test
    void testToBuilder() {
        DumSNSiteSearchDocumentMetadataBean original = DumSNSiteSearchDocumentMetadataBean.builder()
                .href("https://original.com")
                .text("Original Text")
                .build();

        DumSNSiteSearchDocumentMetadataBean modified = original.toBuilder()
                .text("Modified Text")
                .build();

        // Original should remain unchanged
        assertThat(original.getHref()).isEqualTo("https://original.com");
        assertThat(original.getText()).isEqualTo("Original Text");

        // Modified should have updated text but same href
        assertThat(modified.getHref()).isEqualTo("https://original.com");
        assertThat(modified.getText()).isEqualTo("Modified Text");
    }

    @Test
    void testImplementsSerializable() {
        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean();

        assertThat(metadata).isInstanceOf(Serializable.class);
    }

    @Test
    void testUpdateFields() {
        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean();

        // Start with null values
        assertThat(metadata.getHref()).isNull();
        assertThat(metadata.getText()).isNull();

        // Set initial values
        metadata.setHref("https://first.com");
        metadata.setText("First Text");

        assertThat(metadata.getHref()).isEqualTo("https://first.com");
        assertThat(metadata.getText()).isEqualTo("First Text");

        // Update values
        metadata.setHref("https://second.com");
        metadata.setText("Second Text");

        assertThat(metadata.getHref()).isEqualTo("https://second.com");
        assertThat(metadata.getText()).isEqualTo("Second Text");
    }

    @Test
    void testEmptyStringValues() {
        DumSNSiteSearchDocumentMetadataBean metadata = DumSNSiteSearchDocumentMetadataBean.builder()
                .href("")
                .text("")
                .build();

        assertThat(metadata.getHref()).isEmpty();
        assertThat(metadata.getText()).isEmpty();
    }

    @Test
    void testLongUrlAndText() {
        String longUrl = "https://example.com/very/long/path/to/document/with/many/segments/and/parameters?param1=value1&param2=value2&param3=value3";
        String longText = "This is a very long document title that might contain multiple sentences and special characters like émotions, números, and símbolos.";

        DumSNSiteSearchDocumentMetadataBean metadata = DumSNSiteSearchDocumentMetadataBean.builder()
                .href(longUrl)
                .text(longText)
                .build();

        assertThat(metadata.getHref()).isEqualTo(longUrl);
        assertThat(metadata.getText()).isEqualTo(longText);
        assertThat(metadata.getHref().length()).isGreaterThan(100);
        assertThat(metadata.getText().length()).isGreaterThan(100);
    }

    @Test
    void testSpecialCharacters() {
        String specialHref = "https://example.com/документ/文档/مستند";
        String specialText = "Document with émotions (1), números [2], and símbolos {3}";

        DumSNSiteSearchDocumentMetadataBean metadata = new DumSNSiteSearchDocumentMetadataBean();
        metadata.setHref(specialHref);
        metadata.setText(specialText);

        assertThat(metadata.getHref()).isEqualTo(specialHref);
        assertThat(metadata.getText()).isEqualTo(specialText);
    }
}