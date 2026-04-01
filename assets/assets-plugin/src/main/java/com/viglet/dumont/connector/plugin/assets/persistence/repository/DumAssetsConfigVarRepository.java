package com.viglet.dumont.connector.plugin.assets.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsConfigVar;

public interface DumAssetsConfigVarRepository extends JpaRepository<DumAssetsConfigVar, String> {
}
