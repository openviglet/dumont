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

package com.viglet.dumont.connector.eds.commons.ext;

import java.util.Locale;

import com.viglet.dumont.connector.eds.commons.DumEDSContext;

/**
 * Resolves a {@link Locale} for a fetched page when the source has no fixed
 * locale and the locale needs to be inferred from the path or HTML.
 *
 * <p>Configured via {@code DumEDSSource.localeClass} — set to the fully
 * qualified class name. Resolution order in the plugin process:
 *
 * <ol>
 *   <li>{@code source.locale} (explicit) — wins when set</li>
 *   <li>{@code source.localeClass} — invoke this interface per page</li>
 *   <li>{@code Locale.US} — final fallback</li>
 * </ol>
 */
public interface DumEDSExtLocaleInterface {
    Locale consume(DumEDSContext context);
}
