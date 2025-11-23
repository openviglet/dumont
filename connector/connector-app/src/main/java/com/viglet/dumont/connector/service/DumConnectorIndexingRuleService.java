package com.viglet.dumont.connector.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.commons.DumConnectorIndexingRuleType;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingRuleModel;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRuleRepository;
import com.viglet.dumont.spring.utils.DumPersistenceUtils;

@Service
public class DumConnectorIndexingRuleService {
    private final DumConnectorIndexingRuleRepository dumConnectorIndexingRuleRepository;

    public DumConnectorIndexingRuleService(DumConnectorIndexingRuleRepository dumConnectorIndexingRuleRepository) {
        this.dumConnectorIndexingRuleRepository = dumConnectorIndexingRuleRepository;
    }

    public Set<DumConnectorIndexingRuleModel> getIndexingRules(DumConnectorSession dumConnectorSession) {
        return dumConnectorIndexingRuleRepository
                .findBySourceAndRuleType(dumConnectorSession.getSource(), DumConnectorIndexingRuleType.IGNORE);
    }

    public Set<DumConnectorIndexingRuleModel> getBySource(String source) {
        return dumConnectorIndexingRuleRepository
                .findBySource(DumPersistenceUtils.orderByNameIgnoreCase(), source);
    }

    public List<DumConnectorIndexingRuleModel> getAll() {
        return dumConnectorIndexingRuleRepository.findAll();
    }

    public Optional<DumConnectorIndexingRuleModel> getById(String id) {
        return dumConnectorIndexingRuleRepository.findById(id);
    }

    public DumConnectorIndexingRuleModel update(String id, DumConnectorIndexingRuleModel dumConnectorIndexingRule) {
        return dumConnectorIndexingRuleRepository.findById(id).map(edit -> {
            edit.setName(dumConnectorIndexingRule.getName());
            edit.setDescription(dumConnectorIndexingRule.getDescription());
            edit.setAttribute(dumConnectorIndexingRule.getAttribute());
            edit.setRuleType(dumConnectorIndexingRule.getRuleType());
            edit.setSource(dumConnectorIndexingRule.getSource());
            edit.setValues(dumConnectorIndexingRule.getValues());
            edit.setLastModifiedDate(new Date());
            return dumConnectorIndexingRuleRepository.save(edit);
        }).orElse(new DumConnectorIndexingRuleModel());
    }

    public void deleteById(String id) {
        dumConnectorIndexingRuleRepository.deleteById(id);
    }

    public DumConnectorIndexingRuleModel save(DumConnectorIndexingRuleModel dumConnectorIndexingRule) {
        if (dumConnectorIndexingRule == null) {
            throw new IllegalArgumentException("DumConnectorIndexingRuleModel cannot be null");
        }
        return dumConnectorIndexingRuleRepository.save(dumConnectorIndexingRule);
    }

}
