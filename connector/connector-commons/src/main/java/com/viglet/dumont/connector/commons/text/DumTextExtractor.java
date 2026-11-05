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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * Apache Tika text extraction with a size cap (roadmap <strong>D315</strong>), the one every
 * connector that turns a document into text goes through: the assets connector for its files, the
 * OAI-PMH source for a record's full-text PDF.
 *
 * <p>
 * <strong>Two caps.</strong> A document larger than {@link #maxBytes()} is not parsed at all — a
 * truncated PDF or Office file does not parse, so reading half of one buys nothing — and is
 * reported {@link Outcome#TOO_LARGE}. Text past {@link #maxChars()} characters is cut there and the
 * extraction is {@link Outcome#TRUNCATED}: what was read is kept. The defaults,
 * {@value #DEFAULT_MAX_BYTES} bytes (100 MiB) and {@value #DEFAULT_MAX_CHARS} characters, are
 * generous on purpose: they exist to stop one runaway document from exhausting the heap, not to
 * trim ordinary ones.
 *
 * <p>
 * Which formats parse is whatever Tika parser modules are on the classpath: connector-commons
 * brings the PDF one; a module that reads Office files or runs OCR adds those itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumTextExtractor {

    /** The default byte cap: 100 MiB. */
    public static final long DEFAULT_MAX_BYTES = 100L * 1024 * 1024;
    /** The default character cap: ten million characters. */
    public static final int DEFAULT_MAX_CHARS = 10_000_000;

    private final long maxBytes;
    private final int maxChars;
    private final Consumer<ParseContext> contextSetup;

    private DumTextExtractor(Builder builder) {
        this.maxBytes = builder.maxBytes;
        this.maxChars = builder.maxChars;
        this.contextSetup = builder.contextSetup;
    }

    /** An extractor with the default caps. */
    public static DumTextExtractor withDefaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long maxBytes() {
        return maxBytes;
    }

    public int maxChars() {
        return maxChars;
    }

    /** Extracts a file, refusing it up front when it is larger than the byte cap. */
    public Extraction extract(Path file) {
        return extract(file, new Metadata(), context -> {
        });
    }

    /**
     * Extracts a file into {@code metadata}.
     *
     * @param perCall applied to this call's parse context after the extractor's own setup, e.g. to
     *                register an embedded-document extractor that collects into a local buffer
     */
    public Extraction extract(Path file, Metadata metadata, Consumer<ParseContext> perCall) {
        try {
            long size = Files.size(file);
            if (size > maxBytes) {
                log.info("Not extracting {}: {} bytes is over the cap of {}", file.getFileName(), size, maxBytes);
                return new Extraction("", metadata, Outcome.TOO_LARGE);
            }
            try (TikaInputStream stream = TikaInputStream.get(file, metadata)) {
                return parse(stream, metadata, perCall, null);
            }
        } catch (IOException e) {
            log.warn("Text extraction of {} failed: {}", file.getFileName(), e.getMessage());
            return new Extraction("", metadata, Outcome.FAILED);
        }
    }

    /** Extracts a stream, stopping as {@link Outcome#TOO_LARGE} once it passes the byte cap. */
    public Extraction extract(InputStream input) {
        return extract(input, new Metadata(), context -> {
        });
    }

    /**
     * Extracts a stream into {@code metadata}. The stream is read, not closed.
     *
     * @param perCall applied to this call's parse context after the extractor's own setup
     */
    public Extraction extract(InputStream input, Metadata metadata, Consumer<ParseContext> perCall) {
        Bounded bounded = new Bounded(input, maxBytes);
        try (TikaInputStream stream = TikaInputStream.get(bounded, metadata)) {
            return parse(stream, metadata, perCall, bounded);
        } catch (IOException e) {
            return failed(metadata, bounded, e);
        }
    }

    private Extraction parse(TikaInputStream stream, Metadata metadata, Consumer<ParseContext> perCall,
            Bounded bounded) {
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        contextSetup.accept(context);
        perCall.accept(context);
        BodyContentHandler handler = new BodyContentHandler(maxChars);
        try {
            parser.parse(stream, handler, metadata, context);
            return new Extraction(handler.toString(), metadata, Outcome.COMPLETE);
        } catch (SAXException e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                return new Extraction(handler.toString(), metadata, Outcome.TRUNCATED);
            }
            return failed(metadata, bounded, e);
        } catch (IOException | TikaException e) {
            return failed(metadata, bounded, e);
        }
    }

    private Extraction failed(Metadata metadata, Bounded bounded, Exception e) {
        if (bounded != null && bounded.exceeded) {
            log.info("Not extracting a stream over the cap of {} bytes", maxBytes);
            return new Extraction("", metadata, Outcome.TOO_LARGE);
        }
        log.warn("Text extraction failed: {}", e.getMessage());
        return new Extraction("", metadata, Outcome.FAILED);
    }

    /** How an extraction ended. */
    public enum Outcome {
        /** The whole document was read. */
        COMPLETE,
        /** The text reached the character cap and was cut there. */
        TRUNCATED,
        /** The document was over the byte cap and was not parsed. */
        TOO_LARGE,
        /** The document could not be read or parsed. */
        FAILED
    }

    /**
     * One extraction.
     *
     * @param text     the extracted text; empty when nothing was read
     * @param metadata what Tika read about the document
     * @param outcome  how it ended
     */
    public record Extraction(String text, Metadata metadata, Outcome outcome) {

        /** Whether any text came out, complete or cut at the character cap. */
        public boolean hasText() {
            return (outcome == Outcome.COMPLETE || outcome == Outcome.TRUNCATED) && !text.isBlank();
        }
    }

    /** Reads a stream until it passes the byte cap, then fails the read. */
    private static final class Bounded extends FilterInputStream {
        private final long max;
        private long count;
        private boolean exceeded;

        Bounded(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count(skipped);
            return skipped;
        }

        @Override
        public void close() {
            // The caller owns the stream; Tika closing its wrapper must not close it.
        }

        private void count(long n) throws IOException {
            count += n;
            if (count > max) {
                exceeded = true;
                throw new IOException("over the cap of %d bytes".formatted(max));
            }
        }
    }

    public static final class Builder {
        private long maxBytes = DEFAULT_MAX_BYTES;
        private int maxChars = DEFAULT_MAX_CHARS;
        private Consumer<ParseContext> contextSetup = context -> {
        };

        private Builder() {
        }

        /** The largest document parsed, in bytes; anything larger is {@link Outcome#TOO_LARGE}. */
        public Builder maxBytes(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
            this.maxBytes = maxBytes;
            return this;
        }

        /** The most characters kept; the rest is cut and the extraction is {@link Outcome#TRUNCATED}. */
        public Builder maxChars(int maxChars) {
            if (maxChars <= 0) {
                throw new IllegalArgumentException("maxChars must be positive");
            }
            this.maxChars = maxChars;
            return this;
        }

        /** Parser configuration every call shares, such as OCR or PDF settings. */
        public Builder context(Consumer<ParseContext> contextSetup) {
            this.contextSetup = contextSetup == null ? context -> {
            } : contextSetup;
            return this;
        }

        public DumTextExtractor build() {
            return new DumTextExtractor(this);
        }
    }
}
