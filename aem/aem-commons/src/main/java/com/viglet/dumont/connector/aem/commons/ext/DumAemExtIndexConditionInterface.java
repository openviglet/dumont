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

import org.pf4j.ExtensionPoint;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

/**
 * Custom extension point to decide, per content, whether the content should be
 * indexed at all. Classes implementing this interface are resolved via
 * {@code DumAemSource.indexConditionClass} and invoked by
 * {@code DumAemJobService} while deciding if an object is eligible for
 * indexing, after the root-path and content-type gates.
 * <p>
 * Return {@code true} to index the content, {@code false} to skip it. A common
 * use case is excluding pages by their {@code cq:template}
 * ({@link DumAemObjectGeneric#getTemplate()}).
 */
public interface DumAemExtIndexConditionInterface extends ExtensionPoint {
    boolean consume(DumAemObjectGeneric aemObject, DumAemConfiguration dumAemConfiguration);
}
