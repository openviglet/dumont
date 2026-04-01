package com.viglet.dumont.connector.plugin.assets.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serial;
import java.io.Serializable;

@Setter @Getter @Entity @Table(name = "assets_config")
public class DumAssetsConfigVar implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @Id @Column(unique = true, nullable = false, length = 250) private String id;
    @Column private String path;
    @Lob private String value;
    public DumAssetsConfigVar() { super(); }
    public DumAssetsConfigVar(String id, String path, String value) { this.id = id; this.path = path; this.value = value; }
}
