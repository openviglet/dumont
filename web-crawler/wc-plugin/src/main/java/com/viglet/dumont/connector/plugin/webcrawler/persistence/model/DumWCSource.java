package com.viglet.dumont.connector.plugin.webcrawler.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.dumont.spring.jpa.DumUuid;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "wc_source")
public class DumWCSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    public static final String TUR_WC_SOURCE = "dumWCSource";
    public static final String SOURCE_ID = "source_id";
    public static final String WC_SN_SITE = "wc_sn_site";
    public static final String SN_SITE = "sn_site";

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;
    @Column
    private String title;
    @Column
    private String description;
    @Column
    private Locale locale;
    @Column
    private String localeClass;
    @Column
    private String url;
    @Column
    private String username;
    @Column
    private String password;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = WC_SN_SITE, joinColumns = @JoinColumn(name = SOURCE_ID))
    @Column(name = SN_SITE, nullable = false)
    private Collection<String> turSNSites = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY, cascade = {
            CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumWCStartingPoint> startingPoints = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY, cascade = {
            CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumWCAllowUrl> allowUrls = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY, cascade = {
            CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumWCNotAllowUrl> notAllowUrls = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY, cascade = {
            CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumWCFileExtension> notAllowExtensions = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY, cascade = {
            CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumWCAttributeMapping> attributeMappings = new HashSet<>();

    public void setStartingPoints(Collection<DumWCStartingPoint> startingPoints) {
        this.startingPoints.clear();
        if (startingPoints != null) {
            this.startingPoints.addAll(startingPoints);
        }
    }

    public void setAllowUrls(Collection<DumWCAllowUrl> allowUrls) {
        this.allowUrls.clear();
        if (allowUrls != null) {
            this.allowUrls.addAll(allowUrls);
        }
    }

    public void setNotAllowUrls(Collection<DumWCNotAllowUrl> notAllowUrls) {
        this.notAllowUrls.clear();
        if (notAllowUrls != null) {
            this.notAllowUrls.addAll(notAllowUrls);
        }
    }

    public void setNotAllowExtensions(Collection<DumWCFileExtension> notAllowExtensions) {
        this.notAllowExtensions.clear();
        if (notAllowExtensions != null) {
            this.notAllowExtensions.addAll(notAllowExtensions);
        }
    }

    public void setAttributeMappings(Collection<DumWCAttributeMapping> attributeMappings) {
        this.attributeMappings.clear();
        if (attributeMappings != null) {
            this.attributeMappings.addAll(attributeMappings);
        }
    }

    public void setTurSNSites(Collection<String> turSNSites) {
        this.turSNSites.clear();
        if (turSNSites != null) {
            this.turSNSites.addAll(turSNSites);
        }
    }
}
