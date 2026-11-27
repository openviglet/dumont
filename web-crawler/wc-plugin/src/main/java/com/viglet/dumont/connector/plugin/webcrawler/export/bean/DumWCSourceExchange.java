package com.viglet.dumont.connector.plugin.webcrawler.export.bean;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumWCSourceExchange {
    private String id;
    private String title;
    private String description;
    private Locale locale;
    private String localeClass;
    private String url;
    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();
    private String username;
    private String password;
    @Builder.Default
    private Collection<String> startingPoints = new HashSet<>();
    @Builder.Default
    private Collection<String> allowUrls = new HashSet<>();
    @Builder.Default
    private Collection<String> notAllowUrls = new HashSet<>();
    @Builder.Default
    private Collection<String> notAllowExtensions = new HashSet<>();
    @Builder.Default
    private Collection<DumWCAttribExchange> attributes = new HashSet<>();

    // Roadmap DU17: Jackson creates this bean without the builder defaults, so a file that leaves
    // a collection out reads as empty here rather than failing the import on a null.

    public Collection<String> getTurSNSites() {
        if (turSNSites == null) {
            turSNSites = new HashSet<>();
        }
        return turSNSites;
    }

    public Collection<String> getStartingPoints() {
        if (startingPoints == null) {
            startingPoints = new HashSet<>();
        }
        return startingPoints;
    }

    public Collection<String> getAllowUrls() {
        if (allowUrls == null) {
            allowUrls = new HashSet<>();
        }
        return allowUrls;
    }

    public Collection<String> getNotAllowUrls() {
        if (notAllowUrls == null) {
            notAllowUrls = new HashSet<>();
        }
        return notAllowUrls;
    }

    public Collection<String> getNotAllowExtensions() {
        if (notAllowExtensions == null) {
            notAllowExtensions = new HashSet<>();
        }
        return notAllowExtensions;
    }

    public Collection<DumWCAttribExchange> getAttributes() {
        if (attributes == null) {
            attributes = new HashSet<>();
        }
        return attributes;
    }
}
