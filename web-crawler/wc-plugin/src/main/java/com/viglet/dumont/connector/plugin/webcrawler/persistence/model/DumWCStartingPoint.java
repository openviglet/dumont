package com.viglet.dumont.connector.plugin.webcrawler.persistence.model;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@JsonIgnoreProperties({ "dumWCSource" })
public class DumWCStartingPoint extends DumWCUrl {

    @Serial
    private static final long serialVersionUID = 1L;

    // bi-directional many-to-one association to DumWCSource
    @ManyToOne
    @JoinColumn(name = "wc_source_id", nullable = false)
    private DumWCSource dumWCSource;

    public DumWCStartingPoint(String url, DumWCSource dumWCSource) {
        this.url = url;
        this.dumWCSource = dumWCSource;
    }
}
