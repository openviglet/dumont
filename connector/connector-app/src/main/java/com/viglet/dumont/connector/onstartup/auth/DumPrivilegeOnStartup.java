/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.onstartup.auth;

import java.util.ArrayList;
import java.util.List;

import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures all privileges exist on every startup (not just first-time) and
 * that ROLE_ADMIN has every privilege assigned.
 *
 * @since 2026.2.14
 */
@Slf4j
@Component
@Transactional
@Order(10)
public class DumPrivilegeOnStartup implements ApplicationRunner {

    private static final String CATEGORY_CONNECTOR = "CONNECTOR";
    private static final String CATEGORY_INDEXING = "INDEXING";
    private static final String CATEGORY_AEM = "AEM";
    private static final String CATEGORY_DB = "DB";
    private static final String CATEGORY_WC = "WC";
    private static final String CATEGORY_ASSETS = "ASSETS";
    private static final String CATEGORY_SYSTEM = "SYSTEM";

    private static final String[][] PRIVILEGE_DEFINITIONS = {
        {"READ_PRIVILEGE", "Legacy read privilege", CATEGORY_SYSTEM},
        {"WRITE_PRIVILEGE", "Legacy write privilege", CATEGORY_SYSTEM},

        {"CONNECTOR_VIEW", "View connector status", CATEGORY_CONNECTOR},
        {"CONNECTOR_EDIT", "Edit connector configuration", CATEGORY_CONNECTOR},

        {"INDEXING_RULE_VIEW", "View indexing rules", CATEGORY_INDEXING},
        {"INDEXING_RULE_CREATE", "Create indexing rules", CATEGORY_INDEXING},
        {"INDEXING_RULE_EDIT", "Edit indexing rules", CATEGORY_INDEXING},
        {"INDEXING_RULE_DELETE", "Delete indexing rules", CATEGORY_INDEXING},
        {"INDEXING_RUN", "Run indexing jobs", CATEGORY_INDEXING},

        {"AEM_VIEW", "View AEM sources", CATEGORY_AEM},
        {"AEM_CREATE", "Create AEM sources", CATEGORY_AEM},
        {"AEM_EDIT", "Edit AEM sources", CATEGORY_AEM},
        {"AEM_DELETE", "Delete AEM sources", CATEGORY_AEM},

        {"DB_VIEW", "View DB sources", CATEGORY_DB},
        {"DB_CREATE", "Create DB sources", CATEGORY_DB},
        {"DB_EDIT", "Edit DB sources", CATEGORY_DB},
        {"DB_DELETE", "Delete DB sources", CATEGORY_DB},

        {"WC_VIEW", "View Web Crawler sources", CATEGORY_WC},
        {"WC_CREATE", "Create Web Crawler sources", CATEGORY_WC},
        {"WC_EDIT", "Edit Web Crawler sources", CATEGORY_WC},
        {"WC_DELETE", "Delete Web Crawler sources", CATEGORY_WC},

        {"ASSETS_VIEW", "View Assets sources", CATEGORY_ASSETS},
        {"ASSETS_CREATE", "Create Assets sources", CATEGORY_ASSETS},
        {"ASSETS_EDIT", "Edit Assets sources", CATEGORY_ASSETS},
        {"ASSETS_DELETE", "Delete Assets sources", CATEGORY_ASSETS},

        {"SYSTEM_INFO_VIEW", "View system info", CATEGORY_SYSTEM},
    };

    private final DumPrivilegeRepository dumPrivilegeRepository;
    private final DumRoleRepository dumRoleRepository;

    public DumPrivilegeOnStartup(DumPrivilegeRepository dumPrivilegeRepository,
                                 DumRoleRepository dumRoleRepository) {
        this.dumPrivilegeRepository = dumPrivilegeRepository;
        this.dumRoleRepository = dumRoleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DumPrivilege> allPrivileges = new ArrayList<>();
        int created = 0;
        for (String[] def : PRIVILEGE_DEFINITIONS) {
            DumPrivilege privilege = dumPrivilegeRepository.findByName(def[0]);
            if (privilege == null) {
                privilege = new DumPrivilege(def[0]);
                privilege.setDescription(def[1]);
                privilege.setCategory(def[2]);
                dumPrivilegeRepository.save(privilege);
                created++;
            } else if (privilege.getDescription() == null || privilege.getCategory() == null) {
                privilege.setDescription(def[1]);
                privilege.setCategory(def[2]);
                dumPrivilegeRepository.save(privilege);
            }
            allPrivileges.add(privilege);
        }
        if (created > 0) {
            log.info("Created {} new privileges.", created);
        }

        DumRole adminRole = dumRoleRepository.findByName("ROLE_ADMIN");
        if (adminRole != null) {
            var existing = adminRole.getDumPrivileges();
            var existingNames = new java.util.HashSet<String>();
            for (var p : existing) existingNames.add(p.getName());
            var toAdd = allPrivileges.stream().filter(p -> !existingNames.contains(p.getName())).toList();
            if (!toAdd.isEmpty()) {
                var merged = new ArrayList<>(existing);
                merged.addAll(toAdd);
                adminRole.setDumPrivileges(merged);
                dumRoleRepository.save(adminRole);
                log.info("Added {} privileges to ROLE_ADMIN.", toAdd.size());
            }
        }
    }
}
