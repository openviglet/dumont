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

package com.viglet.dumont.connector.plugin.eds.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSSheetPage;

/**
 * Parser for the Edge Delivery Services sheet response envelope:
 *
 * <pre>
 * {
 *   "total": 35,
 *   "offset": 0,
 *   "limit": 35,
 *   "columns": ["path","title","image", ...],
 *   "data":   [ { "path": "/foo", "title": "Foo" }, { "path": "/bar" } ],
 *   ":type":  "sheet"
 * }
 * </pre>
 *
 * Rules:
 * <ul>
 *   <li>Attributes with empty values are typically <strong>absent</strong> from a row's JSON
 *       object (not present as {@code null} or empty string). The parser tolerates both.</li>
 *   <li>{@code :type} may be {@code sheet} or {@code multi-sheet}. Multi-sheet (v1: not
 *       supported) raises {@link UnsupportedSheetTypeException}.</li>
 *   <li>Missing envelope fields fall back to safe defaults: {@code total}/{@code offset}
 *       default to 0, {@code limit} defaults to data length, {@code columns} defaults to
 *       the union of keys observed in rows.</li>
 * </ul>
 */
@Component
public class DumEDSSheetParser {

    public static final String FIELD_TOTAL = "total";
    public static final String FIELD_OFFSET = "offset";
    public static final String FIELD_LIMIT = "limit";
    public static final String FIELD_COLUMNS = "columns";
    public static final String FIELD_DATA = "data";
    public static final String FIELD_TYPE = ":type";

    public DumEDSSheetPage parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("EDS sheet payload is null or blank");
        }
        JSONObject envelope;
        try {
            envelope = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Not a valid EDS sheet JSON envelope", e);
        }

        String sheetType = envelope.optString(FIELD_TYPE, DumEDSSheetPage.TYPE_SHEET);
        if (DumEDSSheetPage.TYPE_MULTI_SHEET.equals(sheetType)) {
            throw new UnsupportedSheetTypeException(
                    "multi-sheet payloads are not supported in this version");
        }

        JSONArray data = envelope.optJSONArray(FIELD_DATA);
        List<Map<String, String>> rows = parseRows(data);

        List<String> columns = parseColumns(envelope.optJSONArray(FIELD_COLUMNS), rows);

        int offset = envelope.optInt(FIELD_OFFSET, 0);
        int limit = envelope.optInt(FIELD_LIMIT, rows.size());
        int total = envelope.optInt(FIELD_TOTAL, rows.size());

        return new DumEDSSheetPage(total, offset, limit, columns, rows, sheetType);
    }

    private List<Map<String, String>> parseRows(JSONArray data) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (data == null) {
            return rows;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.optJSONObject(i);
            if (row == null) {
                continue;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            for (String key : row.keySet()) {
                Object value = row.opt(key);
                if (value == null || JSONObject.NULL.equals(value)) {
                    continue;
                }
                String coerced = value.toString();
                if (coerced.isEmpty()) {
                    continue;
                }
                entry.put(key, coerced);
            }
            rows.add(entry);
        }
        return rows;
    }

    private List<String> parseColumns(JSONArray columns, List<Map<String, String>> rows) {
        List<String> result = explicitColumns(columns);
        if (result.isEmpty()) {
            result = columnsFromRows(rows);
        }
        return List.copyOf(result);
    }

    /** Column names declared in the sheet's {@code columns} array (blanks skipped). */
    private static List<String> explicitColumns(JSONArray columns) {
        List<String> result = new ArrayList<>();
        if (columns != null) {
            for (int i = 0; i < columns.length(); i++) {
                String column = columns.optString(i, null);
                if (column != null && !column.isBlank()) {
                    result.add(column);
                }
            }
        }
        return result;
    }

    /** Fallback column order derived from the row keys, first-seen order, de-duplicated. */
    private static List<String> columnsFromRows(List<Map<String, String>> rows) {
        List<String> result = new ArrayList<>();
        for (Map<String, String> row : rows) {
            for (String key : row.keySet()) {
                if (!result.contains(key)) {
                    result.add(key);
                }
            }
        }
        return result;
    }

    public static class UnsupportedSheetTypeException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public UnsupportedSheetTypeException(String message) {
            super(message);
        }
    }
}
