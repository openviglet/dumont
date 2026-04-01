package com.viglet.dumont.connector.plugin.assets.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;

public interface DumAssetsSourceRepository extends JpaRepository<DumAssetsSource, String> {
    Optional<DumAssetsSource> findByName(String name);
}
