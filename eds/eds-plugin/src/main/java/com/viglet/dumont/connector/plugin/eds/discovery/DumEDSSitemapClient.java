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

package com.viglet.dumont.connector.plugin.eds.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches and parses {@code sitemap.xml} from an EDS site. Handles three shapes:
 *
 * <ul>
 *   <li>plain XML {@code <urlset>} — list of {@code <url><loc>...</loc></url>}</li>
 *   <li>gzipped {@code sitemap.xml.gz} — decompressed before parsing</li>
 *   <li>{@code <sitemapindex>} — recurses into each child sitemap</li>
 * </ul>
 *
 * <p>EDS hard limit is 50k URLs / 50MB uncompressed per sitemap; the
 * sitemap-index fan-out is how big sites stay under that. Depth recursion is
 * capped at {@link #MAX_DEPTH} for defense.
 */
@Slf4j
@Component
public class DumEDSSitemapClient {

    public static final int MAX_DEPTH = 5;
    private static final String GZIP_MAGIC_HEADER = "application/gzip";

    private final RestClient.Builder restClientBuilder;

    public DumEDSSitemapClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Fetches the sitemap and returns the discovered set of <em>paths</em> (host
     * stripped, query stripped). Returns an empty set on any failure — sitemap is
     * a best-effort fallback, not a hard dependency.
     */
    public Set<String> fetchPaths(String baseUrl, String sitemapPath, String userAgent) {
        Set<String> paths = new LinkedHashSet<>();
        if (baseUrl == null || baseUrl.isBlank() || sitemapPath == null || sitemapPath.isBlank()) {
            return paths;
        }
        URI uri = buildUri(baseUrl, sitemapPath);
        fetchInto(uri, userAgent, baseUrl, paths, 0);
        return paths;
    }

    private void fetchInto(URI uri, String userAgent, String baseUrl, Set<String> out, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("Sitemap recursion exceeded MAX_DEPTH={}; stopping at {}", MAX_DEPTH, uri);
            return;
        }
        ResponseEntity<byte[]> response = downloadOrNull(uri, userAgent);
        if (response == null || response.getBody() == null || response.getBody().length == 0) {
            log.debug("Empty sitemap response from {}", uri);
            return;
        }
        Document doc = parseDocumentOrNull(uri, response);
        if (doc == null) {
            return;
        }
        Elements children = doc.select("sitemapindex > sitemap > loc");
        if (!children.isEmpty()) {
            recurseIntoChildren(children, userAgent, baseUrl, out, depth);
        } else {
            collectUrls(doc, baseUrl, out);
        }
    }

    private ResponseEntity<byte[]> downloadOrNull(URI uri, String userAgent) {
        try {
            return restClientBuilder
                    .defaultHeader("User-Agent",
                            userAgent == null ? DumEDSIndexClient.DEFAULT_USER_AGENT : userAgent)
                    .defaultHeader("Accept", "*/*")
                    .build()
                    .get().uri(uri).retrieve().toEntity(byte[].class);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch sitemap {}: {}", uri, e.getMessage());
            return null;
        }
    }

    private Document parseDocumentOrNull(URI uri, ResponseEntity<byte[]> response) {
        try {
            byte[] decoded = maybeDecompress(response.getBody(), uri,
                    response.getHeaders().getContentType());
            String xml = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            return Jsoup.parse(xml, uri.toString(), Parser.xmlParser());
        } catch (IOException e) {
            log.warn("Failed to parse sitemap {}: {}", uri, e.getMessage());
            return null;
        }
    }

    private void recurseIntoChildren(Elements children, String userAgent, String baseUrl,
            Set<String> out, int depth) {
        for (Element loc : children) {
            String childUrl = loc.text().trim();
            if (!childUrl.isEmpty()) {
                fetchInto(URI.create(childUrl), userAgent, baseUrl, out, depth + 1);
            }
        }
    }

    private static void collectUrls(Document doc, String baseUrl, Set<String> out) {
        for (Element loc : doc.select("urlset > url > loc")) {
            String path = toPath(loc.text().trim(), baseUrl);
            if (path != null && !path.isBlank()) {
                out.add(path);
            }
        }
    }

    private static byte[] maybeDecompress(byte[] body, URI uri, MediaType contentType)
            throws IOException {
        boolean looksGzipped = uri.getPath().endsWith(".gz")
                || (contentType != null && GZIP_MAGIC_HEADER.equals(contentType.toString()))
                || (body.length >= 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B);
        if (!looksGzipped) {
            return body;
        }
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return IOUtils.toByteArray(gz);
        }
    }

    /**
     * Converts an absolute or root-relative URL into a site path, stripping host
     * and query string. Returns null if the URL is for a different host than the
     * configured base, so foreign sitemaps don't leak into discovery.
     */
    static String toPath(String url, String baseUrl) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URI parsed = new URI(url);
            if (!parsed.isAbsolute()) {
                return parsed.getPath();
            }
            URI base = new URI(baseUrl);
            if (parsed.getHost() != null && base.getHost() != null
                    && !parsed.getHost().equalsIgnoreCase(base.getHost())) {
                return null;
            }
            return parsed.getPath();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    static URI buildUri(String baseUrl, String sitemapPath) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = sitemapPath.startsWith("/") ? sitemapPath : "/" + sitemapPath;
        return URI.create(b + p);
    }
}
