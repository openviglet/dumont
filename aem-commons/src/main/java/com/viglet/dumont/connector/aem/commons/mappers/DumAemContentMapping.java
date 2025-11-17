package com.viglet.dumont.connector.aem.commons.mappers;

import java.util.List;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DumAemContentMapping {
    private List<TurSNAttributeSpec> targetAttrDefinitions;
    private List<DumAemModel> models;
    private String deltaClassName;

    @Override
    public String toString() {
        return "DumAemContentMapping{" +
                "targetAttrDefinitions=" + targetAttrDefinitions +
                ", models=" + models +
                ", deltaClassName='" + deltaClassName +
                '}';
    }
}
