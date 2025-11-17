package com.viglet.dumont.commons.sn.search;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */

public enum DumSNFilterQueryOperator {
    AND {
        @Override
        public String toString() {
            return "AND";
        }
    },
    OR {
        @Override
        public String toString() {
            return "OR";
        }
    },
    NONE {
        @Override
        public String toString() {
            return "NONE";
        }
    }
}
