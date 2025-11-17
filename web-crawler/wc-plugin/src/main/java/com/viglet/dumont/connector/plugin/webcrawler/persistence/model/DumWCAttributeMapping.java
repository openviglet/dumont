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
@Table(name = "wc_attribute_mapping")
@JsonIgnoreProperties({ "dumWCSource" })
public class DumWCAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;
    private String name;
    private String className;
    private String text;

    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private DumWCSource dumWCSource;

    public DumWCAttributeMapping(String name, Class<?> className, DumWCSource dumWCSource) {
        this.name = name;
        this.className = className.getName();
        this.text = null;
        this.dumWCSource = dumWCSource;
    }

    public DumWCAttributeMapping(String name, String text, DumWCSource dumWCSource) {
        this.name = name;
        this.className = null;
        this.text = text;
        this.dumWCSource = dumWCSource;
    }
}
