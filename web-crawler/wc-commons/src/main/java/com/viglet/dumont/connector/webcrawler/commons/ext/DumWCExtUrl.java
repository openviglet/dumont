package com.viglet.dumont.connector.webcrawler.commons.ext;

import java.util.Optional;

import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.turing.client.sn.TurMultiValue;

public class DumWCExtUrl implements DumWCExtInterface {

    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getUrl()));
    }
}
