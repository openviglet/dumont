package com.viglet.dumont.connector.aem.commons.mappers;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
@ToString
public class DumAemSourceAttr implements Serializable {
    private String name;
    private String className;
    private boolean uniqueValues;
    private boolean convertHtmlToText;

    @Tolerate
    public DumAemSourceAttr() {
        super();
    }
}
