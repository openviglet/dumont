package com.viglet.dumont.connector.plugin.aem.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DumAemBrowseRequest {
    private String endpoint;
    private String username;
    private String password;
    private String path;
}
