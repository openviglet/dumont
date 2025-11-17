package com.viglet.dumont.connector.webcrawler.commons.ext;

import java.util.Date;
import java.util.Optional;

import com.viglet.dumont.connector.webcrawler.commons.DumWCContext;
import com.viglet.turing.client.sn.TurMultiValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumWCExtDate implements DumWCExtInterface {
    @Override
    public Optional<TurMultiValue> consume(DumWCContext context) {
        return Optional.of(TurMultiValue.singleItem(new Date()));
    }
}
