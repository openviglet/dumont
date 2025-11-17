package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DumWCAllowUrlRepository extends JpaRepository<DumWCAllowUrl, String> {
    Optional<List<DumWCAllowUrl>> findByDumWCSource(DumWCSource dumWCSource);
}
