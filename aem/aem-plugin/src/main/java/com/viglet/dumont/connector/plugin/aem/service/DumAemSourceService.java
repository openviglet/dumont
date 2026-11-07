package com.viglet.dumont.connector.plugin.aem.service;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.ONCE;

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

    /**
     * Whether paths matching the source's {@code oncePattern} are skipped now (D344): the source has
     * completed its {@code onceTimes} full runs (1 when unset). A row from before D344 carries only
     * the flag, which meant "done".
     */
    public boolean isOnce(DumAemConfiguration configuration) {
        Optional<DumAemPluginSystem> row = dumAemPluginSystemRepository
                .findByConfig(DumAemCommonsUtils.configOnce(configuration));
        if (row.isEmpty()) {
            return false;
        }
        if (row.get().getStringValue() == null) {
            return row.get().isBooleanValue();
        }
        return onceRuns(row.get()) >= onceTimes(getDumAemSourceById(configuration.getId()).orElse(null));
    }

    /** The full runs counted toward {@code onceTimes}, and how many there are to count. */
    public record OnceProgress(int runs, int times) {
    }

    public OnceProgress onceProgress(DumAemSource source) {
        int runs = dumAemPluginSystemRepository.findByConfig(onceConfig(source))
                .map(DumAemSourceService::onceRuns).orElse(0);
        return new OnceProgress(runs, onceTimes(source));
    }

    /**
     * Counts one completed full run toward {@code onceTimes} (D344). Only a source with an
     * {@code oncePattern} counts, and the count stops at the target, so raising {@code onceTimes}
     * later indexes the pattern's paths again for the difference.
     */
    public void recordFullRun(DumAemSource source) {
        if (StringUtils.isBlank(source.getOncePattern())) {
            return;
        }
        String config = onceConfig(source);
        DumAemPluginSystem row = dumAemPluginSystemRepository.findByConfig(config)
                .orElseGet(() -> new DumAemPluginSystem().setConfig(config));
        int times = onceTimes(source);
        int runs = Math.min(onceRuns(row) + 1, times);
        dumAemPluginSystemRepository.save(row.setStringValue(Integer.toString(runs))
                .setBooleanValue(runs >= times));
        log.info("AEM source '{}': full run {} of {} that index the once pattern's paths",
                source.getName(), runs, times);
    }

    /** Starts the count again, so the once pattern's paths are indexed for another {@code onceTimes} runs. */
    public void resetOnce(DumAemSource source) {
        dumAemPluginSystemRepository.findByConfig(onceConfig(source))
                .ifPresent(dumAemPluginSystemRepository::delete);
    }

    private static String onceConfig(DumAemSource source) {
        return "%s/%s".formatted(source.getId(), ONCE);
    }

    private static int onceRuns(DumAemPluginSystem row) {
        if (row.getStringValue() == null) {
            return row.isBooleanValue() ? Integer.MAX_VALUE : 0;
        }
        try {
            return Integer.parseInt(row.getStringValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int onceTimes(DumAemSource source) {
        Integer times = source == null ? null : source.getOnceTimes();
        return times == null || times < 1 ? 1 : times;
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
