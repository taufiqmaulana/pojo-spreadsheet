package dev.mcoder.etl.pojospreadsheet.io;

/**
 * One invalid value found while reading a sheet.
 *
 * @param row          one-based row number, as shown in the spreadsheet application
 * @param cell         cell reference such as {@code "B5"}, or {@code null} when the field is not mapped to a column
 * @param field        name of the POJO field, or {@code null} for a class-level constraint
 * @param label        the column's {@code @SheetCol} label, or the field name when it has none;
 *                     {@code null} for a class-level constraint
 * @param invalidValue the offending value (the cell text when it could not be converted)
 * @param message      why the value is invalid
 */
public record RowError(int row, String cell, String field, String label, Object invalidValue, String message) {

    @Override
    public String toString() {
        String location = cell != null ? cell : "row " + row;
        if (label != null) {
            location += " (" + label + ")";
        }
        return location + ": " + message + " [value: " + invalidValue + "]";
    }
}
