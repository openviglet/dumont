package com.viglet.dumont.commons.sn.bean;

import java.util.List;
import java.util.Locale;

import com.viglet.dumont.commons.sn.search.DumSNFilterQueryOperator;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DumSNSearchParams {
    private String q = "*";
    private Integer p = 1;
    private List<String> fq;
    private List<String> fqAnd;
    private List<String> fqOr;
    private DumSNFilterQueryOperator fqOp = DumSNFilterQueryOperator.NONE;
    private DumSNFilterQueryOperator fqiOp = DumSNFilterQueryOperator.NONE;
    private String sort = "relevance";
    private Integer rows = -1;
    private Locale locale;
    private List<String> fl;
    private String group;
    private Integer nfpr = 1;

}
