package com.viglet.dumont.connector.plugin.aem.persistence.repository;

import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemAttributeSpecification;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DumAemAttributeSpecificationRepository extends JpaRepository<DumAemAttributeSpecification, String> {
    Optional<List<DumAemAttributeSpecification>> findByDumAemSource(DumAemSource dumAemSource);
}
