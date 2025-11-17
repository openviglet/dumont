package com.viglet.dumont.connector.commons.domain;

import java.util.Set;

import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.turing.client.sn.job.TurSNJobItem;

public record DumJobItemWithSession(TurSNJobItem turSNJobItem, DumConnectorSession session,
        Set<String> dependencies, boolean standalone) {

}
