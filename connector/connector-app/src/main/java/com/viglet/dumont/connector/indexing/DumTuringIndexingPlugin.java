/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.indexing;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.viglet.dumont.connector.commons.domain.DumIndexInventoryEntry;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.plugin.DumIndexingDeliveryException;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.inventory.TurSNInventoryClient;
import com.viglet.turing.client.sn.inventory.TurSNInventoryEntry;
import com.viglet.turing.client.sn.inventory.TurSNInventoryException;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.client.sn.manifest.TurSNManifest;
import com.viglet.turing.client.sn.manifest.TurSNManifestException;
import com.viglet.turing.client.sn.manifest.TurSNManifestResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexing plugin for Viglet Turing ES
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "dumont.indexing.provider", havingValue = "turing", matchIfMissing = true)
public class DumTuringIndexingPlugin implements DumIndexingPlugin {
    
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final String turingUrl;
    private final String turingApiKey;
    private final Function<TurSNManifest, TurSNManifestResult> provisioner;
    private final Supplier<Optional<String>> seInstanceLookup;

    @Autowired
    public DumTuringIndexingPlugin(@Value("${turing.url}") String turingUrl,
            @Value("${turing.apiKey}") String turingApiKey) {
        this(turingUrl, turingApiKey, null, null);
        log.info("Initialized Turing indexing plugin with URL: {}", turingUrl);
    }

