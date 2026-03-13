package com.viglet.dumont.connector.domain;

import java.util.Date;
import java.util.List;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DumConnectorMonitoringRequest {
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 50;
    @Builder.Default
    private String sortBy = "modificationDate";
    @Builder.Default
    private String sortDirection = "desc";

    private String source;
    private String objectId;
    private List<DumIndexingStatus> statuses;
    private String environment;
    private String locale;
    private String site;
    private Date dateFrom;
    private Date dateTo;
}
