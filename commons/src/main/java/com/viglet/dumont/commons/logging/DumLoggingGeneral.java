package com.viglet.dumont.commons.logging;

import java.io.Serializable;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.annotation.JsonSerialize;

@Slf4j
@Builder
@Getter
@Setter
public class DumLoggingGeneral implements Serializable {
    private String clusterNode;
    private String level;
    private String logger;
    private String message;
    private String stackTrace;
    @JsonSerialize(using = IsoDateSerializer.class)
    private Instant date;
}
