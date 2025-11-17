package com.viglet.dumont.spring.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;

public class DumPersistenceUtils {
    private DumPersistenceUtils() {
        throw new IllegalStateException("Utility class");
    }

    @NotNull
    public static Sort orderByNameIgnoreCase() {
        return Sort.by(Sort.Order.asc("name").ignoreCase());
    }

    public static Sort orderByTitleIgnoreCase() {
        return Sort.by(Sort.Order.asc("title").ignoreCase());
    }

    public static Sort orderByLanguageIgnoreCase() {
        return Sort.by(Sort.Order.asc("language").ignoreCase());
    }
}
