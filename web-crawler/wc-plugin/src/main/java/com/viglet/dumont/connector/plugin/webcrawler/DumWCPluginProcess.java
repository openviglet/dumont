package com.viglet.dumont.connector.plugin.webcrawler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([^;\\s]+)",
            Pattern.CASE_INSENSITIVE);
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
    private final long maxCrawlDelayMs;
    private final int sitemapMaxDepth;
    private String username;
    private String password;
    private long requestDelayMs;
    private final DumWCAllowUrlRepository dumWCAllowUrlRepository;
    private final DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository;
    private final DumWCFileExtensionRepository dumWCFileExtensionRepository;
    private final DumWCAttributeMappingRepository dumWCAttributeMappingRepository;
    private final DumConnectorContext dumConnectorContext;

    public DumWCPluginProcess(@Value("${dumont.wc.timeout:5000}") int timeout,
            @Value("${dumont.wc.referrer:https://www.google.com}") String referrer,
            @Value("${dumont.wc.crawlDelay.maxMs:30000}") long maxCrawlDelayMs,
            @Value("${dumont.wc.sitemap.maxDepth:3}") int sitemapMaxDepth,
            DumWCAllowUrlRepository dumWCAllowUrlRepository,
            DumWCNotAllowUrlRepository dumWCNotAllowUrlRepository,
            DumWCFileExtensionRepository dumWCFileExtensionRepository,
            DumWCAttributeMappingRepository dumWCAttributeMappingRepository,
            DumWCStartingPointRepository dumWCStartingPointsRepository,
            DumConnectorContext dumConnectorContext) {
        this.timeout = timeout;
        this.referrer = referrer;
        this.maxCrawlDelayMs = maxCrawlDelayMs;
        this.sitemapMaxDepth = sitemapMaxDepth;
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
        // D06: robots.txt feeds both sitemap discovery and the advertised crawl delay.
        DumWCRobotsTxt robots = sitemapEnabled(dumWCSource) ? fetchRobotsTxt() : DumWCRobotsTxt.EMPTY;
        this.requestDelayMs = effectiveDelayMs(dumWCSource.getCrawlDelay(), robots.crawlDelayMs(),
                maxCrawlDelayMs);
        log.info("Request delay: {} ms", requestDelayMs);
        startingPoints.forEach(url -> queueLinks.offer(this.website + url));
        if (sitemapEnabled(dumWCSource)) {
            seedFromSitemaps(robots);
        }
        getPagesFromQueue(dumWCSource, dumConnectorSession);
        finished(dumConnectorContext, dumConnectorSession);
    }

    private static DumConnectorSession getSource(DumWCSource dumWCSource) {
        return new DumConnectorSession(dumWCSource.getId(),
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
            applyRequestDelay();
        }
    }

    private static boolean sitemapEnabled(DumWCSource dumWCSource) {
        return Boolean.TRUE.equals(dumWCSource.getEnableSitemap());
    }

    /**
     * The delay to enforce between requests (roadmap D06): the larger of the per-source configured
     * delay and the {@code Crawl-delay} advertised in {@code robots.txt}, the latter capped so a
     * hostile or misconfigured directive cannot stall a crawl indefinitely.
     */
    static long effectiveDelayMs(Integer configuredDelayMs, long robotsCrawlDelayMs,
            long maxCrawlDelayMs) {
        long configured = configuredDelayMs != null && configuredDelayMs > 0 ? configuredDelayMs : 0L;
        long robots = robotsCrawlDelayMs > 0 ? Math.min(robotsCrawlDelayMs, maxCrawlDelayMs) : 0L;
        return Math.max(configured, robots);
    }

    private void applyRequestDelay() {
        if (requestDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Discover entry points from the sitemaps advertised in {@code robots.txt}, falling back to the
     * conventional {@code /sitemap.xml} when none are advertised (roadmap D06). Sitemap-index
     * documents are followed up to {@code dumont.wc.sitemap.maxDepth} levels.
     */
    private void seedFromSitemaps(DumWCRobotsTxt robots) {
        List<String> roots = robots.sitemaps().isEmpty()
                ? List.of(baseUrl() + "/sitemap.xml")
                : robots.sitemaps();
        Set<String> pages = new HashSet<>();
        Set<String> visitedSitemaps = new HashSet<>();
        roots.forEach(sitemap -> collectSitemapPages(sitemap, sitemapMaxDepth, pages, visitedSitemaps));
        log.info("Sitemap discovery seeded {} entry point(s) from {} sitemap(s)", pages.size(),
                visitedSitemaps.size());
        pages.forEach(this::seedEntryPoint);
    }

    private void collectSitemapPages(String sitemapUrl, int depthLeft, Set<String> pages,
            Set<String> visitedSitemaps) {
        if (depthLeft < 0 || !visitedSitemaps.add(sitemapUrl)) {
            return;
        }
        DumWCSitemap sitemap = fetchSitemap(sitemapUrl);
        if (sitemap == null) {
            return;
        }
        if (sitemap.index()) {
            sitemap.locations()
                    .forEach(child -> collectSitemapPages(child, depthLeft - 1, pages, visitedSitemaps));
        } else {
            pages.addAll(sitemap.locations());
        }
    }

    private void seedEntryPoint(String absoluteUrl) {
        String pageUrl = getPageUrl(absoluteUrl);
        if (pageUrl.startsWith(this.website) && visitedLinks.add(pageUrl)) {
            queueLinks.offer(pageUrl);
        }
    }

    private DumWCRobotsTxt fetchRobotsTxt() {
        String robotsUrl = baseUrl() + "/robots.txt";
        try {
            return DumWCRobotsTxt.parse(fetchAsString(robotsUrl), userAgent);
        } catch (IOException e) {
            log.warn("Could not fetch robots.txt at {}: {}", robotsUrl, e.getMessage());
            return DumWCRobotsTxt.EMPTY;
        }
    }

    private DumWCSitemap fetchSitemap(String sitemapUrl) {
        try {
            return DumWCSitemap.parse(connection(sitemapUrl).execute().bodyAsBytes(), sitemapUrl);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not fetch sitemap at {}: {}", sitemapUrl, e.getMessage());
            return null;
        }
    }

    private String fetchAsString(String url) throws IOException {
        return connection(url).execute().body();
    }

    private Connection connection(String url) {
        Connection connection = Jsoup.connect(url).userAgent(userAgent).referrer(referrer)
                .timeout(timeout).ignoreContentType(true);
        if (isBasicAuth()) {
            connection.header(AUTHORIZATION, "%s %s".formatted(BASIC, getBasicAuth()));
        }
        return connection;
    }

    private String baseUrl() {
        return this.website.endsWith("/") ? removeLastChar(this.website) : this.website;
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
                && !queueLinks.contains(pageUrl)
                && !isSharpUrl(pageUrl) && !isPagination(pageUrl) && !isJavascriptUrl(pageUrl)
                && pageUrl.startsWith(this.website)
                && (allowStartsWithUrls.stream().anyMatch(getRelativePageUrl(pageUrl)::startsWith)
                        || allowUrls.contains(getRelativePageUrl(pageUrl)))
                && notAllowStartsWithUrls.stream().noneMatch(getRelativePageUrl(pageUrl)::startsWith)
                && !notAllowUrls.contains(getRelativePageUrl(pageUrl))
                && notAllowExtensions.stream().noneMatch(pageUrl::endsWith);
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
                && !indexedLinks.contains(pageUrl);
    }

    private boolean canBeAddToQueue(String pageUrl) {
        return isValidToAddQueue(pageUrl)
                && !visitedLinks.contains(pageUrl);
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
        Connection.Response response = connection.execute();
        return parseDocument(response.bodyAsBytes(), response.contentType(), response.url().toString());
    }

    /**
     * Parse an HTML body, auto-detecting its charset instead of assuming ISO-8859-1
     * (roadmap D05). Detection precedence follows the same order Jsoup applies: the
     * HTTP {@code Content-Type} header charset wins, then a byte-order mark, then the
     * {@code <meta charset>} declaration, defaulting to UTF-8 when nothing is declared.
     * Output settings keep the detected charset so non-Latin-1 text (e.g. pt-BR
     * accents) survives serialization.
     */
    static Document parseDocument(byte[] body, String contentType, String baseUri) throws IOException {
        Document document = Jsoup.parse(new ByteArrayInputStream(body), charsetFromContentType(contentType), baseUri);
        document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        document.outputSettings().charset(document.charset());
        return document;
    }

    /**
     * Extract a supported charset name from an HTTP {@code Content-Type} header, or
     * {@code null} when absent/unsupported so Jsoup falls back to BOM / {@code <meta charset>}
     * / UTF-8 detection.
     */
    static String charsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.find()) {
            String charset = matcher.group(1).trim().replace("\"", "");
            if (Charset.isSupported(charset)) {
                return charset;
            }
        }
        return null;
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
