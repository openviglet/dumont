/*
 *
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

package com.viglet.dumont.connector.assets.commons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import com.viglet.dumont.commons.file.DumFileAttributes;
import com.viglet.dumont.commons.file.DumFileSize;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import com.viglet.dumont.connector.commons.text.DumTextExtractor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumFileUtils {

    /**
     * Checks if the provided URL is allowed to be accessed from the server (SSRF
     * protection).
     * Prevents requests to localhost, private, and reserved address ranges.
     * Customize
     * ALLOWED_DOMAINS or additional logic as needed.
     */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
    // Add allowed domains below, e.g.:
    // "example.com",
    // "sometrustedsource.org"
    );

    private static boolean isAllowedRemoteUrl(URL url) {
        if (url == null) {
            return false;
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        // Allow only HTTP/HTTPS
        String protocol = url.getProtocol();
        if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))) {
            return false;
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            // Block localhost and any local/private addresses
            if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress()
                    || inetAddress.isSiteLocalAddress()
                    || inetAddress.getHostAddress().startsWith("127.")
                    || inetAddress.getHostAddress().equals("0.0.0.0")
                    || inetAddress.getHostAddress().equals("::1")) {
                return false;
            }
            // Optionally match against allowlist of permitted domains
            if (!ALLOWED_DOMAINS.isEmpty()) {
                boolean matched = ALLOWED_DOMAINS.stream().anyMatch(
                        allowed -> host.equalsIgnoreCase(allowed) || host.endsWith("." + allowed));
                if (!matched) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Unknown host in user-supplied URL: {}", url);
            return false;
        }
        return true;
    }

    public static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";
    public static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    public static final String TMP = "tmp";
    public static final String HEAD = "HEAD";

    /**
     * Every extraction here goes through the shared, capped extractor (roadmap D315), with the
     * OCR and inline-image PDF settings this module has always used. Its default caps apply: a file
     * over {@link DumTextExtractor#DEFAULT_MAX_BYTES} bytes (100 MiB) is not parsed, and content past
     * {@link DumTextExtractor#DEFAULT_MAX_CHARS} characters is cut.
     */
    private static final DumTextExtractor EXTRACTOR =
            DumTextExtractor.builder().context(DumFileUtils::configure).build();

    private DumFileUtils() {
        throw new IllegalStateException("Dumont File Utilities class");
    }

    public static DumTikaFileAttributes readFile(String filePath) {
        return readFile(new File(filePath));
    }

    public static DumTikaFileAttributes readFile(File file) {
        if (file.exists()) {
            return parseFile(file);
        } else {
            log.info("File not exists: {}", file.getAbsolutePath());
            return null;
        }
    }

    public static DumTikaFileAttributes parseFile(File file) {
        if (!Files.isReadable(file.toPath())) {
            log.error("Cannot read {}", file.getAbsolutePath());
            return null;
        }
        return getDumTikaFileAttributes(file,
                (metadata, perCall) -> EXTRACTOR.extract(file.toPath(), metadata, perCall));
    }

    /**
     * The file's text: each embedded document's text, then the container's own, cut at the
     * extractor's character cap.
     */
    private static DumTikaFileAttributes getDumTikaFileAttributes(File file, Extraction extraction) {
        StringBuilder contentFile = new StringBuilder();
        EmbeddedDocumentExtractor embeddedDocumentExtractor = new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                    Metadata metadata, ParseContext parseContext, boolean outputHtml)
                    throws IOException {
                parseDocument(stream).ifPresent(contentFile::append);

            }
        };
        Metadata metadata = new Metadata();
        DumTextExtractor.Extraction result = extraction.run(metadata,
                context -> context.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor));
        contentFile.append(result.text());
        if (contentFile.length() > EXTRACTOR.maxChars()) {
            contentFile.setLength(EXTRACTOR.maxChars());
        }
        return new DumTikaFileAttributes(file, contentFile.toString(), metadata);
    }

    public static DumTikaFileAttributes parseFile(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return getDumTikaFileAttributes(null,
                    (metadata, perCall) -> EXTRACTOR.extract(inputStream, metadata, perCall));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /** OCR, and inline images in a PDF, for every parse this module runs. */
    private static void configure(ParseContext parseContext) {
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        parseContext.set(TesseractOCRConfig.class, new TesseractOCRConfig());
        parseContext.set(PDFParserConfig.class, pdfConfig);
    }

    /** One extraction call, given the metadata to fill and the per-call parse context setup. */
    @FunctionalInterface
    private interface Extraction {
        DumTextExtractor.Extraction run(Metadata metadata, Consumer<ParseContext> perCall);
    }

    public static DumFileAttributes documentToText(MultipartFile multipartFile) {
        return Optional.ofNullable(parseFile(multipartFile))
                .map(tikaFileAttributes -> getDumFileAttributes(parseFile(multipartFile),
                        multipartFile.getOriginalFilename(),
                        FilenameUtils.getExtension(multipartFile.getOriginalFilename()),
                        multipartFile.getSize(),
                        getTikaLastModified(tikaFileAttributes).orElseGet(Date::new)))
                .orElseGet(DumFileAttributes::new);
    }

    private static Optional<Date> getTikaLastModified(DumTikaFileAttributes tikaFileAttributes) {
        return Optional.ofNullable(tikaFileAttributes).flatMap(
                t -> Optional.ofNullable(t.getMetadata()).map(m -> m.getDate(DublinCore.MODIFIED)));
    }

    private static void ocrDocumentLog(String documentName) {
        log.info("Processing {} document to text", documentName);
    }

    public static DumFileAttributes urlContentToText(URL url) {
        ocrDocumentLog(url.toString());
        return Optional.ofNullable(getFile(url)).map(f -> {
            f.deleteOnExit();
            return Optional.ofNullable(parseFile(f))
                    .map(tikaFileAttributes -> getDumFileAttributes(tikaFileAttributes,
                            FilenameUtils.getName(url.getPath()),
                            FilenameUtils.getExtension(url.getPath()), f.length(),
                            getLastModified(tikaFileAttributes, url)))
                    .orElseGet(DumFileAttributes::new);
        }).orElseGet(DumFileAttributes::new);
    }

    private static Date getLastModified(DumTikaFileAttributes tikaFileAttributes, URL url) {
        return getTikaLastModified(tikaFileAttributes).orElseGet(() -> getLastModifiedFromUrl(url));
    }

    private static Date getLastModifiedFromUrl(URL url) {
        Date date = new Date();
        if (isAllowedRemoteUrl(url)) {
            try {
                HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
                httpUrlConnection.setRequestMethod(HEAD);
                date = new Date(httpUrlConnection.getLastModified());
                httpUrlConnection.disconnect();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            log.warn("Blocked attempt to access disallowed URL: {}", url);
        }
        return date;
    }

    private static File getFile(URL url) {
        File tempFile = null;
        if (isAllowedRemoteUrl(url)) {
            try {
                tempFile = createTempFile();
                FileUtils.copyURLToFile(url, tempFile, CONNECTION_TIMEOUT_MILLIS,
                        CONNECTION_TIMEOUT_MILLIS);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            log.warn("Blocked attempt to access disallowed URL: {}", url);

        }
        return tempFile;
    }

    private static DumFileAttributes getDumFileAttributes(DumTikaFileAttributes tikaFileAttributes,
            String fileName, String fileExtension, long fileSize, Date lastModified) {
        return Optional.ofNullable(tikaFileAttributes)
                .map(attributes -> DumFileAttributes.builder().content(attributes.getContent())
                        .name(fileName).extension(fileExtension).size(new DumFileSize(fileSize))
                        .title(getTitle(tikaFileAttributes, fileName)).lastModified(lastModified)
                        .metadata(getMetadataMap(tikaFileAttributes)).build())
                .orElseGet(DumFileAttributes::new);
    }

    private static String getTitle(DumTikaFileAttributes tikaFileAttributes, String fileName) {
        return Optional.ofNullable(tikaFileAttributes.getMetadata().get(PDF_DOC_INFO_TITLE))
                .orElse(fileName);
    }

    private static Map<String, String> getMetadataMap(DumTikaFileAttributes file) {
        Map<String, String> metadataMap = new HashMap<>();
        Arrays.stream(file.getMetadata().names())
                .forEach(name -> metadataMap.put(name, file.getMetadata().get(name)));
        return metadataMap;
    }

    /**
     * The text of one document, through the shared capped extractor; empty when it could not be
     * parsed or was over the byte cap.
     */
    public static Optional<String> parseDocument(InputStream stream) throws IOException {
        DumTextExtractor.Extraction extraction = EXTRACTOR.extract(stream);
        return switch (extraction.outcome()) {
            case COMPLETE, TRUNCATED -> Optional.of(extraction.text());
            case TOO_LARGE, FAILED -> Optional.empty();
        };
    }

    private static File createTempFile() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), null,
                DumCommonsUtils.addSubDirToStoreDir(TMP));
    }

}
