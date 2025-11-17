package com.viglet.dumont.connector.plugin.webcrawler.persistence.model;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.dumont.spring.jpa.DumUuid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wc_file_extension")
@JsonIgnoreProperties({ "dumWCSource" })
public class DumWCFileExtension implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String extension;

    // bi-directional many-to-one association to DumWCSource
    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private DumWCSource dumWCSource;

    public DumWCFileExtension(String extension, DumWCSource dumWCSource) {
        this.extension = extension;
        this.dumWCSource = dumWCSource;
    }
}
