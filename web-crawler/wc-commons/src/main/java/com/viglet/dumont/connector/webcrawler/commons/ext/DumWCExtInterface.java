package com.viglet.dumont.connector.webcrawler.commons.ext;

import java.util.Optional;

import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.turing.client.sn.TurMultiValue;

public interface DumWCExtInterface {
    Optional<TurMultiValue> consume(DumWCContext context);
}
