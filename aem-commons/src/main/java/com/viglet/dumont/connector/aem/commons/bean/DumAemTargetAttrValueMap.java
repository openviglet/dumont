package com.viglet.dumont.connector.aem.commons.bean;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.turing.client.sn.TurMultiValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemTargetAttrValueMap extends HashMap<String, TurMultiValue> {

    public void addWithSingleValue(String attributeName, String value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Date value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Boolean value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Long value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Double value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Float value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.singleItem(value, override), override);
    }

    public void addWithStringCollectionValue(String attributeName, List<String> value,
            boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, new TurMultiValue(value, override), override);
    }

    public void addWithDateCollectionValue(String attributeName, List<Date> value,
            boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, TurMultiValue.fromDateCollection(value, override), override);
    }

    public void addWithSingleValue(String attributeName, Integer value, boolean override) {
        if (value == null) {
            return;
        }
        TurMultiValue newValue = TurMultiValue.singleItem(value, override);
        addOrMerge(attributeName, newValue, override);
    }

    public void addWithSingleValue(String attributeName, TurMultiValue value, boolean override) {
        if (value == null) {
            return;
        }
        addOrMerge(attributeName, value, override);
    }

    /**
     * Internal helper to add or merge TurMultiValue based on override flag.
     */
    private void addOrMerge(String attributeName, TurMultiValue value, boolean override) {
        if (override || !this.containsKey(attributeName)) {
            this.put(attributeName, value);
        } else {
            this.get(attributeName).addAll(value);
        }
    }

    public void merge(DumAemTargetAttrValueMap fromMap) {
        fromMap.keySet().forEach(fromKey -> {
            if (this.containsKey(fromKey)) {
                if (fromMap.get(fromKey).isOverride()) {
                    this.put(fromKey, fromMap.get(fromKey));
                } else {
                    this.get(fromKey).addAll(fromMap.get(fromKey));
                }
            } else {
                this.put(fromKey, fromMap.get(fromKey));
            }
        });
    }

    public static DumAemTargetAttrValueMap singleItem(DumAemTargetAttrValue dumCmsTargetAttrValue) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.put(dumCmsTargetAttrValue.getTargetAttrName(),
                dumCmsTargetAttrValue.getMultiValue());
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, List<String> value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithStringCollectionValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, TurMultiValue value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, String value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Date value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Boolean value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Integer value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Double value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Float value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName, Long value,
            boolean override) {
        DumAemTargetAttrValueMap dumCmsTargetAttrValueMap = new DumAemTargetAttrValueMap();
        dumCmsTargetAttrValueMap.addWithSingleValue(attributeName, value, override);
        return dumCmsTargetAttrValueMap;
    }

    public static DumAemTargetAttrValueMap singleItem(String attributeName,
            TurMultiValue turMultiValue) {
        return DumAemTargetAttrValueMap
                .singleItem(new DumAemTargetAttrValue(attributeName, turMultiValue));
    }

    public static DumAemTargetAttrValueMap singleItem(DumAemTargetAttr dumAemTargetAttr,
            boolean override) {
        return DumAemTargetAttrValueMap.singleItem(dumAemTargetAttr.getName(),
                dumAemTargetAttr.getTextValue(), override);
    }
}
