/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viglet.dumont.connector.plugin.assets.api.dto;

import java.util.Collection;
import java.util.HashSet;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumAssetsSourceApi} create/update (SonarQube S4684) — a plain POJO
 * mirroring the writable fields of {@code DumAssetsSource} so the persistent entity is never bound
 * directly from the request body. {@code id} is accepted only to validate the path variable on
 * update; it is never applied to the entity.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumAssetsSourceRequest {

    private String id;
    private String name;
    private String description;
    private String sourceDir;
    private String prefixFromReplace;
    private String prefixToReplace;
    private String site;
    private String locale;
    private String contentType;
    private int chunk;
    private boolean typeInId;
    private String fileSizeField;
    private String fileExtensionField;
    private String contentField;
    private String encoding;
    private boolean showOutput;
    private Collection<String> turSNSites = new HashSet<>();
}
