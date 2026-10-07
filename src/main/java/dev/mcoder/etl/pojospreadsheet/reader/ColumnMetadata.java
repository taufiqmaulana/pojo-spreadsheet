package dev.mcoder.etl.pojospreadsheet.reader;

/**
 * One {@code @SheetCol} field of a POJO.
 *
 * @param column    column letter(s), upper case, for example {@code "A"} or {@code "AB"}
 * @param fieldName name of the POJO field
 * @param label     the {@code @SheetCol} label, or the field name when it has none
 * @see SheetMetadata#columns(Class)
 */
public record ColumnMetadata(String column, String fieldName, String label) {
}
