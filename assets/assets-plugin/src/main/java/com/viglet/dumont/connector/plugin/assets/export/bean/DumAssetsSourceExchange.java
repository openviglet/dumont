package com.viglet.dumont.connector.plugin.assets.export.bean;

import lombok.*;
import java.util.Collection;
import java.util.HashSet;

@Builder(toBuilder = true) @AllArgsConstructor @NoArgsConstructor @Getter @Setter
public class DumAssetsSourceExchange {
    private String id;
    private String name;
    private String description;
    private String sourceDir;
    private String prefixFromReplace;
    private String prefixToReplace;
    private String site;
    private String locale;
    private String contentType;
    private int chunk;
    private boolean typeInId;
    private String fileSizeField;
    private String fileExtensionField;
    private String encoding;
    private boolean showOutput;
    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();
}
