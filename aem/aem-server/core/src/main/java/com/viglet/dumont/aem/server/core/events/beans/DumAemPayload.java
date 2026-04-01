package com.viglet.dumont.aem.server.core.events.beans;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
public class DumAemPayload {
    List<String> paths;
    DumAemEvent event;
}
