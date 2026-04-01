package com.viglet.dumont.connector.aem.commons.bean;

public enum DumAemEvent {
    INDEXING {
        @Override
        public String toString() {
            return "INDEXING";
        }
    },
    DEINDEXING {
        @Override
        public String toString() {
            return "DEINDEXING";
        }
    },
    PUBLISHING {
        @Override
        public String toString() {
            return "PUBLISHING";
        }
    },
    UNPUBLISHING {
        @Override
        public String toString() {
            return "UNPUBLISHING";
        }
    }
}
