/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * D339: a client holding the connector's {@code Key} writes every API without a CSRF token.
 * The plugins are not on this module's classpath, so the controllers are found by walking the
 * reactor's sources: a plugin added later is checked the day it lands.
 */
class DumCsrfExemptionTest {

    private static final Pattern MAPPING =
            Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"(/api/[^\"]*)\"");

    private static final Set<String> SKIPPED = Set.of("node_modules", "target", ".git", "store", "dist");

    private final RequestMatcher exempt = new OrRequestMatcher(DumWebConfiguration.csrfExemptions());

    @Test
    void everyApiTakesAKeyHoldingWriteWithoutAToken() throws IOException {
        TreeSet<String> paths = apiMappings();
        // The walk itself is under test: an empty or partial scan would pass vacuously.
        assertTrue(paths.contains("/api/v2/catalog/source"), () -> "scan missed catalog: " + paths);
        assertTrue(paths.contains("/api/v2/eds/source"), () -> "scan missed eds: " + paths);

        List<String> refused = new ArrayList<>();
        for (String path : paths) {
            MockHttpServletRequest post = post(path);
            post.addHeader("Key", "any");
            if (!exempt.matches(post)) {
                refused.add(path);
            }
        }
        assertTrue(refused.isEmpty(), () -> "a Key-holding POST still needs a CSRF token on " + refused);
    }

    @Test
    void theKeyAsQueryParameterCountsToo() {
        MockHttpServletRequest post = post("/api/v2/catalog/source");
        post.setParameter("Key", "any");
        assertTrue(exempt.matches(post));
    }

    @Test
    void aBrowserWriteWithoutTheKeyStillNeedsTheToken() {
        assertFalse(exempt.matches(post("/api/v2/catalog/source")));
        assertFalse(exempt.matches(post("/api/v2/plugins")));
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    private static TreeSet<String> apiMappings() throws IOException {
        TreeSet<String> paths = new TreeSet<>();
        Files.walkFileTree(reactorRoot(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = String.valueOf(dir.getFileName());
                return SKIPPED.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")
                        && file.toString().replace('\\', '/').contains("/src/main/java/")) {
                    Matcher m = MAPPING.matcher(Files.readString(file));
                    while (m.find()) {
                        paths.add(m.group(1));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return paths;
    }

    private static Path reactorRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("catalog")) && Files.isDirectory(dir.resolve("connector"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("reactor root not found from " + Path.of("").toAbsolutePath());
    }
}
