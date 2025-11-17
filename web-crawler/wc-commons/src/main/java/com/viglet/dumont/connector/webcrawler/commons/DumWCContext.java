package com.viglet.dumont.connector.webcrawler.commons;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;

@Builder
@Getter
@Setter
public class DumWCContext {
    private String url;
    private String userAgent;
    private String referrer;
    private int timeout;
    private Document document;
}
