/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.dumont.connector.api.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.dumont.connector.bean.DumCurrentUser;
import com.viglet.dumont.connector.persistence.dto.auth.DumUserDto;
import com.viglet.dumont.connector.persistence.mapper.auth.DumUserMapper;
import com.viglet.dumont.connector.persistence.model.auth.DumGroup;
import com.viglet.dumont.connector.persistence.model.auth.DumPrivilege;
import com.viglet.dumont.connector.persistence.model.auth.DumUser;
import com.viglet.dumont.connector.persistence.repository.auth.DumGroupRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumPrivilegeRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumRoleRepository;
import com.viglet.dumont.connector.persistence.repository.auth.DumUserRepository;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Native (session-based) user management, mirroring {@code TurUserAPI}.
 *
 * @since 2026.2.14
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/user")
@Tag(name = "User", description = "User API")
public class DumUserAPI {

    private static final String ADMIN = "admin";
    private static final String ADMINISTRATOR = "Administrator";
    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String GIVEN_NAME = "given_name";
    private static final String FAMILY_NAME = "family_name";
    private static final String EMAIL = "email";

    private final PasswordEncoder passwordEncoder;
    private final DumUserRepository dumUserRepository;
    private final DumGroupRepository dumGroupRepository;
    private final DumRoleRepository dumRoleRepository;
    private final DumAuthConfigProperties configProperties;
    private final DumUserMapper dumUserMapper;
    private final DumPrivilegeRepository dumPrivilegeRepository;

    public DumUserAPI(PasswordEncoder passwordEncoder,
            DumUserRepository dumUserRepository,
            DumGroupRepository dumGroupRepository,
            DumRoleRepository dumRoleRepository,
            DumAuthConfigProperties configProperties,
            DumUserMapper dumUserMapper,
            DumPrivilegeRepository dumPrivilegeRepository) {
        this.passwordEncoder = passwordEncoder;
        this.dumUserRepository = dumUserRepository;
        this.dumGroupRepository = dumGroupRepository;
        this.dumRoleRepository = dumRoleRepository;
        this.configProperties = configProperties;
        this.dumUserMapper = dumUserMapper;
        this.dumPrivilegeRepository = dumPrivilegeRepository;
    }

    private String firstNonNull(OAuth2User user, String... keys) {
        for (String key : keys) {
            String value = user.getAttribute(key);
            if (value != null) return value;
        }
        return null;
    }

