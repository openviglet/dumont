package com.viglet.dumont.commons.cache;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DumCustomClassCache {
    private DumCustomClassCache() {
        throw new IllegalStateException("Custom Class Cache class");
    }

    private static final Map<String, Object> customClassMap = new ConcurrentHashMap<>();

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
