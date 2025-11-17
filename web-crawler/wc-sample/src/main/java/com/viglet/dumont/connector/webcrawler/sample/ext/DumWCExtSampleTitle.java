package com.viglet.dumont.connector.webcrawler.sample.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.dumont.connector.webcrawler.commons.ext.DumWCExtInterface;

import java.util.Optional;

public class DumWCExtSampleTitle implements DumWCExtInterface {
    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getDocument().title()));
    }
}
