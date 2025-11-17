package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCNotAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DumWCNotAllowUrlRepository extends JpaRepository<DumWCNotAllowUrl, String> {
    Optional<List<DumWCNotAllowUrl>> findByDumWCSource(DumWCSource dumWCSource);
}
