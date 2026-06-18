/*
 *
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

package com.viglet.dumont.connector.aem.commons.ext;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;

/**
 * A small, single-responsibility extractor that contributes one component's
 * (or one concern's) field values to the target attribute map.
 * <p>
 * This is the unit of the AEM component-extractor SPI: instead of a single
 * monolithic {@code extractAttributes} method fusing template routing,
 * component-tree traversal and field mapping, each component (banner, course,
 * teacher, event, news, content fragment…) becomes its own
 * {@code DumAemComponentExtractor}, registered with a
 * {@link DumAemExtModelJsonBase} via
 * {@link DumAemExtModelJsonBase#componentExtractors()} and composed per page.
 * <p>
 * Each extractor is independently unit-testable: it operates only on the parsed
 * model, the {@link DumAemModelJsonQuery} helper and the {@link DumAemObject},
 * and mutates the shared {@link DumAemAttrMap}. Contributions are additive and
 * order-independent for distinct attribute names; when several extractors target
 * the same attribute, use the {@code append}/{@code set} semantics of
 * {@link DumAemAttrMap} to control merge behaviour.
 *
 * <pre>{@code
 * public class MyNewsExtractor implements DumAemComponentExtractor<MyModel> {
 *     @Override
 *     public void contribute(MyModel model, DumAemModelJsonQuery query,
 *             DumAemObject aemObject, DumAemAttrMap attrValues) {
 *         query.component("my-app/components/news", MyNews.class)
 *             .first()
 *             .attr("date", MyNews::getDate)
 *             .attr("title", MyNews::getTitle)
 *             .into(attrValues);
 *     }
 * }
 * }</pre>
 *
 * <h2>Concurrency contract — stateless during extraction</h2>
 * The owning {@link DumAemExtModelJsonBase} is resolved once and <strong>cached as a
 * singleton</strong> (see {@code com.viglet.dumont.commons.cache.DumCustomClassCache}), then
 * reused across every page of a run. Under {@code dumont.reactive.indexing=true} ids are
 * extracted in parallel (see {@code DumBoundedExtraction}), so that <em>one</em> extractor
 * instance — and every component extractor it composes — has {@link #contribute} invoked
 * <strong>concurrently from many threads</strong>, each with its own {@code model},
 * {@code aemObject} and {@code attrValues}.
 * <p>
 * An implementation MUST therefore be <strong>stateless during extraction</strong>: derive
 * everything from the four {@code contribute} arguments plus local variables, and write only to
 * the {@code attrValues} it is handed. Never stash per-page data — the current model, a partial
 * result, a "last seen" value — in a mutable instance field between calls; a second thread will
 * overwrite it and corrupt the output of both. Immutable fields set at construction
 * (configuration, the model class, sub-extractor lists) are safe; <em>per-invocation</em> mutable
 * instance state is the violation. The same contract binds a monolithic
 * {@link DumAemExtModelJsonBase#extractAttributes} override (e.g. a single content-extractor
 * fusing the whole tree). {@code DumAemExtractorConcurrencyTest} enforces it by asserting that
 * parallel extraction over a shared instance yields the identical attribute maps as sequential
 * extraction; a deliberately stateful extractor diverges and is caught.
 *
 * @param <T> the root model bean type for the model.json response
 */
@FunctionalInterface
public interface DumAemComponentExtractor<T> {

    /**
     * Contributes this component's field values to the target attribute map.
     *
     * @param model      the deserialized root model object
     * @param query      a query helper for finding AEM components by type via JsonPath
     * @param aemObject  the AEM content object being indexed
     * @param attrValues the shared attribute map to populate with extracted values
     */
    void contribute(T model, DumAemModelJsonQuery query, DumAemObject aemObject,
            DumAemAttrMap attrValues);
}
