package com.viglet.dumont.connector.domain;

import java.util.List;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DumConnectorMonitoringPage {
    private List<String> sources;
    private List<String> environments;
    private List<String> locales;
    private List<String> sites;
    private List<DumConnectorIndexingModel> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
