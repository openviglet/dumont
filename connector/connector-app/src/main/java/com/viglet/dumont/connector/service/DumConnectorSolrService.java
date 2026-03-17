package com.viglet.dumont.connector.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.RemoteSolrException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CursorMarkParams;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
import com.viglet.dumont.connector.domain.DumSNSite;
import com.viglet.dumont.connector.domain.DumSNSiteLocale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DumConnectorSolrService {
    private static final String ID = "id";
    private final DumConnectorIndexingService indexingService;
    private final SolrClient solrClient;
    private final RestClient restClient;

    public DumConnectorSolrService(DumConnectorIndexingService indexingService,
            SolrClient solrClient,
            RestClient restClient) {
        this.indexingService = indexingService;
        this.solrClient = solrClient;
        this.restClient = restClient;
    }

    public DumConnectorValidateDifference validateContent(String source, String provider) {
        log.debug("Starting validation for source={}, provider={}", source, provider);
        Map<String, List<String>> missingMap = new HashMap<>();
        Map<String, List<String>> extraMap = new HashMap<>();

        List<String> sites = indexingService.getSites(source, provider);
        if (sites.isEmpty()) {
            log.debug("No sites found in indexing for source={}, falling back to Dumont API", source);
            sites = dumontSites();
        }

        for (String site : sites) {
            List<DumSNSiteLocale> locales = dumontLocale(site);
            log.debug("Site={} has {} locale(s)", site, locales.size());
            List<String> environments = indexingService.getEnvironment(site, provider);
            if (environments.isEmpty()) {
                locales.forEach(siteLocale -> computeDifferences(
                        source, null, siteLocale, provider, missingMap, extraMap));
            } else {
                for (String environment : environments) {
                    locales.forEach(siteLocale -> computeDifferences(
                            source, environment, siteLocale, provider, missingMap, extraMap));
                }
            }
        }
        log.debug("Validation complete for source={}: {} core(s) processed", source, missingMap.size());
        return DumConnectorValidateDifference.builder()
                .missing(missingMap).extra(extraMap).build();
    }

    private void computeDifferences(String source, String environment,
            DumSNSiteLocale siteLocale, String provider,
            Map<String, List<String>> missingMap, Map<String, List<String>> extraMap) {
        String core = siteLocale.getCore();
        try {
            log.debug("Fetching Solr IDs for core={}", core);
            Set<String> solrIds = fetchAllSolrIds(core);
            log.debug("Fetched {} Solr IDs for core={}", solrIds.size(), core);

            log.debug("Fetching DB IDs for core={}, source={}, environment={}", core, source, environment);
            Set<String> dbIds = environment != null
                    ? new HashSet<>(indexingService.getObjectIdList(
                            source, environment, siteLocale, provider))
                    : new HashSet<>();
            log.debug("Fetched {} DB IDs for core={}", dbIds.size(), core);

            List<String> extraIds = solrIds.stream()
                    .filter(id -> !dbIds.contains(id)).toList();
            List<String> missingIds = dbIds.stream()
                    .filter(id -> !solrIds.contains(id)).toList();

            log.debug("Core={}: {} extra, {} missing", core, extraIds.size(), missingIds.size());
            extraMap.put(core, extraIds);
            missingMap.put(core, missingIds);
        } catch (RemoteSolrException e) {
            log.warn("Core={} not found in Solr, skipping: {}", core, e.getMessage());
        } catch (IOException | SolrServerException e) {
            log.error("Failed to compute differences for core={}: {}", core, e.getMessage(), e);
        }
    }

    private Set<String> fetchAllSolrIds(String core) throws IOException, SolrServerException {
        Set<String> solrIds = new HashSet<>();
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        int pageCount = 0;
        while (true) {
            SolrQuery query = new SolrQuery();
            query.setQuery("*:*");
            query.setFields(ID);
            query.setRows(1000);
            query.setSort(ID, SolrQuery.ORDER.asc);
            query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrClient.query(core, query);
            int batchSize = response.getResults().size();
            response.getResults()
                    .forEach(doc -> solrIds.add((String) doc.getFieldValue(ID)));
            pageCount++;
            log.debug("Core={}: fetched page {} ({} docs, {} total so far)",
                    core, pageCount, batchSize, solrIds.size());
            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }
        return solrIds;
    }

    private List<String> dumontSites() {
        try {
            DumSNSite[] sites = restClient.get()
                    .uri("/api/sn")
                    .retrieve()
                    .body(DumSNSite[].class);

            return sites != null
                    ? Arrays.stream(sites).map(DumSNSite::getName).toList()
                    : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to retrieve sites from Dumont API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<DumSNSiteLocale> dumontLocale(String snSite) {
        if (snSite == null) {
            return Collections.emptyList();
        }

        try {
            DumSNSiteLocale[] dumSNSiteLocaleList = restClient.get()
                    .uri(String.format("/api/sn/name/%s/locale", snSite))
                    .retrieve()
                    .body(DumSNSiteLocale[].class);

            return dumSNSiteLocaleList != null
                    ? Arrays.asList(dumSNSiteLocaleList)
                    : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to retrieve locales for site: {}", snSite, e);
            return Collections.emptyList();
        }
    }

    public boolean hasContentIdAtSolr(String id, String source, String provider) {
        for (String site : indexingService.getSites(source, provider)) {
            for (DumSNSiteLocale siteLocale : dumontLocale(site)) {
                try {
                    if (solrClient.getById(siteLocale.getCore(), id) != null)
                        return true;
                } catch (IOException | SolrServerException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return false;
    }
}
