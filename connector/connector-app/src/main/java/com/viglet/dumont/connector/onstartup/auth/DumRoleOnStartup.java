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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumRole;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;

import jakarta.transaction.Transactional;

/**
 * Bootstraps default roles (ROLE_ADMIN, ROLE_USER) and their privileges.
 * Invoked indirectly by {@link DumAuthBootstrapRunner} before groups/users.
 *
 * @since 2026.2.14
 */
@Component
public class DumRoleOnStartup {
    private static final String CATEGORY_CONNECTOR = "CONNECTOR";
    private static final String CATEGORY_INDEXING = "INDEXING";
    private static final String CATEGORY_AEM = "AEM";
    private static final String CATEGORY_DB = "DB";
    private static final String CATEGORY_WC = "WC";
    private static final String CATEGORY_ASSETS = "ASSETS";
    private static final String CATEGORY_SYSTEM = "SYSTEM";

    private final DumPrivilegeRepository dumPrivilegeRepository;
    private final DumRoleRepository dumRoleRepository;

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

    public DumRoleOnStartup(DumPrivilegeRepository dumPrivilegeRepository, DumRoleRepository dumRoleRepository) {
        this.dumPrivilegeRepository = dumPrivilegeRepository;
        this.dumRoleRepository = dumRoleRepository;
    }

    @Transactional
    public void createDefaultRows() {
        List<DumPrivilege> allPrivileges = new ArrayList<>();
        for (String[] def : PRIVILEGE_DEFINITIONS) {
            allPrivileges.add(createPrivilegeIfNotFound(def[0], def[1], def[2]));
        }
        createRoleIfNotFound("ROLE_ADMIN", allPrivileges);
        createRoleIfNotFound("ROLE_USER", Collections.singletonList(
                dumPrivilegeRepository.findByName("READ_PRIVILEGE")));
    }

    @Transactional
    public DumPrivilege createPrivilegeIfNotFound(String name, String description, String category) {
        DumPrivilege privilege = dumPrivilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new DumPrivilege(name);
            privilege.setDescription(description);
            privilege.setCategory(category);
            dumPrivilegeRepository.save(privilege);
        } else if (privilege.getDescription() == null || privilege.getCategory() == null) {
            privilege.setDescription(description);
            privilege.setCategory(category);
            dumPrivilegeRepository.save(privilege);
        }
        return privilege;
    }

    @Transactional
    public void createRoleIfNotFound(String name, Collection<DumPrivilege> privileges) {
        DumRole role = dumRoleRepository.findByName(name);
        if (role == null) {
            role = new DumRole(name);
            role.setDumPrivileges(privileges);
            dumRoleRepository.save(role);
        }
    }
}
