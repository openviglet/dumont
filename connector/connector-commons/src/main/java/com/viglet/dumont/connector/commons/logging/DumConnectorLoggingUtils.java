package com.viglet.dumont.connector.commons.logging;

import java.time.Instant;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.commons.indexing.DumLoggingStatus;
import com.viglet.dumont.commons.logging.DumLoggingIndexing;
import com.viglet.dumont.commons.logging.DumLoggingIndexingLog;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/**
 * Writes one document's progress into the indexing log.
 *
 * <p>
 * Every overload here takes the source and transaction id, one way or another, because a row
 * without them cannot be filtered by source or followed through a run — and a document whose
 * history names a source at one status and nobody at the next is worse than useless to a reader
 * tracing it. There used to be an overload that took neither (roadmap <strong>D71</strong>); six
 * call sites used it while holding the session, and the queue used it because past that boundary
 * there is no session at all. {@link DumJobTrail} is what the queue reads instead.
 */
public class DumConnectorLoggingUtils {
    public static final String URL = "url";

    private DumConnectorLoggingUtils() {
        throw new IllegalStateException("Connector Logging Utility class");
    }

    public static void setSuccessStatus(TurSNJobItem turSNJobItem, DumConnectorSession session,
            DumIndexingStatus status) {
        setSuccessStatus(turSNJobItem, session, status, null);
    }

    public static void setSuccessStatus(TurSNJobItem turSNJobItem, DumConnectorSession session,
            DumIndexingStatus status, String details) {
        setSuccessStatus(turSNJobItem, session == null ? null : session.getSource(),
                session == null ? null : session.getTransactionId(), status, details);
    }

    /**
     * The item and the session it was produced under, which is the shape every strategy already
     * holds — passing the pair is what keeps a document's rows attributed all the way through.
     */
    public static void setSuccessStatus(DumJobItemWithSession turSNJobItemWithSession,
            DumIndexingStatus status) {
        setSuccessStatus(turSNJobItemWithSession.turSNJobItem(), turSNJobItemWithSession.session(),
                status, null);
    }

    /**
     * The one place a log row is built. Source and transaction id are named separately rather than
     * read off a session, because past the queue there is no session — the listener recovers them
     * from the item's trail (D71) and calls this.
     */
    public static void setSuccessStatus(TurSNJobItem turSNJobItem, String source,
            String transactionId, DumIndexingStatus status, String details) {
        DumLoggingIndexingLog.setStatus(DumLoggingIndexing.builder().contentId(turSNJobItem.getId())
                .url(turSNJobItem.getStringAttribute(URL))
                .environment(turSNJobItem.getEnvironment()).locale(turSNJobItem.getLocale())
                .status(status).resultStatus(DumLoggingStatus.SUCCESS)
                .transactionId(transactionId).checksum(turSNJobItem.getChecksum())
                .source(source).date(Instant.now()).sites(turSNJobItem.getSiteNames())
                .details(details).build());
    }
}
