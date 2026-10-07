package dev.mcoder.etl.pojospreadsheet.reader;

/**
 * Where to read from: the sheet (by name, or by zero-based index when no name is set) and how
 * many leading header rows to skip.
 *
 * <pre>{@code
 * ReadOptions.defaults().sheet("Employees").headerRows(2)
 * }</pre>
 */
public record ReadOptions(String sheetName, int sheetIndex, int headerRows) {

    public ReadOptions {
        if (sheetIndex < 0) {
            throw new IllegalArgumentException("sheetIndex must be >= 0");
        }
        if (headerRows < 0) {
            throw new IllegalArgumentException("headerRows must be >= 0");
        }
    }

    /**
     * The first sheet, with one header row.
     */
    public static ReadOptions defaults() {
        return new ReadOptions(null, 0, 1);
    }

    public ReadOptions sheet(String name) {
        return new ReadOptions(name, sheetIndex, headerRows);
    }

    public ReadOptions sheet(int index) {
        return new ReadOptions(null, index, headerRows);
    }

    public ReadOptions headerRows(int rows) {
        return new ReadOptions(sheetName, sheetIndex, rows);
    }
}
