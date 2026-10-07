package dev.mcoder.etl.pojospreadsheet.io;

import java.nio.file.Path;
import java.util.Locale;

/**
 * File format written by {@link SpreadsheetWriter}.
 */
public enum SpreadsheetFormat {

    /** Excel 2007+ ({@code .xlsx}): up to 1,048,576 rows. */
    XLSX,

    /** Excel 97-2003 ({@code .xls}): up to 65,536 rows. */
    XLS;

    /**
     * The format matching the extension of {@code file}.
     *
     * @throws IllegalArgumentException if the extension is neither {@code .xlsx} nor {@code .xls}
     */
    public static SpreadsheetFormat of(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) {
            return XLSX;
        }
        if (name.endsWith(".xls")) {
            return XLS;
        }
        throw new IllegalArgumentException("Cannot tell the format of " + file + "; use a .xlsx or .xls extension");
    }
}
