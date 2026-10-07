package dev.mcoder.etl.pojospreadsheet.io;

import java.util.Objects;

/**
 * The name of the sheet to create, and whether to write a header row of column labels.
 *
 * <pre>{@code
 * WriteOptions.defaults().sheet("Employees").header(false)
 * }</pre>
 */
public record WriteOptions(String sheetName, boolean header) {

    public WriteOptions {
        Objects.requireNonNull(sheetName, "sheetName");
    }

    /**
     * A sheet named {@code Sheet1}, with a header row.
     */
    public static WriteOptions defaults() {
        return new WriteOptions("Sheet1", true);
    }

    public WriteOptions sheet(String name) {
        return new WriteOptions(name, header);
    }

    public WriteOptions header(boolean header) {
        return new WriteOptions(sheetName, header);
    }
}
