package dev.mcoder.etl.pojospreadsheet.io;

import java.util.Objects;

/**
 * How {@link SpreadsheetWriter} writes a sheet: its name, and whether to start with a header row.
 * Instances are immutable; each method returns a copy.
 *
 * <pre>{@code
 * WriteOptions.defaults().sheet("Employees").header(false)
 * }</pre>
 *
 * @param sheetName name of the sheet to create
 * @param header    write a bold, frozen first row holding each column's {@code @SheetCol} label
 *                  (or field name when it has none)
 */
public record WriteOptions(String sheetName, boolean header) {

    /**
     * @throws NullPointerException if {@code sheetName} is {@code null}
     */
    public WriteOptions {
        Objects.requireNonNull(sheetName, "sheetName");
    }

    /**
     * A sheet named {@code Sheet1}, with a header row.
     */
    public static WriteOptions defaults() {
        return new WriteOptions("Sheet1", true);
    }

    /**
     * Names the sheet {@code name}.
     *
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public WriteOptions sheet(String name) {
        return new WriteOptions(name, header);
    }

    /**
     * Whether to write the header row. Without it, data starts in the first row; read such a sheet
     * back with {@code ReadOptions.defaults().headerRows(0)}.
     */
    public WriteOptions header(boolean header) {
        return new WriteOptions(sheetName, header);
    }
}
