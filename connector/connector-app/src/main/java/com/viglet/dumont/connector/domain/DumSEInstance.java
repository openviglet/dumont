package com.viglet.dumont.connector.domain;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DumSEInstance implements Serializable {
    private String host;
    private int port;
}