    private List<String> resolvePrivileges(DumUser dumUser) {
        if (!configProperties.isPermissions()) {
            return dumPrivilegeRepository.findAll().stream()
                    .map(DumPrivilege::getName)
                    .toList();
        }
        var groups = dumGroupRepository.findByDumUsersContaining(dumUser);
        var privileges = new LinkedHashSet<String>();
        for (var group : groups) {
            for (var role : dumRoleRepository.findByDumGroupsContaining(group)) {
                for (var privilege : role.getDumPrivileges()) {
                    privileges.add(privilege.getName());
                }
            }
        }
        return new ArrayList<>(privileges);
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAdminOrSelf(String username) {
        return isAdmin() || currentUsername().equals(username);
    }

    @Secured("ROLE_ADMIN")
    @GetMapping
    public List<DumUserDto> dumUserList() {
        return dumUserMapper.toDtoList(dumUserRepository.findAll());
    }

    @GetMapping("/current")
    public DumCurrentUser dumUserCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OAuth2User) {
            return oauth2User();
        }
        return regularUser(authentication.getName());
    }

    private DumCurrentUser regularUser(String currentUserName) {
        DumUser dumUser = dumUserRepository.findByUsername(currentUserName);
        if (dumUser == null) {
            return null;
        }
        dumUser.setPassword(null);

        boolean isAdmin = !configProperties.isPermissions();
        if (!isAdmin && dumUser.getDumGroups() != null) {
            for (DumGroup group : dumUser.getDumGroups()) {
                if (ADMINISTRATOR.equals(group.getName())) {
                    isAdmin = true;
                    break;
                }
            }
        }

        DumCurrentUser currentUser = new DumCurrentUser();
        currentUser.setUsername(dumUser.getUsername());
        currentUser.setFirstName(dumUser.getFirstName());
        currentUser.setLastName(dumUser.getLastName());
        currentUser.setAdmin(isAdmin);
        currentUser.setEmail(dumUser.getEmail());
        currentUser.setAvatarUrl(dumUser.getAvatarUrl());
        currentUser.setHasAvatar(dumUser.getAvatarUrl() != null);
        currentUser.setRealm(dumUser.getRealm());
        currentUser.setPrivileges(resolvePrivileges(dumUser));
        return currentUser;
    }

    private DumCurrentUser oauth2User() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2User user = ((OAuth2User) authentication.getPrincipal());
        String realm = (authentication instanceof OAuth2AuthenticationToken oauthToken)
                ? oauthToken.getAuthorizedClientRegistrationId()
                : "oauth2";
        String username = firstNonNull(user, PREFERRED_USERNAME, "login", EMAIL);
        String email = firstNonNull(user, EMAIL);
        String picture = firstNonNull(user, "picture", "avatar_url");

        String firstName = user.getAttribute(GIVEN_NAME);
        String lastName = user.getAttribute(FAMILY_NAME);
        if (firstName == null && lastName == null) {
            String fullName = firstNonNull(user, "name");
            if (fullName != null) {
                int space = fullName.indexOf(' ');
                firstName = space > 0 ? fullName.substring(0, space) : fullName;
                lastName = space > 0 ? fullName.substring(space + 1) : null;
            }
        }

        DumUser dumUser = dumUserRepository.findByUsername(username);
        if (dumUser == null) {
            dumUser = DumUser.builder()
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .avatarUrl(picture)
                    .realm(realm)
                    .enabled(1)
                    .build();
            dumUserRepository.save(dumUser);
        } else {
            dumUser.setFirstName(firstName);
            dumUser.setLastName(lastName);
            dumUser.setEmail(email);
            dumUser.setRealm(realm);
            if (picture != null && dumUser.getAvatarUrl() == null) {
                dumUser.setAvatarUrl(picture);
            }
            dumUserRepository.save(dumUser);
        }

        boolean isAdmin = !configProperties.isPermissions();
        if (!isAdmin) {
            var groups = dumGroupRepository.findByDumUsersContaining(dumUser);
            if (groups != null) {
                for (DumGroup group : groups) {
                    if (ADMINISTRATOR.equals(group.getName())) {
                        isAdmin = true;
                        break;
                    }
                }
            }
        }

        DumCurrentUser currentUser = new DumCurrentUser();
        currentUser.setUsername(username);
        currentUser.setFirstName(firstName);
        currentUser.setLastName(lastName);
        currentUser.setEmail(email);
        currentUser.setAdmin(isAdmin);
        currentUser.setAvatarUrl(dumUser.getAvatarUrl());
        currentUser.setHasAvatar(dumUser.getAvatarUrl() != null);
        currentUser.setRealm(dumUser.getRealm());
        currentUser.setPrivileges(resolvePrivileges(dumUser));
        return currentUser;
    }

    @GetMapping("/{username}")
    public ResponseEntity<DumUserDto> dumUserEdit(@PathVariable String username) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        DumUser dumUser = dumUserRepository.findByUsername(username);
        DumUser user = Optional.ofNullable(dumUser).map(currentUser -> {
            currentUser.setPassword(null);
            currentUser.setDumGroups(dumGroupRepository.findByDumUsersContaining(currentUser));
            return currentUser;
        }).orElseGet(DumUser::new);
        return ResponseEntity.ok(dumUserMapper.toDto(user));
    }

    @PutMapping("/{username}")
    public ResponseEntity<DumUserDto> dumUserUpdate(@PathVariable String username, @RequestBody DumUserDto dumUserDto) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        DumUser dumUser = dumUserMapper.toEntity(dumUserDto);
        DumUser user = Optional.ofNullable(dumUserRepository.findByUsername(username)).map(userEdit -> {
            userEdit.setFirstName(dumUser.getFirstName());
            userEdit.setLastName(dumUser.getLastName());
            userEdit.setEmail(dumUser.getEmail());
            userEdit.setAvatarUrl(dumUser.getAvatarUrl());
            if (StringUtils.hasText(dumUser.getPassword())) {
                userEdit.setPassword(passwordEncoder.encode(dumUser.getPassword()));
            }
            if (isAdmin()) {
                userEdit.setDumGroups(dumUser.getDumGroups());
            }
            dumUserRepository.save(userEdit);
            return userEdit;
        }).orElseGet(DumUser::new);
        return ResponseEntity.ok(dumUserMapper.toDto(user));
    }

    @Secured("ROLE_ADMIN")
    @Transactional
    @DeleteMapping("/{username}")
    public boolean dumUserDelete(@PathVariable String username) {
        if (!username.equalsIgnoreCase(ADMIN)) {
            dumUserRepository.deleteByUsername(username);
            return true;
        }
        return false;
    }

    @Secured("ROLE_ADMIN")
    @PostMapping
    public DumUserDto dumUserAdd(@RequestBody DumUserDto dumUserDto) {
        DumUser dumUser = dumUserMapper.toEntity(dumUserDto);
        if (StringUtils.hasText(dumUser.getPassword())) {
            dumUser.setPassword(passwordEncoder.encode(dumUser.getPassword()));
            dumUserRepository.save(dumUser);
        }
        return dumUserMapper.toDto(dumUser);
    }

    public record RegisterRequest(String username, String password, String firstName, String lastName, String email) {}

    @PostMapping("/register")
    public ResponseEntity<?> dumUserRegister(@RequestBody RegisterRequest request) {
        if (configProperties.getAuthentication() == null
                || !configProperties.getAuthentication().isNewUser()) {
            return ResponseEntity.status(403).body(Map.of("error", "Self-registration is disabled"));
        }
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }
        if (dumUserRepository.findByUsername(request.username()) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        DumUser dumUser = DumUser.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .enabled(1)
                .build();
        DumGroup userGroup = dumGroupRepository.findByName("User");
        if (userGroup != null) {
            dumUser.setDumGroups(Collections.singletonList(userGroup));
        }
        dumUserRepository.save(dumUser);
        return ResponseEntity.ok(dumUserMapper.toDto(dumUser));
    }

    @Secured("ROLE_ADMIN")
    @GetMapping("/model")
    public DumUserDto dumUserStructure() {
        return new DumUserDto();
    }

    @PutMapping("/{username}/avatar-url")
    public ResponseEntity<Void> updateAvatarUrl(@PathVariable String username,
            @RequestBody(required = false) Map<String, String> body) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        DumUser dumUser = dumUserRepository.findByUsername(username);
        if (dumUser == null) {
            return ResponseEntity.notFound().build();
        }
        String avatarUrl = (body != null) ? body.get("avatarUrl") : null;
        dumUser.setAvatarUrl(avatarUrl);
        dumUserRepository.save(dumUser);
        return ResponseEntity.ok().build();
    }
}
