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
}
