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
 * Custom extension point to decide, per content, whether its dependencies
 * should be extracted and indexed. Classes implementing this interface are
 * resolved via {@code DumAemSource.dependencyClass} and invoked by
 * {@code DumAemJobService} before the infinity.json/model.json dependency
 * extraction runs.
 * <p>
 * Return {@code true} to include the content's dependencies, {@code false}
 * to skip extraction for that specific content.
 */
public interface DumAemExtDependencyInterface extends ExtensionPoint {
    boolean consume(DumAemObjectGeneric aemObject, DumAemConfiguration dumAemConfiguration);
}
