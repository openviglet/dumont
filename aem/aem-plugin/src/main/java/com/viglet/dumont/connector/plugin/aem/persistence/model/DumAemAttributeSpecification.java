package com.viglet.dumont.connector.plugin.aem.persistence.model;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.dumont.spring.jpa.DumUuid;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuppressWarnings("java:S2387")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "aem_attribute_specification")
@JsonIgnoreProperties({ "dumAemSource" })
public class DumAemAttributeSpecification extends TurSNAttributeSpec {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "class_name")
    private String className;

    private String text;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TurSEFieldType type;

    @Column(name = "mandatory")
    private boolean mandatory;

    @Column(name = "multi_valued")
    private boolean multiValued;

    @Column(name = "description")
    private String description;

    @Column(name = "facet")
    private boolean facet;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "language")
    @Column(name = "facet_name")
    @CollectionTable(name = "aem_attritbute_facet", joinColumns = @JoinColumn(name = "spec_id"))
    private Map<String, String> facetNames = new HashMap<>();

    @ManyToOne
    @JoinColumn(name = "aem_source_id", nullable = false)
    private DumAemSource dumAemSource;

}
