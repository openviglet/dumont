package com.viglet.dumont.aem.server.core.events.beans;

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
