/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.commons.sn.bean;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@RequiredArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
public class DumSNSiteSearchQueryContextBean implements Serializable {
	private int count;
	private String index;
	private int limit;
	private int offset;
	private int page;
	private int pageCount;
	private int pageEnd;
	private int pageStart;
	private long responseTime;
	private DumSNSiteSearchQueryContextQueryBean query;
	private DumSNSiteSearchDefaultFieldsBean defaultFields;
	private String facetType;
	private String facetItemType;
}
