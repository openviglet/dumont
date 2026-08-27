package com.viglet.dumont.connector.plugin.aem.service;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtUrlAttributeInterface;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.plugin.aem.conf.AemPluginHandlerConfiguration;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemPluginSystem;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemSource;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemPluginSystemRepository;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemSourceRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumAemSourceService {
    private static final String URL_ATTRIBUTE = "url";
    private final DumAemSourceRepository dumAemSourceRepository;
    private final DumAemPluginSystemRepository dumAemPluginSystemRepository;
    private final DumAemContentMappingService dumAemContentMappingService;
    private final boolean queryBuilderEnabled;
    private final String assetSearchPath;

    public DumAemSourceService(DumAemSourceRepository dumAemSourceRepository,
            DumAemPluginSystemRepository dumAemPluginSystemRepository,
            DumAemContentMappingService dumAemContentMappingService,
            @Value("${dumont.aem.querybuilder:false}") boolean queryBuilderEnabled,
            @Value("${dumont.aem.delivery.assetSearchPath:}") String assetSearchPath) {
        this.dumAemSourceRepository = dumAemSourceRepository;
        this.dumAemPluginSystemRepository = dumAemPluginSystemRepository;
        this.dumAemContentMappingService = dumAemContentMappingService;
        this.queryBuilderEnabled = queryBuilderEnabled;
        this.assetSearchPath = assetSearchPath;
    }

    public List<DumAemSource> getAllSources() {
        return dumAemSourceRepository.findAll();
    }

    public Optional<DumAemSource> getDumAemSourceByName(String source) {
        return dumAemSourceRepository.findByName(source);
    }

    public Optional<DumAemSource> getDumAemSourceById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return dumAemSourceRepository.findById(id);
    }

    public DumConnectorSession getDumConnectorSession(DumAemSource dumAemSource) {
        return new DumConnectorSession(dumAemSource.getName(), getProviderName(),
                dumAemSource.getDefaultLocale());
    }

    public static String getProviderName() {
        return AEM;
    }

    public boolean isOnce(DumAemConfiguration configuration) {
        return dumAemPluginSystemRepository
                .findByConfig(DumAemCommonsUtils.configOnce(configuration))
                .map(DumAemPluginSystem::isBooleanValue).orElse(false);
    }

    /**
     * The source's configuration, with the global QueryBuilder flag folded in.
     * <p>
     * QueryBuilder is forced off for a source on the delivery fetch strategy (roadmap
     * <strong>D61</strong>): {@code /bin/querybuilder.json} is an author-tier servlet the publish tier
     * does not expose, so the global flag must not reach a source that has no author to ask.
     */
    public DumAemConfiguration getDumAemConfiguration(DumAemSource dumAemSource) {
        DumAemConfiguration config = new DumAemConfiguration(
                new AemPluginHandlerConfiguration(dumAemSource));
        return config.toBuilder()
                .queryBuilderEnabled(queryBuilderEnabled && !config.isDeliveryFetch())
                // D67: blank means the Assets Search API's default mount on the delivery host.
                .assetSearchPath(assetSearchPath)
                .build();
    }

    public boolean isPublish(DumAemConfiguration configuration) {
        return configuration.isPublish()
                && StringUtils.isNotEmpty(configuration.getPublishSNSite());
    }

    public boolean isAuthor(DumAemConfiguration configuration) {
        return configuration.isAuthor() && StringUtils.isNotEmpty(configuration.getAuthorSNSite());
    }

    public String resolveIdFromUrl(String source, String url) {
        String id = getDumAemSourceByName(source)
                .flatMap(dumAemSource -> dumAemContentMappingService
                        .getAttributeSpecification(dumAemSource, URL_ATTRIBUTE)
                        .flatMap(spec -> {
                            String className = spec.getClassName();
                            log.debug("ClassName : {}", className);
                            return DumCustomClassCache.getCustomClassMap(className)
                                    .map(classInstance -> {
                                        DumAemConfiguration configuration = getDumAemConfiguration(dumAemSource);
                                        return ((DumAemExtUrlAttributeInterface) classInstance).getIdFromUrl(url,
                                                configuration);
                                    });
                        }))
                .orElse(null);
        log.debug("Resolved ID from URL '{}': {}", url, id);
        return id;
    }
}
