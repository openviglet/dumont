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

/**
 * The grounding contract as a reusable operation (roadmap <strong>D03</strong>).
 *
 * <p>
 * {@link com.viglet.dumont.connector.commons.ground.DumGrounding} strips empty values from an
 * attribute map so an absent field is omitted from the published document, never sent as {@code ""}.
 * The structured-source SPI grounds at record-build time; this package lets the same rule be
 * enforced end-to-end at the indexing boundary for every connector.
 */
package com.viglet.dumont.connector.commons.ground;