    /** The manifest transport and the SE-instance lookup are the seams a test replaces. */
    DumTuringIndexingPlugin(String turingUrl, String turingApiKey,
            Function<TurSNManifest, TurSNManifestResult> provisioner,
            Supplier<Optional<String>> seInstanceLookup) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.provisioner = provisioner != null ? provisioner : m -> getTurSNServer().provision(m);
        this.seInstanceLookup = seInstanceLookup != null ? seInstanceLookup : this::firstSeInstance;
    }

    @Override
    public void index(TurSNJobItems turSNJobItems) {
        log.debug("Indexing {} items to Turing ES", turSNJobItems.getTuringDocuments().size());
        // D138: importItems logs a failed delivery and answers false; ignoring that recorded the batch
        // as indexed while Turing never received it.
        if (!TurSNJobUtils.importItems(turSNJobItems, getTurSNServer(), false)) {
            throw new DumIndexingDeliveryException("Turing did not accept a batch of %d item(s) at %s"
                    .formatted(turSNJobItems.getTuringDocuments().size(), turingUrl));
        }
    }

    @Override
    public String getProviderName() {
        return "TURING";
    }

    /**
     * Converges the site and its field schema through Turing's manifest endpoint before the first
     * document (roadmap <strong>D63</strong>). Re-posting the same manifest is a server-side no-op,
     * so this is safe on every run and needs no local "already provisioned" bookkeeping.
     * <p>
     * A rejected manifest does <em>not</em> stop the run. A {@code 409} means Turing kept the site's
     * current schema, so indexing proceeds exactly as it did before this call existed — with fields
     * inferred at index time. Refusing to index would take a working index offline over a schema
     * drift the operator may not be able to resolve at that moment, so the failure is reported
     * loudly, naming the fields that would have had to be rewritten, and the documents still flow.
     * <p>
     * Two refusals are answered rather than reported (D340), each at most once, so the first run
     * against a new or older site indexes with no manual step:
     * <ul>
     * <li>a site that does not exist and so needs a search-engine instance (400): the call is made
     * again on the first enabled instance Turing lists, as the WordPress plugin does;</li>
     * <li>a field the site holds with another type or cardinality (409): the call is made again
     * declaring {@code RECREATE} for exactly the fields Turing named. The source's schema is what
     * every document of this run carries, and this is a full run, so the field is refilled by the
     * documents that follow.</li>
     * </ul>
     */
    @Override
    public Optional<String> provision(DumIndexingSchema schema) {
        Attempt attempt = new Attempt(null, Set.of());
        while (true) {
            try {
                logProvisioned(schema, provisioner.apply(manifest(schema, attempt.seInstanceId(),
                        attempt.recreate())));
                return Optional.empty();
            } catch (TurSNManifestException e) {
                Optional<Attempt> next = retry(schema, attempt, e);
                if (next.isEmpty()) {
                    return Optional.of(refusal(schema, e));
                }
                attempt = next.get();
            }
        }
    }

    /** What one provisioning call names beyond the schema: the instance and the fields to recreate. */
    private record Attempt(String seInstanceId, Set<String> recreate) {
    }

    /**
     * The call that answers this refusal, or none when it is to be reported. Each answer is given
     * once: the instance is named only while none is, and the fields are recreated only while none
     * is, so the loop ends after at most three calls.
     */
    private Optional<Attempt> retry(DumIndexingSchema schema, Attempt attempt, TurSNManifestException e) {
        if (attempt.seInstanceId() == null && needsSeInstance(e)) {
            Optional<String> instance = seInstanceLookup.get();
            instance.ifPresentOrElse(
                    id -> log.info("Site '{}' does not exist; creating it on search engine instance {}",
                            schema.siteName(), id),
                    () -> log.error("Site '{}' does not exist and Turing lists no search engine "
                            + "instance to create it on", schema.siteName()));
            return instance.map(id -> new Attempt(id, attempt.recreate()));
        }
        if (e.isMigrationRequired() && attempt.recreate().isEmpty() && !e.getBreakingChanges().isEmpty()) {
            Set<String> fields = new LinkedHashSet<>();
            e.getBreakingChanges().forEach(change -> fields.add(change.field()));
            log.warn("Site '{}' holds field(s) {} with another type or cardinality than the source "
                    + "declares; recreating them: {}", schema.siteName(), fields, e.getBreakingChanges());
            return Optional.of(new Attempt(attempt.seInstanceId(), fields));
        }
        return Optional.empty();
    }

    private static void logProvisioned(DumIndexingSchema schema, TurSNManifestResult result) {
        if (result.isNoOp()) {
            log.debug("Site '{}' schema already matches the {} declared field(s), {} custom "
                    + "facet(s) and {} custom sort(s)", schema.siteName(), schema.fields().size(),
                    schema.customFacets().size(), schema.customSorts().size());
        } else {
            log.info("Provisioned site '{}' (created={}) from {} declared field(s); migrated: {}; "
                    + "custom facets changed: {}; custom sorts changed: {}", result.siteName(),
                    result.siteCreated(), schema.fields().size(), result.fieldsMigrated(),
                    result.customFacetsChanged(), result.customSortsChanged());
        }
    }

    private static String refusal(DumIndexingSchema schema, TurSNManifestException e) {
        if (e.isMigrationRequired()) {
            log.error("Site '{}' schema needs a declared migration — indexing continues with the "
                    + "site's current schema. Breaking change(s): {}", schema.siteName(),
                    e.getBreakingChanges());
            return "site '%s' kept its current schema: the declared one needs a migration (%s)"
                    .formatted(schema.siteName(), e.getBreakingChanges());
        }
        log.error("Could not provision site '{}': {} — indexing continues with the site's "
                + "current schema", schema.siteName(), e.getMessage(), e);
        return "site '%s' was not provisioned: %s".formatted(schema.siteName(), e.getMessage());
    }

    /** Turing's 400 for a manifest naming a site that does not exist, and no instance to create it on. */
    static boolean needsSeInstance(TurSNManifestException e) {
        return e.getStatusCode() == 400 && e.getMessage() != null && e.getMessage().contains("seInstanceId");
    }

    /**
     * The search-engine instance a new site is created on: the first enabled one Turing lists
     * ({@code GET /api/se}), or the first at all when none says it is enabled.
     */
    /**
     * D343: asked only after deliveries failed in a row. Turing's public discovery endpoint, with
     * short timeouts: no answer, or a server error, is down; any other answer means Turing is up and
     * refused the batches for what they held.
     */
    @Override
    public Optional<String> unreachable() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HEALTH_TIMEOUT);
        factory.setReadTimeout(HEALTH_TIMEOUT);
        try {
            RestClient.builder().baseUrl(turingUrl).requestFactory(factory).build()
                    .get().uri("/api/discovery").retrieve().toBodilessEntity();
            return Optional.empty();
        } catch (RestClientResponseException e) {
            return e.getStatusCode().is5xxServerError()
                    ? Optional.of("Viglet Turing at %s answers %d".formatted(turingUrl,
                            e.getStatusCode().value()))
                    : Optional.empty();
        } catch (RestClientException e) {
            return Optional.of("Viglet Turing unreachable at %s".formatted(turingUrl));
        }
    }

    private RestClient turing() {
        return RestClient.builder().baseUrl(turingUrl).defaultHeader("Key", turingApiKey).build();
    }

    /**
     * D342: the site is looked up by name among Turing's SN sites, then its locales. Turing
     * answers a missing site's locale list with an empty list, so the site is checked first. A
     * Turing that cannot be asked answers nothing here: that is a delivery failure, not a missing
     * site.
     */
    @Override
    public Optional<String> missingSite(String siteName, Locale locale) {
        try {
            RestClient turing = turing();
            List<Map<String, Object>> sites = turing.get().uri("/api/sn").retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return missingSite(siteName, locale, sites, () -> turing.get()
                    .uri("/api/sn/name/{siteName}/locale", siteName).retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }));
        } catch (RestClientException e) {
            log.warn("Could not check SN site '{}' at {}: {}", siteName, turingUrl, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> missingSite(String siteName, Locale locale, List<Map<String, Object>> sites,
            Supplier<List<Map<String, Object>>> locales) {
        boolean exists = sites != null && sites.stream()
                .anyMatch(site -> siteName.equalsIgnoreCase(String.valueOf(site.get("name"))));
        if (!exists) {
            return Optional.of("SN site '%s' not found in Viglet Turing".formatted(siteName));
        }
        if (locale == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> held = locales.get();
        boolean hasLocale = held != null && held.stream()
                .anyMatch(entry -> sameLocale(locale, entry.get("language")));
        return hasLocale ? Optional.empty()
                : Optional.of("SN site '%s' has no locale %s in Viglet Turing".formatted(siteName, locale));
    }

    private static boolean sameLocale(Locale locale, Object language) {
        return language != null
                && locale.toString().equalsIgnoreCase(String.valueOf(language).replace('-', '_'));
    }

    private Optional<String> firstSeInstance() {
        try {
            List<Map<String, Object>> instances = turing()
                    .get().uri("/api/se")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return chooseSeInstance(instances);
        } catch (RestClientException e) {
            log.warn("Could not list Turing's search engine instances at {}: {}", turingUrl, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> chooseSeInstance(List<Map<String, Object>> instances) {
        if (instances == null || instances.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> chosen = instances.stream()
                .filter(DumTuringIndexingPlugin::isEnabled)
                .findFirst()
                .orElse(instances.getFirst());
        return Optional.ofNullable(chosen.get("id")).map(String::valueOf);
    }

    private static boolean isEnabled(Map<String, Object> instance) {
        Object enabled = instance.get("enabled");
        return enabled instanceof Boolean b ? b : enabled instanceof Number n && n.intValue() != 0;
    }

    /**
     * The manifest a schema converges to. Custom facets and sorts (D144) go on the same call as the
     * fields; the builder sends none when none is declared, which leaves the site's own untouched.
     */
    static TurSNManifest manifest(DumIndexingSchema schema) {
        return manifest(schema, null, Set.of());
    }

    /** The same, naming the instance a new site is created on and the fields to recreate (D340). */
    static TurSNManifest manifest(DumIndexingSchema schema, String seInstanceId, Set<String> recreate) {
        TurSNManifest.Builder builder = TurSNManifest.builder()
                .name(schema.siteName())
                .locale(schema.locale())
                .fields(schema.fields());
        if (seInstanceId != null) {
            builder.seInstanceId(seInstanceId);
        }
        recreate.forEach(builder::recreate);
        schema.customFacets().forEach(builder::customFacet);
        schema.customSorts().forEach(builder::customSort);
        return builder.build();
    }

    /**
     * Enumerates what the index actually holds for a source (roadmap <strong>D64</strong>), walking
     * Turing's inventory cursor across every locale of the site.
     * <p>
     * An unreadable index comes back as {@link Optional#empty()}, never as an empty list. The
     * distinction is the whole point: the audit acts on a difference, and reporting a failed read as
     * "the index holds nothing" would have it republish the entire source.
     */
    @Override
    public Optional<List<DumIndexInventoryEntry>> inventory(String siteName, String source) {
        try {
            List<DumIndexInventoryEntry> entries = new ArrayList<>();
            for (TurSNInventoryEntry entry : new TurSNInventoryClient(getTurSNServer(), siteName,
                    TurSNInventoryClient.DEFAULT_ROWS).allLocales(source)) {
                entries.add(new DumIndexInventoryEntry(entry.id(), entry.version(),
                        entry.checksum()));
            }
            log.debug("Index inventory for site '{}' source '{}': {} document(s)", siteName, source,
                    entries.size());
            return Optional.of(entries);
        } catch (TurSNInventoryException e) {
            log.error("Could not read the index inventory for site '{}' source '{}': {}", siteName,
                    source, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private TurSNServer getTurSNServer() {
        return new TurSNServer(URI.create(turingUrl), null,
                new TurApiKeyCredentials(turingApiKey));
    }
}
