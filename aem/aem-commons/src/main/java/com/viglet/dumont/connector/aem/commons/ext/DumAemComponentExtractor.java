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
