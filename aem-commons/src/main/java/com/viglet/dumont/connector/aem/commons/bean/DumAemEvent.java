package com.viglet.dumont.connector.aem.commons.bean;

public enum DumAemEvent {
    UNPUBLISHING {
        public String toString() {
            return "UNPUBLISHING";
        }
    },
    PUBLISHING {
        public String toString() {
            return "PUBLISHING";
        }
    },
    NONE {
        public String toString() {
            return "NONE";
        }
    }
}
