package dev.mcoder.etl.pojospreadsheet.io;

/**
 * Where to read from: the sheet (by name, or by zero-based index when no name is set), how
 * many leading header rows to skip, and whether to check the header labels.
 *
 * <pre>{@code
 * ReadOptions.defaults().sheet("Employees").headerRows(2).validateHeader(true)
 * }</pre>
 *
 * @param validateHeader check that the last header row holds each column's {@code @SheetCol}
 *                       label before reading any data; requires {@code headerRows >= 1}
 */
public record ReadOptions(String sheetName, int sheetIndex, int headerRows, boolean validateHeader) {

    public ReadOptions {
        if (sheetIndex < 0) {
            throw new IllegalArgumentException("sheetIndex must be >= 0");
        }
        if (headerRows < 0) {
            throw new IllegalArgumentException("headerRows must be >= 0");
        }
        if (validateHeader && headerRows == 0) {
            throw new IllegalArgumentException("validateHeader requires headerRows >= 1");
        }
    }

    public ReadOptions(String sheetName, int sheetIndex, int headerRows) {
        this(sheetName, sheetIndex, headerRows, false);
    }

    /**
     * The first sheet, with one header row that is not validated.
     */
    public static ReadOptions defaults() {
        return new ReadOptions(null, 0, 1, false);
    }

    public ReadOptions sheet(String name) {
        return new ReadOptions(name, sheetIndex, headerRows, validateHeader);
    }

    public ReadOptions sheet(int index) {
        return new ReadOptions(null, index, headerRows, validateHeader);
    }

    public ReadOptions headerRows(int rows) {
        return new ReadOptions(sheetName, sheetIndex, rows, validateHeader);
    }

    /**
     * Whether to check the header labels. Labels are compared ignoring case and surrounding
     * whitespace; a mismatch throws {@link SpreadsheetValidationException} before any data is read.
     */
    public ReadOptions validateHeader(boolean validate) {
        return new ReadOptions(sheetName, sheetIndex, headerRows, validate);
    }
}
