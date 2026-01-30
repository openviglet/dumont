package com.viglet.dumont.connector.plugin.aem.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;

public interface DumAemAttributeSpecificationRepository extends JpaRepository<DumAemAttributeSpecification, String> {
    Optional<List<DumAemAttributeSpecification>> findByDumAemSource(DumAemSource dumAemSource);

    Optional<List<DumAemAttributeSpecification>> findByDumAemSourceAndName(DumAemSource dumAemSource, String name);
}
