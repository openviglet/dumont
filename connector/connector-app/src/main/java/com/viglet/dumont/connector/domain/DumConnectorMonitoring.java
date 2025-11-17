package com.viglet.dumont.connector.domain;

import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import lombok.*;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DumConnectorMonitoring {
    private List<String> sources;
    private List<DumConnectorIndexingModel> indexing;
}
