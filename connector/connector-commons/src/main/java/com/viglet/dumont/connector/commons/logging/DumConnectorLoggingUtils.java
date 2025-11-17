package com.viglet.dumont.connector.commons.logging;

import java.util.Date;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;
import com.viglet.dumont.commons.logging.DumLoggingIndexing;
import com.viglet.dumont.commons.logging.DumLoggingIndexingLog;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

public class DumConnectorLoggingUtils {
    public static final String URL = "url";

    public static void setSuccessStatus(TurSNJobItem turSNJobItem, DumConnectorSession session,
            DumIndexingStatus status) {
        setSuccessStatus(turSNJobItem, session, status, null);
    }

    public static void setSuccessStatus(TurSNJobItem turSNJobItem, DumConnectorSession session,
            DumIndexingStatus status, String details) {
        DumLoggingIndexingLog.setStatus(DumLoggingIndexing.builder().contentId(turSNJobItem.getId())
                .url(turSNJobItem.getStringAttribute(URL))
                .environment(turSNJobItem.getEnvironment()).locale(turSNJobItem.getLocale())
                .status(status).resultStatus(DumLoggingStatus.SUCCESS)
                .transactionId(session.getTransactionId()).checksum(turSNJobItem.getChecksum())
                .source(session.getSource()).date(new Date()).sites(turSNJobItem.getSiteNames())
                .details(details).build());
    }

    public static void setSuccessStatus(DumJobItemWithSession turSNJobItemWithSession,
            DumIndexingStatus status) {
        setSuccessStatus(turSNJobItemWithSession.turSNJobItem(), status);
    }

    public static void setSuccessStatus(TurSNJobItem turSNJobItem, DumIndexingStatus status) {
        DumLoggingIndexingLog.setStatus(DumLoggingIndexing.builder().contentId(turSNJobItem.getId())
                .url(turSNJobItem.getStringAttribute(URL))
                .environment(turSNJobItem.getEnvironment()).locale(turSNJobItem.getLocale())
                .status(status).resultStatus(DumLoggingStatus.SUCCESS)
                .checksum(turSNJobItem.getChecksum()).date(new Date())
                .sites(turSNJobItem.getSiteNames()).build());
    }

}
