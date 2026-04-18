/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package com.viglet.dumont.connector.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.viglet.dumont.connector.config.security.DumAuthenticationEntryPoint;
import com.viglet.dumont.connector.config.security.DumCsrfCookieFilter;
import com.viglet.dumont.connector.config.security.DumCustomUserDetailsService;
import com.viglet.dumont.connector.config.security.DumLogoutHandler;
import com.viglet.dumont.connector.config.security.DumOAuth2UserService;
import com.viglet.dumont.connector.config.security.DumOidcUserService;
import com.viglet.dumont.connector.properties.DumAuthConfigProperties;

/**
 * Main Spring Security configuration for Dumont, mirroring the Turing
 * security layout (session auth, OAuth2 / Keycloak, CSRF, logout) while
 * keeping {@link DumApiKeyFilter} in front so the legacy {@code Key}
 * header contract used by Turing → Dumont calls still works.
 */
@Configuration
@EnableWebSecurity
@Profile("production")
@EnableMethodSecurity(securedEnabled = true)
@ComponentScan(basePackageClasses = DumCustomUserDetailsService.class)
public class DumWebConfiguration {

    private static final String LOGIN_PATH = "/api/login";
    private static final String LOGOUT_PATH = "/logout";
    private static final String SETUP_PATH = "/api/setup";
    public static final String ERROR_PATH = "/error/**";

    private final DumApiKeyFilter apiKeyFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:''}")
    private String issuerUri;
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:''}")
    private String clientId;
    @Value("${dumont.url:http://localhost:30130}")
    private String dumontUrl;
    @Value("${server.servlet.session.cookie.name:JSESSIONID}")
    private String sessionCookieName;

    private final PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();

    public DumWebConfiguration(DumApiKeyFilter apiKeyFilter,
                               UserDetailsService userDetailsService,
                               PasswordEncoder passwordEncoder) {
        this.apiKeyFilter = apiKeyFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    @SuppressWarnings("java:S4502")
    SecurityFilterChain filterChain(HttpSecurity http,
                                    DumLogoutHandler logoutHandler,
                                    DumAuthConfigProperties configProperties,
                                    DumAuthenticationEntryPoint authenticationEntryPoint,
                                    DumOAuth2UserService oAuth2UserService,
                                    DumOidcUserService oidcUserService,
                                    ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) throws Exception {

        http.headers(header -> header.frameOptions(
                frameOptions -> frameOptions.disable()
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)));
        http.cors(Customizer.withDefaults());
        // API Key filter first — bypasses session auth for Turing → Dumont calls.
        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        http.userDetailsService(userDetailsService);
        http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        http.securityContext(securityContext -> securityContext
                .securityContextRepository(new HttpSessionSecurityContextRepository()));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();

        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        mvc.matcher("/api/v2/integration/**"),
                        mvc.matcher("/api/v2/connector/**"),
                        mvc.matcher("/api/v2/aem/**"),
                        mvc.matcher("/api/v2/db/**"),
                        mvc.matcher("/api/v2/wc/**"),
                        mvc.matcher("/api/v2/assets/**"),
                        mvc.matcher(ERROR_PATH),
                        mvc.matcher(LOGOUT_PATH),
                        mvc.matcher(LOGIN_PATH),
                        mvc.matcher(SETUP_PATH),
                        mvc.matcher("/login/oauth2/**"),
                        mvc.matcher("/oauth2/**")))
                .addFilterAfter(new DumCsrfCookieFilter(), BasicAuthenticationFilter.class);

        boolean oauth2Available = clientRegistrationRepository.getIfAvailable() != null;
        if (configProperties.isKeycloak()) {
            String keycloakLogoutUrl = String.format(
                    "%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                    issuerUri, clientId, dumontUrl);
            if (oauth2Available) {
                http.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo
                        .userService(oAuth2UserService)
                        .oidcUserService(oidcUserService)));
            }
            http.authorizeHttpRequests(authorize -> authorize.requestMatchers(
                            mvc.matcher(ERROR_PATH),
                            mvc.matcher("/api/v2/ping"),
                            mvc.matcher("/api/v2/discovery"),
                            mvc.matcher("/api/v2/connector/status"),
                            mvc.matcher("/api/v2/user/current"),
                            mvc.matcher("/api/csrf"),
                            mvc.matcher("/assets/**"),
                            mvc.matcher("/favicon.ico"),
                            mvc.matcher("/*.png"),
                            mvc.matcher("/manifest.json"),
                            mvc.matcher(LOGIN_PATH),
                            mvc.matcher(SETUP_PATH))
                    .permitAll()
                    .anyRequest().authenticated());
            http.logout(logout -> logout.addLogoutHandler(logoutHandler)
                    .logoutSuccessUrl(keycloakLogoutUrl));
        } else {
            if (oauth2Available
                    && (configProperties.getAuthentication() == null
                        || configProperties.getAuthentication().isThirdparty())) {
                http.oauth2Login(oauth2 -> oauth2
                        .loginPage("/dumont/login")
                        .defaultSuccessUrl("/dumont/admin", true)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                                .oidcUserService(oidcUserService)));
            }
            http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(authenticationEntryPoint))
                    .authorizeHttpRequests(authorize -> authorize.requestMatchers(
                                    mvc.matcher(ERROR_PATH),
                                    mvc.matcher("/api/v2/ping"),
                                    mvc.matcher("/api/v2/discovery"),
                                    mvc.matcher("/api/v2/connector/status"),
                                    mvc.matcher("/api/v2/user/current"),
                                    mvc.matcher("/api/csrf"),
                                    mvc.matcher(LOGOUT_PATH),
                                    mvc.matcher("/index.html"),
                                    mvc.matcher("/login/**"),
                                    mvc.matcher("/setup/**"),
                                    mvc.matcher("/admin/**"),
                                    mvc.matcher("/dumont/**"),
                                    mvc.matcher("/"),
                                    mvc.matcher("/assets/**"),
                                    mvc.matcher("/fonts/**"),
                                    mvc.matcher("/favicon.ico"),
                                    mvc.matcher("/*.png"),
                                    mvc.matcher("/manifest.json"),
                                    mvc.matcher("/browserconfig.xml"),
                                    mvc.matcher(LOGIN_PATH),
                                    mvc.matcher(SETUP_PATH))
                            .permitAll()
                            .anyRequest().authenticated());
            http.logout(logout -> logout
                    .logoutRequestMatcher(mvc.matcher(HttpMethod.GET, LOGOUT_PATH))
                    .addLogoutHandler(logoutHandler)
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies(sessionCookieName, "XSRF-TOKEN")
                    .logoutSuccessUrl("/dumont/login"));
        }
        return http.build();
    }

    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.httpFirewall(allowUrlEncodedSlashHttpFirewall()).ignoring()
                .requestMatchers(mvc.matcher("/h2/**"));
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
        try {
            return authenticationConfiguration.getAuthenticationManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get AuthenticationManager", e);
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    }

    @Bean
    HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
}
