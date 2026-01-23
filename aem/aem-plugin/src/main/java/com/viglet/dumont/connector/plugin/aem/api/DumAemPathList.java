package com.viglet.dumont.connector.plugin.aem.api;

import java.util.List;

import com.viglet.dumont.connector.aem.commons.bean.DumAemEvent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DumAemPathList {
    private List<String> paths;
    private DumAemEvent event;
    private Boolean recursive;
}
