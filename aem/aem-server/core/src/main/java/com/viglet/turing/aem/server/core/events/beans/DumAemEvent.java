package com.viglet.dumont.aem.server.core.events.beans;

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
