package com.viglet.dumont.connector.aem.commons.mappers;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class DumAemSourceAttr {
    private String name;
    private String className;
    private boolean uniqueValues;
    private boolean convertHtmlToText;

    @Tolerate
    public DumAemSourceAttr() {
        super();
    }

    @Override
    public String toString() {
        return "DumAemSourceAttr{" +
                "name='" + name + '\'' +
                ", className='" + className + '\'' +
                ", uniqueValues=" + uniqueValues +
                ", convertHtmlToText=" + convertHtmlToText +
                '}';
    }
}
