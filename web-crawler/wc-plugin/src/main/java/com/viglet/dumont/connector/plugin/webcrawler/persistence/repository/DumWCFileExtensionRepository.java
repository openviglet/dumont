package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCFileExtension;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DumWCFileExtensionRepository extends JpaRepository<DumWCFileExtension, String> {
    Optional<List<DumWCFileExtension>> findByDumWCSource(DumWCSource dumWCSource);
}
