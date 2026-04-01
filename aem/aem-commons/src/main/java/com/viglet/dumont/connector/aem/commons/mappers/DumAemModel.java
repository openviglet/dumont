package com.viglet.dumont.connector.aem.commons.mappers;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class DumAemModel {
    private String type;
    private String subType;
    private String className;
    private String validToIndex;
    @Builder.Default
    private List<DumAemTargetAttr> targetAttrs = new ArrayList<>();
}
