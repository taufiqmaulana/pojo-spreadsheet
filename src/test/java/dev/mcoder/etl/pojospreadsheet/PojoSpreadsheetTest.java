package dev.mcoder.etl.pojospreadsheet;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class PojoSpreadsheetTest {

    @Test
    void versionIsPresent() {
        assertNotNull(PojoSpreadsheet.version());
    }
}
