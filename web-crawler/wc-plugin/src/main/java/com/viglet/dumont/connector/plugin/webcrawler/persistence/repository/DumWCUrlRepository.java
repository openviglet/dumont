package com.viglet.dumont.connector.plugin.webcrawler.persistence.repository;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DumWCUrlRepository extends JpaRepository<DumWCUrl, String> {
}
