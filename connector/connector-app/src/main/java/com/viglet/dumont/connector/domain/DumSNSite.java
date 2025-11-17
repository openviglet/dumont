package com.viglet.dumont.connector.domain;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DumSNSite implements Serializable {
    private String name;
    private DumSEInstance dumSEInstance;
}
