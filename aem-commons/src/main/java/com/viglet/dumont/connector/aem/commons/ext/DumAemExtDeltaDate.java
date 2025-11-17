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

import java.util.Date;

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtDeltaDate implements DumAemExtDeltaDateInterface {
  @Override
  public Date consume(DumAemObject aemObject, DumAemConfiguration dumAemConfiguration) {
    log.debug("Executing DumAemExtDeltaDate");
    return DumAemCommonsUtils.getDeltaDate(aemObject);
  }

}
