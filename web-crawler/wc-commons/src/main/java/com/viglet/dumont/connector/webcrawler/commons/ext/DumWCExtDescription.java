package com.viglet.dumont.connector.webcrawler.commons.ext;

import java.util.Optional;

import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.turing.client.sn.TurMultiValue;

public class DumWCExtDescription implements DumWCExtInterface {

    public static final String META_NAME_DESCRIPTION = "meta[name=description]";
    public static final String CONTENT = "content";

    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        return Optional.of(context.getDocument().select(META_NAME_DESCRIPTION))
                .map(elements -> !elements.isEmpty() ? TurMultiValue.singleItem(elements.getFirst().attr(CONTENT))
                        : null);

    }
}
