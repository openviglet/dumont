package com.viglet.dumont.connector.commons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DU6: source names")
class DumSourceNamesTest {

    @Test
    @DisplayName("a name without whitespace is valid")
    void acceptsAPlainName() {
        assertTrue(DumSourceNames.isValid("wknd-site_2"));
    }

    @Test
    @DisplayName("a space, a tab or a newline anywhere makes it invalid")
    void refusesWhitespace() {
        assertFalse(DumSourceNames.isValid("wknd site"));
        assertFalse(DumSourceNames.isValid("wknd\tsite"));
        assertFalse(DumSourceNames.isValid(" wknd"));
        assertFalse(DumSourceNames.isValid("wknd\n"));
    }

    @Test
    @DisplayName("a null name is left to each source's own required check")
    void leavesNullToTheCaller() {
        assertTrue(DumSourceNames.isValid(null));
    }
}
