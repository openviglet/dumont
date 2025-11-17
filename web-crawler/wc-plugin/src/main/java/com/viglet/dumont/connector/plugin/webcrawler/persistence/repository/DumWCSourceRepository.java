package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DumWCSourceRepository extends JpaRepository<DumWCSource, String> {
}
