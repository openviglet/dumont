package com.viglet.dumont.connector.aem.commons.bean;

public enum DumAemEvent {
    UNPUBLISHING {
        @Override
        public String toString() {
            return "UNPUBLISHING";
        }
    },
    PUBLISHING {
        @Override
        public String toString() {
            return "PUBLISHING";
        }
    },
    NONE {
        @Override
        public String toString() {
            return "NONE";
        }
    }
}
