package com.viglet.dumont.connector.plugin.assets.export.bean;

import lombok.*;
import java.util.Collection;

@Builder(toBuilder = true) @AllArgsConstructor @NoArgsConstructor @Getter @Setter
public class DumAssetsExchange {
    private Collection<DumAssetsSourceExchange> sources;
}
