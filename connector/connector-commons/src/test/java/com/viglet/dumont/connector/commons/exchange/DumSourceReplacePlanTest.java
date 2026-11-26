package com.viglet.dumont.connector.commons.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DU9: replacing import plan")
class DumSourceReplacePlanTest {

    @Test
    @DisplayName("matches by name: new names are created, known ones overwritten, missing ones removed")
    void matchesByName() {
        DumSourceReplacePlan plan = DumSourceReplacePlan.of(List.of("wknd", "news"), List.of("news", "legacy"),
                true);

        assertEquals(List.of("wknd"), plan.create());
        assertEquals(List.of("news"), plan.overwrite());
        assertEquals(List.of("legacy"), plan.remove());
        assertTrue(plan.isApplicable());
    }

    @Test
    @DisplayName("an empty file removes everything")
    void emptyFileRemovesAll() {
        DumSourceReplacePlan plan = DumSourceReplacePlan.of(List.of(), List.of("a", "b"), true);

        assertEquals(List.of("a", "b"), plan.remove());
        assertTrue(plan.create().isEmpty());
    }

    @Test
    @DisplayName("blank, repeated and spaced names make the plan inapplicable")
    void refusesInvalidNames() {
        DumSourceReplacePlan plan = DumSourceReplacePlan.of(Arrays.asList("ok", " ", null, "ok", "wknd site"),
                List.of(), true);

        assertFalse(plan.isApplicable());
        assertEquals(List.of(" ", "null", "ok", "wknd site"), plan.invalid());
        assertTrue(plan.refusal().getMessage().contains("nothing was changed"));
    }

    @Test
    @DisplayName("a display-only title may hold spaces")
    void titlesMayHoldSpaces() {
        DumSourceReplacePlan plan = DumSourceReplacePlan.of(List.of("Viglet site"), List.of(), false);

        assertTrue(plan.isApplicable());
        assertEquals(List.of("Viglet site"), plan.create());
    }

    @Test
    @DisplayName("the mode parses case-insensitively, defaults to add and refuses anything else")
    void parsesTheMode() {
        assertEquals(DumSourceImportMode.ADD, DumSourceImportMode.of(null));
        assertEquals(DumSourceImportMode.ADD, DumSourceImportMode.of(" "));
        assertEquals(DumSourceImportMode.REPLACE, DumSourceImportMode.of("Replace"));
        assertThrows(DumSourceImportException.class, () -> DumSourceImportMode.of("overwrite"));
    }
}
