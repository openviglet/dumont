package com.viglet.dumont.commons.logging;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Slf4j
@Builder
@Getter
@Setter
public class DumLoggingIndexing implements Serializable {
    private DumIndexingStatus status;
    private String source;
    private String contentId;
    private String url;
    private List<String> sites;
    private String environment;
    private Locale locale;
    private String transactionId;
    private String checksum;
    private DumLoggingStatus resultStatus;
    private String details;
    @JsonSerialize(using = IsoDateSerializer.class)
    private Date date;

}
