package com.viglet.dumont.connector.plugin.webcrawler.export.bean;

import lombok.*;

import java.util.Collection;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumWCExchange {
    private Collection<DumWCSourceExchange> sources;
}
