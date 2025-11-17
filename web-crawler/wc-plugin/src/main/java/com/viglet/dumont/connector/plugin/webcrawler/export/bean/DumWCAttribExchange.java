package com.viglet.dumont.connector.plugin.webcrawler.export.bean;

import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumWCAttribExchange {
    private String name;
    private String className;
    private String text;
}
