package com.viglet.dumont.connector.persistence.specification;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;

import jakarta.persistence.criteria.Join;

public final class DumConnectorIndexingSpecification {

    private DumConnectorIndexingSpecification() {
    }

    public static Specification<DumConnectorIndexingModel> fromRequest(
            DumConnectorMonitoringRequest request, String provider) {
        return Specification.where(withProvider(provider))
                .and(withSource(request.getSource()))
                .and(withObjectIdContaining(request.getObjectId()))
                .and(withStatuses(request))
                .and(withEnvironment(request.getEnvironment()))
                .and(withLocale(request.getLocale()))
                .and(withSite(request.getSite()))
                .and(withDateFrom(request))
                .and(withDateTo(request));
    }

    private static Specification<DumConnectorIndexingModel> withProvider(String provider) {
        return (root, query, cb) -> provider == null ? null : cb.equal(root.get("provider"), provider);
    }

    private static Specification<DumConnectorIndexingModel> withSource(String source) {
        return (root, query, cb) -> (source == null || source.isBlank())
                ? null
                : cb.equal(root.get("source"), source);
    }

    private static Specification<DumConnectorIndexingModel> withObjectIdContaining(String objectId) {
        return (root, query, cb) -> (objectId == null || objectId.isBlank())
                ? null
                : cb.like(cb.lower(root.get("objectId")), "%" + objectId.toLowerCase() + "%");
    }

    private static Specification<DumConnectorIndexingModel> withStatuses(
            DumConnectorMonitoringRequest request) {
        return (root, query, cb) -> (request.getStatuses() == null || request.getStatuses().isEmpty())
                ? null
                : root.get("status").in(request.getStatuses());
    }

    private static Specification<DumConnectorIndexingModel> withEnvironment(String environment) {
        return (root, query, cb) -> (environment == null || environment.isBlank())
                ? null
                : cb.equal(root.get("environment"), environment);
    }

    private static Specification<DumConnectorIndexingModel> withLocale(String locale) {
        return (root, query, cb) -> {
            if (locale == null || locale.isBlank()) return null;
            return cb.equal(root.get("locale"), Locale.forLanguageTag(locale.replace("_", "-")));
        };
    }

    private static Specification<DumConnectorIndexingModel> withSite(String site) {
        return (root, query, cb) -> {
            if (site == null || site.isBlank()) return null;
            Join<Object, Object> sitesJoin = root.join("sites");
            return cb.equal(sitesJoin, site);
        };
    }

    private static Specification<DumConnectorIndexingModel> withDateFrom(
            DumConnectorMonitoringRequest request) {
        return (root, query, cb) -> request.getDateFrom() == null
                ? null
                : cb.greaterThanOrEqualTo(root.get("modificationDate"), request.getDateFrom());
    }

    private static Specification<DumConnectorIndexingModel> withDateTo(
            DumConnectorMonitoringRequest request) {
        return (root, query, cb) -> request.getDateTo() == null
                ? null
                : cb.lessThanOrEqualTo(root.get("modificationDate"), request.getDateTo());
    }
}
