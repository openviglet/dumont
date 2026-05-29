/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.eds.sample.ext;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtInterface;
import com.viglet.turing.client.sn.TurMultiValue;

class DumEDSExtMetaNameTest {

    @Test
    void defaultInstanceReadsKeywordsAsMultiValue() {
        Document doc = Jsoup.parse("""
                <html><head>
                  <meta name="keywords" content="finance, pix, conta-digital">
                </head><body></body></html>
                """);
        DumEDSExtMetaName ext = new DumEDSExtMetaName();

        Optional<TurMultiValue> result = ext.consume(
                DumEDSContext.builder().document(doc).build());

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly("finance", "pix", "conta-digital");
    }

    @Test
    void emptyOptionalWhenMetaAbsent() {
        Document doc = Jsoup.parse("<html><head></head><body></body></html>");
        DumEDSExtMetaName ext = new DumEDSExtMetaName();

        Optional<TurMultiValue> result = ext.consume(
                DumEDSContext.builder().document(doc).build());

        assertThat(result).isEmpty();
    }

    @Test
    void emptyOptionalWhenContentIsBlank() {
        Document doc = Jsoup.parse("""
                <html><head><meta name="keywords" content="  "></head></html>
                """);
        DumEDSExtMetaName ext = new DumEDSExtMetaName();

        Optional<TurMultiValue> result = ext.consume(
                DumEDSContext.builder().document(doc).build());

        assertThat(result).isEmpty();
    }

    @Test
    void nullContextOrDocumentReturnsEmpty() {
        DumEDSExtMetaName ext = new DumEDSExtMetaName();

        assertThat(ext.consume(null)).isEmpty();
        assertThat(ext.consume(DumEDSContext.builder().document(null).build())).isEmpty();
    }

    @Test
    void subclassesCanTargetCustomMetaName() {
        Document doc = Jsoup.parse("""
                <html><head>
                  <meta name="x-section" content="finance">
                  <meta name="keywords" content="ignored">
                </head></html>
                """);

        DumEDSExtMetaName ext = new DumEDSExtMetaName() {
            @Override
            protected String metaName() {
                return "x-section";
            }
        };

        Optional<TurMultiValue> result = ext.consume(
                DumEDSContext.builder().document(doc).build());

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly("finance");
    }

    @Test
    void implementsTheExtensionInterface() {
        assertThat(new DumEDSExtMetaName()).isInstanceOf(DumEDSExtInterface.class);
    }
}
