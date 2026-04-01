/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.assets.sample;

/**
 * Sample class for the Assets Plugin.
 * <p>
 * This module provides sample export configurations for the Assets connector.
 * See the {@code scripts/sample/export/} directory for example JSON files.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public final class DumAssetsSampleInfo {

    public static final String SAMPLE_NAME = "Dumont Assets Plugin Sample";
    public static final String SAMPLE_VERSION = "2026.2";

    private DumAssetsSampleInfo() {
        throw new IllegalStateException("Utility class");
    }

    public static String info() {
        return SAMPLE_NAME + " v" + SAMPLE_VERSION;
    }
}
