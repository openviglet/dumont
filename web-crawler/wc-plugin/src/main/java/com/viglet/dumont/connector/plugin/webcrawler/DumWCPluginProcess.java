package com.viglet.dumont.connector.plugin.webcrawler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;
import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.dumont.connector.webcrawler.commons.ext.DumWCExtInterface;
import com.viglet.dumont.connector.webcrawler.commons.ext.DumWCExtLocaleInterface;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import generator.RandomUserAgentGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumWCPluginProcess {
    public static final String MAILTO = "mailto";
    public static final String TEL = "tel:";
    public static final String JAVASCRIPT = "javascript:";
    public static final String A_HREF = "a[href]";
    public static final String ABS_HREF = "abs:href";
    public static final String WILD_CARD = "*";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BASIC = "Basic";
    public static final String WEB_CRAWLER = "WEB-CRAWLER";
    private final List<String> startingPoints = new ArrayList<>();
    private final List<String> allowUrls = new ArrayList<>();
    private final List<String> allowStartsWithUrls = new ArrayList<>();
    private final List<String> notAllowUrls = new ArrayList<>();
    private final List<String> notAllowStartsWithUrls = new ArrayList<>();
    private final List<String> notAllowExtensions = new ArrayList<>();
    private final DumWCStartingPointRepository dumWCStartingPointsRepository;
    private final String userAgent = RandomUserAgentGenerator.getNextNonMobile();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Set<String> indexedLinks = new HashSet<>();
    private final Queue<String> queueLinks = new LinkedList<>();
    private String website;
    private Collection<String> snSites;
    private final int timeout;
    private final String referrer;
    private String username;
    private String password;
    private final DumWCAllowUrlRepository dumWCAllowUrlRepository;
    private final DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    private final DumWCFileExtensionRepository dumWCFileExtensionRepository;
    private final DumWCAttributeMappingRepository dumWCAttributeMappingRepository;
    private final DumConnectorContext dumConnectorContext;

    public DumWCPluginProcess(@Value("${dumont.wc.timeout:5000}") int timeout,
            @Value("${dumont.wc.referrer:https://www.google.com}") String referrer,
            DumWCAllowUrlRepository dumWCAllowUrlRepository,
            DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository,
            DumWCFileExtensionRepository dumWCFileExtensionRepository,
            DumWCAttributeMappingRepository dumWCAttributeMappingRepository,
            DumWCStartingPointRepository dumWCStartingPointsRepository,
            DumConnectorContext dumConnectorContext) {
        this.timeout = timeout;
        this.referrer = referrer;
        this.dumWCAllowUrlRepository = dumWCAllowUrlRepository;
        this.dumWCNotAllowUrlRepository = dumWCNotAllowUrlRepository;
        this.dumWCFileExtensionRepository = dumWCFileExtensionRepository;
        this.dumWCAttributeMappingRepository = dumWCAttributeMappingRepository;
        this.dumWCStartingPointsRepository = dumWCStartingPointsRepository;
        this.dumConnectorContext = dumConnectorContext;
    }

    public void start(DumWCSource dumWCSource) {
        DumConnectorSession dumConnectorSession = getSource(dumWCSource);
        dumWCFileExtensionRepository.findByDumWCSource(dumWCSource)
                .ifPresent(source -> source.forEach(dumWCFileExtension -> this.notAllowExtensions
                        .add(dumWCFileExtension.getExtension())));
        dumWCNotAllowUrlRepository.findByDumWCSource(dumWCSource)
                .ifPresent(source -> source.forEach(dumWCNotAllowUrl -> {
                    if (dumWCNotAllowUrl.getUrl().trim().endsWith(WILD_CARD)) {
                        this.notAllowStartsWithUrls
                                .add(StringUtils.chop(dumWCNotAllowUrl.getUrl()));
                    } else {
                        this.notAllowUrls.add(dumWCNotAllowUrl.getUrl());
                    }
                }));
        dumWCAllowUrlRepository.findByDumWCSource(dumWCSource)
                .ifPresent(source -> source.forEach(dumWCAllowUrl -> {
                    if (dumWCAllowUrl.getUrl().trim().endsWith(WILD_CARD)) {
                        this.allowStartsWithUrls
                                .add(StringUtils.chop(dumWCAllowUrl.getUrl().trim()));
                    } else {
                        this.allowUrls.add(dumWCAllowUrl.getUrl());
                    }
                }));
        dumWCStartingPointsRepository.findByDumWCSource(dumWCSource)
                .ifPresent(source -> source.forEach(dumWCStartingPoint -> this.startingPoints
                        .add(dumWCStartingPoint.getUrl())));
        this.website = dumWCSource.getUrl();
        this.snSites = dumWCSource.getTurSNSites();
        this.username = dumWCSource.getUsername();
        this.password = dumWCSource.getPassword();
        log.info("User Agent: {}", userAgent);
        startingPoints.forEach(url -> {
            queueLinks.offer(this.website + url);
            getPagesFromQueue(dumWCSource, dumConnectorSession);
        });
        finished(dumConnectorContext, dumConnectorSession);
    }

    private static DumConnectorSession getSource(DumWCSource dumWCSource) {
        return new DumConnectorSession(dumWCSource.getId(), dumWCSource.getTurSNSites(),
                WEB_CRAWLER, dumWCSource.getLocale());
    }

    private static void finished(DumConnectorContext dumConnectorContext,
            DumConnectorSession source) {
        dumConnectorContext.finishIndexing(source, false);
    }

    private void getPagesFromQueue(DumWCSource dumWCSource, DumConnectorSession source) {
        while (!queueLinks.isEmpty()) {
            String url = queueLinks.poll();
            getPage(dumWCSource, url, source);
        }
    }

    private void getPage(DumWCSource dumWCSource, String url, DumConnectorSession source) {
        try {
            log.info("{}: {}", url, dumWCSource.getTurSNSites());
            Document document = getHTML(url);
            String checksum = getCRC32Checksum(document.html().getBytes());
            getPageLinks(document);
            String pageUrl = getPageUrl(url);
            if (canBeIndexed(pageUrl)) {
                indexedLinks.add(pageUrl);
                log.info("WC is creating a Job Item: {}", url);
                addTurSNJobItem(dumWCSource, document, url, checksum, source);
                return;
            } else {
                log.debug("Ignored: {}", url);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        new TurSNJobItem();
    }

    private void getPageLinks(Document document) {
        document.select(A_HREF).forEach(page -> addPageToQueue(getPageUrl(page.attr(ABS_HREF))));
    }

    private void addPageToQueue(String pageUrl) {
        if (canBeAddToQueue(pageUrl) && visitedLinks.add(pageUrl) && !queueLinks.offer(pageUrl)) {
            log.error("Item didn't add to queue: {}", pageUrl);
        }
    }

    private boolean isValidToAddQueue(String pageUrl) {
        return isNotMailUrl(pageUrl) && isNotTelUrl(pageUrl)
                && !StringUtils.equalsAny(pageUrl, queueLinks.toArray(new String[0]))
                && !isSharpUrl(pageUrl) && !isPagination(pageUrl) && !isJavascriptUrl(pageUrl)
                && pageUrl.startsWith(this.website)
                && (StringUtils.startsWithAny(getRelativePageUrl(pageUrl),
                        allowStartsWithUrls.toArray(new String[0]))
                        || StringUtils.equalsAny(getRelativePageUrl(pageUrl),
                                allowUrls.toArray(new String[0])))
                && !StringUtils.startsWithAny(getRelativePageUrl(pageUrl),
                        notAllowStartsWithUrls.toArray(new String[0]))
                && !StringUtils.equalsAny(getRelativePageUrl(pageUrl),
                        notAllowUrls.toArray(new String[0]))
                && !StringUtils.endsWithAny(pageUrl, notAllowExtensions.toArray(new String[0]));
    }

    private void addTurSNJobItem(DumWCSource dumWCSource, Document document, String url,
            String checksum, DumConnectorSession source) {
        DumJobItemWithSession dumJobItemWithSession = new DumJobItemWithSession(
                new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(snSites),
                        getLocale(dumWCSource, document, url),
                        getJobItemAttributes(dumWCSource, document, url), null, checksum),
                source, Collections.emptySet(), false);
        dumConnectorContext.addJobItem(dumJobItemWithSession);
    }

    public static String getCRC32Checksum(byte[] bytes) {
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return String.valueOf(crc32.getValue());
    }

    private Map<String, Object> getJobItemAttributes(DumWCSource dumWCSource, Document document,
            String url) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        dumWCAttributeMappingRepository.findByDumWCSource(dumWCSource)
                .ifPresent(source -> source.forEach(dumWCCustomClass -> Optional
                        .ofNullable(dumWCCustomClass.getText()).ifPresentOrElse(
                                text -> usesText(dumWCCustomClass, text, turSNJobItemAttributes),
                                () -> {
                                    if (!StringUtils.isEmpty(dumWCCustomClass.getClassName()))
                                        usesCustomClass(document, url, dumWCCustomClass,
                                                turSNJobItemAttributes);
                                })));
        return turSNJobItemAttributes;
    }

    private void usesCustomClass(Document document, String url,
            DumWCAttributeMapping dumWCCustomClass, Map<String, Object> turSNJobItemAttributes) {
        getCustomClass(document, url, dumWCCustomClass)
                .ifPresent(turMultiValue -> turMultiValue.forEach(attributeValue -> {
                    if (!StringUtils.isBlank(attributeValue)) {
                        if (turSNJobItemAttributes.containsKey(dumWCCustomClass.getName())) {
                            addItemInExistingAttribute(attributeValue, turSNJobItemAttributes,
                                    dumWCCustomClass.getName());
                        } else {
                            addFirstItemToAttribute(dumWCCustomClass.getName(), attributeValue,
                                    turSNJobItemAttributes);
                        }
                    }
                }));
    }

    private static void usesText(DumWCAttributeMapping dumWCCustomClass, String text,
            Map<String, Object> turSNJobItemAttributes) {
        turSNJobItemAttributes.put(dumWCCustomClass.getName(), text);
    }

    private Optional<TurMultiValue> getCustomClass(Document document, String url,
            DumWCAttributeMapping dumWCAttributeMapping) {
        return DumCustomClassCache.getCustomClassMap(dumWCAttributeMapping.getClassName())
                .flatMap(classInstance -> ((DumWCExtInterface) classInstance)
                        .consume(getDumWCContext(document, url)));
    }

    private static void addItemInExistingAttribute(String attributeValue,
            Map<String, Object> attributes, String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList)
            addItemToArray(attributes, attributeName, attributeValue);
        else
            convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
            String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName,
            String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private void addFirstItemToAttribute(String attributeName, String attributeValue,
            Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    private Locale getLocale(DumWCSource dumWCSource, Document document, String url) {
        return Optional.ofNullable(dumWCSource.getLocale()).orElseGet(() -> {
            if (!StringUtils.isEmpty(dumWCSource.getLocaleClass())) {
                return DumCustomClassCache.getCustomClassMap(dumWCSource.getLocaleClass())
                        .map(classInstance -> ((DumWCExtLocaleInterface) classInstance)
                                .consume(getDumWCContext(document, url)))
                        .orElse(Locale.US);
            }
            return Locale.US;
        });
    }

    private DumWCContext getDumWCContext(Document document, String url) {
        return DumWCContext.builder().document(document).url(url).timeout(timeout)
                .userAgent(userAgent).referrer(referrer).build();
    }

    private boolean canBeIndexed(String pageUrl) {
        return isValidToAddQueue(pageUrl)
                && !StringUtils.equalsAny(pageUrl, indexedLinks.toArray(new String[0]));
    }

    private boolean canBeAddToQueue(String pageUrl) {
        return isValidToAddQueue(pageUrl)
                && !StringUtils.equalsAny(pageUrl, visitedLinks.toArray(new String[0]));
    }

    private static boolean isJavascriptUrl(String pageUrl) {
        return pageUrl.contains(JAVASCRIPT);
    }

    private String getPageUrl(String attr) {
        String pageUrl = getUrlWithoutParameters(
                !isHttpUrl(attr) && isNotMailUrl(attr) && isNotTelUrl(attr) ? this.website + attr
                        : attr);
        String pageUrlNormalized = pageUrl.endsWith("/") ? removeLastChar(pageUrl) : pageUrl;
        if (isNotMailUrl(attr) && isNotTelUrl(attr)) {
            try {
                return URI.create(pageUrlNormalized).normalize().toString();
            } catch (IllegalArgumentException ignored) {
                // No error
            }
        }
        return pageUrlNormalized;
    }

    private static String removeLastChar(String pageUrl) {
        return pageUrl.substring(0, pageUrl.length() - 1);
    }

    private Document getHTML(String url) throws IOException {
        Connection connection = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).timeout(timeout);
        if (isBasicAuth()) {
            connection.header(AUTHORIZATION, "%s %s".formatted(BASIC, getBasicAuth()));
        }
        Document document = connection.get();

        document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        document.outputSettings().charset(StandardCharsets.ISO_8859_1);
        document.charset(StandardCharsets.ISO_8859_1);
        return document;
    }

    private String getBasicAuth() {
        return Base64.getEncoder()
                .encodeToString("%s:%s".formatted(this.username, this.password).getBytes());
    }

    private boolean isBasicAuth() {
        return this.username != null;
    }

    private String getRelativePageUrl(String pageUrl) {
        return pageUrl.replaceAll(this.website, "");
    }

    private static boolean isPagination(String pageUrl) {
        return pageUrl.contains("/page/");
    }

    private static boolean isSharpUrl(String attr) {
        return attr.contains("#");
    }

    private static boolean isHttpUrl(String attr) {
        return attr.toLowerCase().startsWith("http");
    }

    private static boolean isNotMailUrl(String attr) {
        return !attr.toLowerCase().startsWith(MAILTO);
    }

    private static boolean isNotTelUrl(String attr) {
        return !attr.toLowerCase().startsWith(TEL);
    }

    private String getUrlWithoutParameters(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null,
                    uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }
}
