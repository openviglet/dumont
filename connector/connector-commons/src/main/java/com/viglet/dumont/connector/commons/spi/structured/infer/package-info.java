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
 * AI-assisted source inference (roadmap <strong>D13</strong>).
 *
 * <p>
 * Given a URL, the {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceInferrer}
 * fetches a sample, classifies the source into one of the D02
 * {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceShape shapes} and proposes
 * an editable {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceDraft} — a
 * strategy config plus a field manifest. The draft is a <strong>starting point a human edits and
 * confirms</strong>; it never auto-publishes.
 *
 * <p>
 * The language-model call sits behind the
 * {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceInferenceModel} seam, so
 * this module carries no model wiring; the production adapter in {@code connector-app} reuses Turing's
 * existing LLM surface ({@code POST /api/v2/summary}), exactly like the D04 classifier. Once a human
 * confirms, the
 * {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceDraftMaterializer} turns
 * the draft into a live
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource} the generic plugin
 * indexes end-to-end.
 *
 * <p>
 * <strong>Grounding.</strong> Inference proposes <em>structure</em> (shape, selectors, field types),
 * never content — values always come from the live extraction. It may omit a field it cannot ground;
 * it never fabricates one, and any failure degrades to an
 * {@link com.viglet.dumont.connector.commons.spi.structured.infer.DumSourceShape#UNKNOWN} draft.
 */
package com.viglet.dumont.connector.commons.spi.structured.infer;
