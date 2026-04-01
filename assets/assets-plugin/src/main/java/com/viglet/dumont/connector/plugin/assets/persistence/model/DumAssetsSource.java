package com.viglet.dumont.connector.plugin.assets.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

import com.viglet.dumont.spring.jpa.DumUuid;
import jakarta.persistence.*;
import lombok.*;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "assets_source")
public class DumAssetsSource implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id @DumUuid @Column(name = "id", nullable = false)
    private String id;
    @Column private String name;
    @Column private String description;
    @Column private String sourceDir;
    @Column private String prefixFromReplace;
    @Column private String prefixToReplace;
    @Column private String site;
    @Column private String locale;
    @Column private String contentType;
    @Column private int chunk;
    @Column private boolean typeInId;
    @Column private String fileSizeField;
    @Column private String fileExtensionField;
    @Column private String encoding;
    @Column private boolean showOutput;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "assets_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();
}
