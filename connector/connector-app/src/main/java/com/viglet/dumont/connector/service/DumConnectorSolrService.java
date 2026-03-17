package com.viglet.dumont.connector.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CursorMarkParams;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.domain.DumConnectorValidateDifference;
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
        Map<String, List<String>> missingMap = new HashMap<>();
        Map<String, List<String>> extraMap = new HashMap<>();
        for (String site : indexingService.getSites(source, provider)) {
            List<DumSNSiteLocale> locales = dumontLocale(site);
            for (String environment : indexingService.getEnvironment(site, provider)) {
                locales.forEach(siteLocale -> computeDifferences(
                        source, environment, siteLocale, provider, missingMap, extraMap));
            }
        }
        return DumConnectorValidateDifference.builder()
                .missing(missingMap).extra(extraMap).build();
    }

    private void computeDifferences(String source, String environment,
            DumSNSiteLocale siteLocale, String provider,
            Map<String, List<String>> missingMap, Map<String, List<String>> extraMap) {
        try {
            Set<String> solrIds = fetchAllSolrIds(siteLocale.getCore());
            Set<String> dbIds = new HashSet<>(indexingService.getObjectIdList(
                    source, environment, siteLocale, provider));

            List<String> extraIds = solrIds.stream()
                    .filter(id -> !dbIds.contains(id)).toList();
            List<String> missingIds = dbIds.stream()
                    .filter(id -> !solrIds.contains(id)).toList();

            extraMap.put(siteLocale.getCore(), extraIds);
            missingMap.put(siteLocale.getCore(), missingIds);
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
    }

    private Set<String> fetchAllSolrIds(String core) throws IOException, SolrServerException {
        Set<String> solrIds = new HashSet<>();
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        while (true) {
            SolrQuery query = new SolrQuery();
            query.setQuery("*:*");
            query.setFields(ID);
            query.setRows(1000);
            query.setSort(ID, SolrQuery.ORDER.asc);
            query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response = solrClient.query(core, query);
            response.getResults()
                    .forEach(doc -> solrIds.add((String) doc.getFieldValue(ID)));
            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }
        return solrIds;
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
