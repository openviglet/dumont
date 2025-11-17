package com.viglet.dumont.connector.commons.domain;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Locale;

@Builder
@Getter
@Setter
public class DumConnectorIndexing {

    private int id;
    private String objectId;
    private String source;
    private String environment;
    private String transactionId;
    private String checksum;
    private Locale locale;
    private Date created;
    private Date modificationDate;
    private DumIndexingStatus status;
    private List<String> sites;
}
