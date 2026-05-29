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

package com.viglet.dumont.connector.eds.commons.domain;

import java.util.List;
import java.util.Map;

/**
 * Single page of an Edge Delivery Services sheet response
 * (e.g. /query-index.json). EDS exposes every spreadsheet-derived JSON resource
 * with the same envelope: {@code total / offset / limit / columns / data /
 * :type}. Missing fields are tolerated; absent attributes within a row are
 * simply omitted by EDS (never null, never empty string).
 */
public record DumEDSSheetPage(
        int total,
        int offset,
        int limit,
        List<String> columns,
        List<Map<String, String>> rows,
        String sheetType) {

    public static final String TYPE_SHEET = "sheet";
    public static final String TYPE_MULTI_SHEET = "multi-sheet";

    public boolean isSingleSheet() {
        return TYPE_SHEET.equals(sheetType);
    }

    public boolean isMultiSheet() {
        return TYPE_MULTI_SHEET.equals(sheetType);
    }

    public boolean hasMore() {
        return offset + rows.size() < total;
    }

    public int nextOffset() {
        return offset + rows.size();
    }
}
