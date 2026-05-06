package com.viglet.dumont.commons.cache;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
public class DumCustomClassCache {
    private DumCustomClassCache() {
        throw new IllegalStateException("Custom Class Cache class");
    }

    private static final Map<String, Object> customClassMap = new ConcurrentHashMap<>();

    private static final AtomicReference<Function<String, Optional<Object>>> externalResolver =
            new AtomicReference<>();

    /**
     * Registers an external resolver consulted before falling back to
     * {@code Class.forName} on the host classpath. Used by the PF4J plugin
     * resolver to instantiate classes from plugin classloaders.
     */
    public static void setExternalResolver(Function<String, Optional<Object>> resolver) {
        externalResolver.set(resolver);
    }

    /**
     * Retrieve an instance of a class from a thread-safe cache. The class will be
     * instantiated if not present.
     *
     * @param className The name of the class that has to be retrieved.
     * @return An Optional of the instance.
     */
    public static Optional<Object> getCustomClassMap(String className) {
        try {
            return Optional.of(customClassMap.computeIfAbsent(className, key -> {
                Function<String, Optional<Object>> resolver = externalResolver.get();
                if (resolver != null) {
                    Optional<Object> external = resolver.apply(key);
                    if (external.isPresent()) {
                        log.info("Custom class {} resolved via external resolver", key);
                        return external.get();
                    }
                }
                log.info("Custom class {} not found in memory, instancing...", key);
                try {
                    return Objects.requireNonNull(Class.forName(key)
                            .getDeclaredConstructor().newInstance());
                } catch (InstantiationException | IllegalAccessException
                        | InvocationTargetException | NoSuchMethodException
                        | ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }));
        } catch (IllegalStateException e) {
            log.error(e.getCause().getMessage(), e.getCause());
            return Optional.empty();
        }
    }
}
