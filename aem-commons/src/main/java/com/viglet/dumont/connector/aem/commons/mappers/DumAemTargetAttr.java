package com.viglet.dumont.connector.aem.commons.mappers;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DumAemTargetAttr extends DumAemTargetAttrDefinition {
    private String textValue;
    protected List<DumAemSourceAttr> sourceAttrs;

    @Override
    public String toString() {
        return "{" +
                "name='" + getName() + '\'' +
                ", type='" + getType() + '\'' +
                ", mandatory=" + isMandatory() +
                ", multiValued=" + isMultiValued() +
                ", description='" + getDescription() + '\'' +
                ", facet=" + isFacet() +
                ", facetName='" + getFacetName() + '\'' +
                ", className='" + getClassName() + '\'' +
                ", textValue='" + getTextValue() + '\'' +
                ", sourceAttrs='" + getSourceAttrs() + '\'' +
                '}';
    }
}
