package com.viglet.dumont.connector.aem.commons.mappers;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class DumAemTargetAttr extends DumAemTargetAttrDefinition {
    private String textValue;
    protected List<DumAemSourceAttr> sourceAttrs;
}
