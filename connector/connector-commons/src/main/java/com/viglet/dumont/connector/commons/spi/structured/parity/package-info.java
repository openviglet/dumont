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
 * Per-source parity gate for the leg-by-leg sidecar &rarr; native migration (roadmap
 * <strong>D11</strong>).
 *
 * <p>
 * The {@link com.viglet.dumont.connector.commons.spi.structured.parity.DumParityGate} compares a
 * candidate {@code DumStructuredSource} (a native declarative strategy, e.g. one materialized from a
 * D19 SIGAA recipe) against the incumbent baseline (the catalog sidecar / Python run) on the two
 * checks the roadmap names — record count within tolerance and canonical-URL coverage — and returns a
 * {@link com.viglet.dumont.connector.commons.spi.structured.parity.DumParityReport}. A migration leg
 * flips to native only when the report passes; the gate itself is read-only.
 *
 * <p>
 * The gate is connector-agnostic: it speaks only the structured-source SPI, so any source pair (not
 * just catalog) can be gated the same way.
 */
package com.viglet.dumont.connector.commons.spi.structured.parity;
