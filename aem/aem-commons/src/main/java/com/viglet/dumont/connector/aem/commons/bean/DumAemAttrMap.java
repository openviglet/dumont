package com.viglet.dumont.connector.aem.commons.bean;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

/**
 * Core data structure for collecting extracted AEM attributes.
 * <p>
 * Use the fluent API ({@link #set}, {@link #append}, {@link #setIfAbsent}) for concise,
 * chainable attribute mapping. Null values are silently ignored in all methods.
 *
 * <pre>{@code
 * attrValues
 *     .set("title", model.getTitle())
 *     .set("date", model.getDate())
 *     .setIfAbsent("abstract", description)
 *     .append("text", teacher.getBio())
 *     .setAll("tags", List.of("news", "tech"));
 * }</pre>
 */
public class DumAemAttrMap extends HashMap<String, TurMultiValue> {

    // ── Fluent API ──────────────────────────────────────────────────────────

    /**
     * Sets a value, replacing any existing value for this attribute.
     * Accepts: String, Date, Boolean, Integer, Long, Double, Float, TurMultiValue.
     */
    public DumAemAttrMap set(String name, Object value) {
        addValue(name, value, true);
        return this;
    }

    /**
     * Appends a value, merging with any existing value for this attribute.
     */
    public DumAemAttrMap append(String name, Object value) {
        addValue(name, value, false);
        return this;
    }

    /**
     * Sets a value only if the attribute does not already exist.
     */
    public DumAemAttrMap setIfAbsent(String name, Object value) {
        if (value != null && !this.containsKey(name)) {
            addValue(name, value, true);
        }
        return this;
    }

    /** Sets a string collection, replacing any existing value. */
    public DumAemAttrMap setAll(String name, List<String> values) {
        if (values != null) {
            addOrMerge(name, new TurMultiValue(values, true), true);
        }
        return this;
    }

    /** Appends a string collection, merging with any existing value. */
    public DumAemAttrMap appendAll(String name, List<String> values) {
        if (values != null) {
            addOrMerge(name, new TurMultiValue(values, false), false);
        }
        return this;
    }

    /** Sets a date collection, replacing any existing value. */
    public DumAemAttrMap setAllDates(String name, List<Date> values) {
        if (values != null) {
            addOrMerge(name, TurMultiValue.fromDateCollection(values, true), true);
        }
        return this;
    }

    /** Appends a date collection, merging with any existing value. */
    public DumAemAttrMap appendAllDates(String name, List<Date> values) {
        if (values != null) {
            addOrMerge(name, TurMultiValue.fromDateCollection(values, false), false);
        }
        return this;
    }

    // ── Core dispatch ───────────────────────────────────────────────────────

    /**
     * Adds a value of any supported type, dispatching to the appropriate
     * TurMultiValue factory. Used internally by the fluent API and by
     * {@link com.viglet.dumont.connector.aem.commons.ext.DumAemComponentMapper}.
     */
    public void addWithValue(String name, Object value, boolean override) {
        addValue(name, value, override);
    }

    private void addValue(String name, Object value, boolean override) {
        if (value == null) {
            return;
        }
        TurMultiValue mv = switch (value) {
            case TurMultiValue existing -> existing;
            case String s -> TurMultiValue.singleItem(s, override);
            case Date d -> TurMultiValue.singleItem(d, override);
            case Boolean b -> TurMultiValue.singleItem(b, override);
            case Integer i -> TurMultiValue.singleItem(i, override);
            case Long l -> TurMultiValue.singleItem(l, override);
            case Double d -> TurMultiValue.singleItem(d, override);
            case Float f -> TurMultiValue.singleItem(f, override);
            default -> TurMultiValue.singleItem(value.toString(), override);
        };
        addOrMerge(name, mv, override);
    }

    private void addOrMerge(String name, TurMultiValue value, boolean override) {
        if (override || !this.containsKey(name)) {
            this.put(name, value);
        } else {
            this.get(name).addAll(value);
        }
    }

    // ── Merge ───────────────────────────────────────────────────────────────

    public void merge(DumAemAttrMap fromMap) {
        fromMap.forEach((key, value) -> {
            if (this.containsKey(key) && !value.isOverride()) {
                this.get(key).addAll(value);
            } else {
                this.put(key, value);
            }
        });
    }

    // ── Static factories ────────────────────────────────────────────────────

    /** Creates a new map with a single attribute set (override = true). */
    public static DumAemAttrMap of(String name, Object value) {
        return new DumAemAttrMap().set(name, value);
    }

    /** Creates a new map with a single attribute appended (override = false). */
    public static DumAemAttrMap ofAppend(String name, Object value) {
        return new DumAemAttrMap().append(name, value);
    }

    /** Creates a new map with a string collection appended. */
    public static DumAemAttrMap ofAppendAll(String name, List<String> values) {
        return new DumAemAttrMap().appendAll(name, values);
    }

    /** Creates a new map from a {@link DumAemTargetAttr} text value (append mode). */
    public static DumAemAttrMap ofAppend(DumAemTargetAttr attr) {
        return new DumAemAttrMap().append(attr.getName(), attr.getTextValue());
    }
}
