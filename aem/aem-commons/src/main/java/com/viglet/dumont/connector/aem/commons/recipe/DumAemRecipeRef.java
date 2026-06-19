/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.aem.commons.recipe;

/**
 * A reference to a versioned AEM recipe in the {@link DumAemRecipeLibrary} (roadmap
 * <strong>D27</strong>): a {@link #name() name} plus a {@link #version() version}.
 *
 * <p>
 * The AEM-side counterpart of the structured-source {@code DumRecipeRef} (D19) — declared here so
 * {@code aem-commons} can version recipes without depending on the connector commons (the same reason
 * {@code DumAemContentTypeField} mirrors {@code DumStructuredField}). An extension binds to a recipe
 * <em>by reference</em> ({@code course-offer@3}), never by copy, which is what lets a single recipe
 * bump re-apply to every institution that uses it. A bare {@code course-offer} ({@link #LATEST_VERSION})
 * resolves to the highest registered version, so pinning is opt-in: pin to freeze an institution on a
 * known-good recipe, or track {@link #LATEST_VERSION} to ride every bump.
 *
 * @param name    the recipe name (required)
 * @param version the pinned version, or {@link #LATEST_VERSION} to resolve the newest registered version
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record DumAemRecipeRef(String name, int version) {

    /** Sentinel version meaning "resolve the highest registered version". */
    public static final int LATEST_VERSION = 0;

    public DumAemRecipeRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AEM recipe ref name is required");
        }
        name = name.trim();
        if (version < 0) {
            throw new IllegalArgumentException("AEM recipe ref version cannot be negative: " + version);
        }
    }

    public static DumAemRecipeRef of(String name, int version) {
        return new DumAemRecipeRef(name, version);
    }

    /** A ref that tracks the newest registered version of {@code name}. */
    public static DumAemRecipeRef latest(String name) {
        return new DumAemRecipeRef(name, LATEST_VERSION);
    }

    /**
     * Parse a {@code name@version} string ({@code "course-offer@3"}). A bare {@code "course-offer"}
     * (no {@code @version}) is treated as {@link #latest(String)}.
     */
    public static DumAemRecipeRef parse(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("AEM recipe ref is required");
        }
        int at = ref.lastIndexOf('@');
        if (at < 0) {
            return latest(ref.trim());
        }
        String name = ref.substring(0, at).trim();
        String versionText = ref.substring(at + 1).trim();
        try {
            return new DumAemRecipeRef(name, Integer.parseInt(versionText));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("AEM recipe ref version must be an integer: " + ref, e);
        }
    }

    public boolean isLatest() {
        return version == LATEST_VERSION;
    }

    @Override
    public String toString() {
        return isLatest() ? name : name + "@" + version;
    }
}
