package com.viglet.dumont.commons.se;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import com.viglet.dumont.commons.sn.bean.DumSNFilterParams;
import com.viglet.dumont.commons.sn.bean.DumSNSearchParams;
import com.viglet.dumont.commons.sn.bean.DumSNSitePostParamsBean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DumSEParameters implements Serializable {
        private String query;
        private DumSNFilterParams dumSNFilterParams;
        private List<String> boostQueries;
        private List<String> fieldList;
        private Integer currentPage;
        private String sort;
        private Integer rows;
        private String group;
        private Integer autoCorrectionDisabled;
        private DumSNSitePostParamsBean dumSNSitePostParamsBean;

        public DumSEParameters(DumSNSearchParams dumSNSearchParams) {
                this(dumSNSearchParams, null);
        }

        public DumSEParameters(DumSNSearchParams dumSNSearchParams,
                        DumSNSitePostParamsBean gDumSNSitePostParamsBean) {
                super();
                DumSNFilterParams dumSNFilterParams = DumSNFilterParams.builder()
                                .defaultValues(dumSNSearchParams.getFq())
                                .and(dumSNSearchParams.getFqAnd()).or(dumSNSearchParams.getFqOr())
                                .operator(dumSNSearchParams.getFqOp())
                                .itemOperator(dumSNSearchParams.getFqiOp()).build();

                this.query = dumSNSearchParams.getQ();
                this.dumSNFilterParams = dumSNFilterParams;
                this.currentPage = dumSNSearchParams.getP();
                this.sort = dumSNSearchParams.getSort();
                this.rows = dumSNSearchParams.getRows();
                this.group = dumSNSearchParams.getGroup();
                this.autoCorrectionDisabled = dumSNSearchParams.getNfpr();
                this.fieldList = dumSNSearchParams.getFl();
                this.dumSNSitePostParamsBean = gDumSNSitePostParamsBean;
                overrideFromPost(gDumSNSitePostParamsBean);
        }

        private void overrideFromPost(DumSNSitePostParamsBean postParamsBean) {
                if (postParamsBean == null) {
                        return;
                }

                setSort(Optional.ofNullable(postParamsBean.getSort()).orElse(sort));
                setRows(Optional.ofNullable(postParamsBean.getRows()).orElse(rows));
                setGroup(Optional.ofNullable(postParamsBean.getGroup()).orElse(group));
                setCurrentPage(Optional.ofNullable(postParamsBean.getPage()).orElse(currentPage));
                setQuery(Optional.ofNullable(postParamsBean.getQuery()).orElse(query));
                setFieldList(Optional.ofNullable(postParamsBean.getFieldList()).orElse(fieldList));

                if (dumSNFilterParams != null) {
                        dumSNFilterParams.setDefaultValues(
                                        Optional.ofNullable(postParamsBean.getFq()).orElse(
                                                        dumSNFilterParams.getDefaultValues()));
                        dumSNFilterParams.setAnd(Optional.ofNullable(postParamsBean.getFqAnd())
                                        .orElse(dumSNFilterParams.getAnd()));
                        dumSNFilterParams.setOr(Optional.ofNullable(postParamsBean.getFqOr())
                                        .orElse(dumSNFilterParams.getOr()));
                        dumSNFilterParams.setOperator(
                                        Optional.ofNullable(postParamsBean.getFqOperator())
                                                        .orElse(dumSNFilterParams.getOperator()));
                }
        }
}
