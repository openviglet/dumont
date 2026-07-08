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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.dumont.connector.commons.spi.structured.preview.DumInMemoryPreviewSnapshotStore;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshotStore;

/**
 * App-supplied beans for the structured-source SPI storage seams.
 *
 * <p>
 * The structured-source commons keeps its stores storage-agnostic — the interface says "the app
 * supplies the implementation". Coverage (D15) and provenance (D16) are provided by JPA-backed
 * {@code @Repository} beans; the preview baseline store (D14) has no persistence yet, so the default
 * is the process-local {@link DumInMemoryPreviewSnapshotStore}. Every structured plugin
 * ({@code DumAemPlugin}, the assets/db plugins, the inferred plugin) autowires this single bean, so a
 * baseline captured by one index run is the one a later preview diffs against.
 */
@Configuration
public class DumStructuredStoreConfiguration {

    @Bean
    DumPreviewSnapshotStore previewSnapshotStore() {
        return new DumInMemoryPreviewSnapshotStore();
    }
}
