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

package com.viglet.dumont.connector.commons.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.dumont.connector.commons.text.DumTextExtractor.Outcome;

/** Tika extraction with a byte and a character cap (roadmap D315). */
class DumTextExtractorTest {

    private static final String SENTENCE = "Harvested full text from a repository PDF";

    @Test
    void extractsAPdf() throws IOException {
        DumTextExtractor.Extraction extraction = DumTextExtractor.withDefaults()
                .extract(new ByteArrayInputStream(pdf(SENTENCE)));

        assertEquals(Outcome.COMPLETE, extraction.outcome());
        assertTrue(extraction.text().contains(SENTENCE), extraction.text());
        assertTrue(extraction.hasText());
    }

    @Test
    void cutsTextAtTheCharacterCap() {
        String text = "abcdefghij".repeat(100);

        DumTextExtractor.Extraction extraction = DumTextExtractor.builder().maxChars(25).build()
                .extract(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Outcome.TRUNCATED, extraction.outcome());
        assertEquals(25, extraction.text().length());
        assertTrue(extraction.hasText(), "what was read before the cap is kept");
    }

    @Test
    void refusesAStreamOverTheByteCap() throws IOException {
        byte[] pdf = pdf(SENTENCE);

        DumTextExtractor.Extraction extraction = DumTextExtractor.builder().maxBytes(pdf.length / 2).build()
                .extract(new ByteArrayInputStream(pdf));

        assertEquals(Outcome.TOO_LARGE, extraction.outcome());
        assertFalse(extraction.hasText());
    }

    @Test
    void refusesAFileOverTheByteCapWithoutReadingIt(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("big.pdf"), pdf(SENTENCE));

        assertEquals(Outcome.TOO_LARGE, DumTextExtractor.builder().maxBytes(10).build().extract(file).outcome());
        assertTrue(DumTextExtractor.withDefaults().extract(file).text().contains(SENTENCE));
    }

    @Test
    void anUnreadableDocumentFailsQuietly() {
        DumTextExtractor.Extraction extraction = DumTextExtractor.withDefaults()
                .extract(new ByteArrayInputStream("%PDF-1.7 not really".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(Outcome.FAILED, extraction.outcome());
        assertEquals("", extraction.text());
    }

    @Test
    void capsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> DumTextExtractor.builder().maxBytes(0));
        assertThrows(IllegalArgumentException.class, () -> DumTextExtractor.builder().maxChars(0));
    }

    /** A one-page PDF holding {@code text}. */
    static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
