package com.viglet.dumont.connector.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.google.common.collect.Lists;
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

    @NotNull
    public Map<String, List<String>> solrExtraContent(String source, String provider) {
        Map<String, List<String>> solrExtraContentMap = new HashMap<>();
        for (String site : indexingService.getSites(source, provider)) {
            for (String environment : indexingService.getEnvironment(site, provider)) {
                dumontLocale(site).forEach(siteLocale -> {
                    try {
                        SolrQuery query = new SolrQuery();
                        query.setQuery("*:*");
                        query.setFields(ID);
                        query.setRows(Integer.MAX_VALUE);
                        QueryResponse response = solrClient.query(siteLocale.getCore(), query);
                        List<String> solrIds = new ArrayList<>(response.getResults().stream()
                                .map(solrDocument -> (String) solrDocument.getFieldValue(ID))
                                .toList());
                        List<String> connectorIds = new ArrayList<>();
                        for (List<String> partition : Lists.partition(solrIds, 100)) {
                            connectorIds.addAll(indexingService
                                    .validateObjectIdList(source, environment, siteLocale, provider, partition));
                        }
                        solrIds.removeAll(connectorIds);
                        solrExtraContentMap.put(siteLocale.getCore(), solrIds);
                    } catch (IOException | SolrServerException e) {
                        log.error(e.getMessage(), e);
                    }
                });
            }
        }
        return solrExtraContentMap;
    }

    @NotNull
    public Map<String, List<String>> solrMissingContent(String source, String provider) {
        Map<String, List<String>> solrMissingContentMap = new HashMap<>();
        for (String site : indexingService.getSites(source, provider)) {
            for (String environment : indexingService.getEnvironment(site, provider)) {
                dumontLocale(site).forEach(siteLocale -> {
                    List<String> outputIdList = new ArrayList<>();
                    List<String> objectIdList = indexingService.getObjectIdList(source, environment, siteLocale,
                            provider);
                    try {
                        for (List<String> partition : Lists.partition(objectIdList, 20)) {
                            SolrDocumentList documents = solrClient.getById(siteLocale.getCore(), partition);
                            documents.forEach(document -> outputIdList.add(document.get(ID).toString()));
                        }
                    } catch (IOException | SolrServerException e) {
                        log.error(e.getMessage(), e);
                    }
                    objectIdList.removeAll(outputIdList);
                    solrMissingContentMap.put(siteLocale.getCore(), objectIdList);
                });
            }
        }
        return solrMissingContentMap;
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
