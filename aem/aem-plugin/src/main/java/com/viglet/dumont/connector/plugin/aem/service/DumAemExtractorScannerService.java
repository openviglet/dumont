package com.viglet.dumont.connector.plugin.aem.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import com.viglet.dumont.connector.aem.commons.ext.DumAemExtAttributeInterface;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentInterface;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtDeltaDateInterface;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtUrlAttributeInterface;

import lombok.extern.slf4j.Slf4j;

/**
 * Scans the classpath for Java classes implementing AEM extractor interfaces
 * and caches the result. Used by the wizard/spec form to autocomplete the
 * className field with classes actually available in the running JVM.
 *
 * Uses ASM-based metadata reading (via Spring's MetadataReaderFactory) to
 * inspect every {@code .class} resource without loading the class itself.
 * This works correctly with Spring Boot's PropertiesLauncher (loader.path)
 * which loads external JARs into a custom URLClassLoader.
 */
@Service
@Slf4j
public class DumAemExtractorScannerService {

    public record ExtractorClass(String fqcn, String simpleName, String packageName, String category) {
    }

    private static final Map<String, Class<?>> CATEGORY_TO_INTERFACE = new LinkedHashMap<>();
    static {
        CATEGORY_TO_INTERFACE.put("attribute", DumAemExtAttributeInterface.class);
        CATEGORY_TO_INTERFACE.put("content", DumAemExtContentInterface.class);
        CATEGORY_TO_INTERFACE.put("url", DumAemExtUrlAttributeInterface.class);
        CATEGORY_TO_INTERFACE.put("delta", DumAemExtDeltaDateInterface.class);
    }

    /** Base packages scanned. Limit scope to keep startup fast. */
    private static final List<String> SCAN_PACKAGES = List.of(
            "com.viglet",
            "br.com",
            "com.adobe",
            "io.viglet");

    private final Map<String, List<ExtractorClass>> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void scanAll() {
        long start = System.currentTimeMillis();
        Map<String, List<ExtractorClass>> byCategory = new LinkedHashMap<>();
        Map<String, AssignableTypeFilter> filters = new LinkedHashMap<>();
        CATEGORY_TO_INTERFACE.forEach((category, iface) -> {
            byCategory.put(category, new ArrayList<>());
            filters.put(category, new AssignableTypeFilter(iface));
        });

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                ClassUtils.getDefaultClassLoader());
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);

        for (String pkg : SCAN_PACKAGES) {
            scanPackage(pkg, resolver, readerFactory, filters, byCategory);
        }

        byCategory.forEach((category, list) -> {
            list.sort((a, b) -> a.simpleName().compareToIgnoreCase(b.simpleName()));
            cache.put(category, Collections.unmodifiableList(list));
            log.info("Found {} extractor class(es) for category '{}'", list.size(), category);
        });

        log.info("Extractor classpath scan completed in {} ms", System.currentTimeMillis() - start);
    }

    private void scanPackage(String pkg, ResourcePatternResolver resolver,
            MetadataReaderFactory readerFactory,
            Map<String, AssignableTypeFilter> filters,
            Map<String, List<ExtractorClass>> byCategory) {
        String pattern = "classpath*:" + pkg.replace('.', '/') + "/**/*.class";
        try {
            Resource[] resources = resolver.getResources(pattern);
            log.debug("Scanning {} resources in package {}", resources.length, pkg);
            for (Resource resource : resources) {
                inspectResource(resource, readerFactory, filters, byCategory);
            }
        } catch (IOException e) {
            log.warn("Failed scanning package {}: {}", pkg, e.getMessage());
        }
    }

    private void inspectResource(Resource resource, MetadataReaderFactory readerFactory,
            Map<String, AssignableTypeFilter> filters,
            Map<String, List<ExtractorClass>> byCategory) {
        if (!resource.isReadable()) return;
        MetadataReader reader = readMetadata(resource, readerFactory);
        if (reader == null) return;
        if (reader.getClassMetadata().isAbstract() || reader.getClassMetadata().isInterface()) {
            return;
        }
        for (Map.Entry<String, AssignableTypeFilter> entry : filters.entrySet()) {
            // AssignableTypeFilter walks the hierarchy and matches superclass interfaces.
            if (matches(entry.getValue(), reader, readerFactory)) {
                byCategory.get(entry.getKey()).add(toExtractorClass(reader, entry.getKey()));
            }
        }
    }

    private MetadataReader readMetadata(Resource resource, MetadataReaderFactory readerFactory) {
        try {
            return readerFactory.getMetadataReader(resource);
        } catch (IOException e) {
            log.trace("Failed to read metadata for {}: {}", resource, e.getMessage());
            return null;
        }
    }

    private boolean matches(AssignableTypeFilter filter, MetadataReader reader,
            MetadataReaderFactory readerFactory) {
        try {
            return filter.match(reader, readerFactory);
        } catch (IOException e) {
            return false;
        }
    }

    private ExtractorClass toExtractorClass(MetadataReader reader, String category) {
        String fqcn = reader.getClassMetadata().getClassName();
        int lastDot = fqcn.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
        String packageName = lastDot >= 0 ? fqcn.substring(0, lastDot) : "";
        return new ExtractorClass(fqcn, simpleName, packageName, category);
    }

    /**
     * Returns extractor classes for the given category, optionally filtered by
     * a substring matching either the simple name or the full FQCN.
     */
    public List<ExtractorClass> list(String category, String query) {
        if (category == null) category = "attribute";
        List<ExtractorClass> all = cache.getOrDefault(category.toLowerCase(Locale.ROOT), List.of());
        if (query == null || query.isBlank()) return all;
        String q = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(c -> c.simpleName().toLowerCase(Locale.ROOT).contains(q)
                        || c.fqcn().toLowerCase(Locale.ROOT).contains(q))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Returns the names of supported categories. */
    public Set<String> categories() {
        return CATEGORY_TO_INTERFACE.keySet();
    }
}
