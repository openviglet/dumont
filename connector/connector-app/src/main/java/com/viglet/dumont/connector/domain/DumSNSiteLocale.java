package com.viglet.dumont.connector.domain;

import java.io.Serializable;
import java.util.Locale;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DumSNSiteLocale implements Serializable {
    private Locale language;
    private String core;
    private DumSNSite dumSNSite;
}