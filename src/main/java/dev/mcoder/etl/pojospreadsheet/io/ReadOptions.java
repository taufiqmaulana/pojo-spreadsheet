package dev.mcoder.etl.pojospreadsheet.io;

/**
 * How {@link SpreadsheetReader} reads a workbook: which sheet, how many header rows to skip, and
 * whether to check the header labels. Instances are immutable; each method returns a copy.
 *
 * <pre>{@code
 * ReadOptions.defaults().sheet("Employees").headerRows(2).validateHeader(true)
 * }</pre>
 *
 * @param sheetName      name of the sheet to read, or {@code null} to select it by {@code sheetIndex}
 * @param sheetIndex     zero-based index of the sheet to read; ignored when {@code sheetName} is set
 * @param headerRows     number of leading rows to skip before the data rows
 * @param validateHeader check that the last header row holds each column's {@code @SheetCol}
 *                       label before reading any data; requires {@code headerRows >= 1}
 */
public record ReadOptions(String sheetName, int sheetIndex, int headerRows, boolean validateHeader) {

    /**
     * @throws IllegalArgumentException if {@code sheetIndex} or {@code headerRows} is negative, or
     *                                  {@code validateHeader} is set with no header row
     */
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

    /**
     * Options that do not validate the header.
     */
    public ReadOptions(String sheetName, int sheetIndex, int headerRows) {
        this(sheetName, sheetIndex, headerRows, false);
    }

    /**
     * The first sheet, with one header row that is not validated.
     */
    public static ReadOptions defaults() {
        return new ReadOptions(null, 0, 1, false);
    }

    /**
     * Reads the sheet named {@code name}. {@link SpreadsheetReader} throws
     * {@link IllegalArgumentException} if the workbook has no such sheet.
     */
    public ReadOptions sheet(String name) {
        return new ReadOptions(name, sheetIndex, headerRows, validateHeader);
    }

    /**
     * Reads the sheet at the zero-based {@code index}, replacing any sheet name set earlier.
     *
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public ReadOptions sheet(int index) {
        return new ReadOptions(null, index, headerRows, validateHeader);
    }

    /**
     * Skips {@code rows} leading rows, such as a title and a row of column labels. Use 0 when the
     * data starts in the first row.
     *
     * @throws IllegalArgumentException if {@code rows} is negative, or 0 while the header is validated
     */
    public ReadOptions headerRows(int rows) {
        return new ReadOptions(sheetName, sheetIndex, rows, validateHeader);
    }

    /**
     * Whether to check the header labels. Labels are compared ignoring case and surrounding
     * whitespace; a mismatch throws {@link SpreadsheetValidationException} before any data is read.
     *
     * @throws IllegalArgumentException if {@code validate} is true and {@code headerRows} is 0
     */
    public ReadOptions validateHeader(boolean validate) {
        return new ReadOptions(sheetName, sheetIndex, headerRows, validate);
    }
}
