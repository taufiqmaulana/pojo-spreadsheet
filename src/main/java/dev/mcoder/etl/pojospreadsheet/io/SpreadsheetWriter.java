package dev.mcoder.etl.pojospreadsheet.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.io.SheetMapping.Column;

/**
 * Writes a collection of POJOs to a spreadsheet, one row per POJO, each {@link SheetCol} field in
 * its column. The optional header row holds each column's label. Files written here can be read
 * back with {@link SpreadsheetReader}.
 *
 * <p>Values are written as native cells: numbers as numbers, booleans as booleans, dates as dates
 * formatted {@code yyyy-mm-dd} (or {@code yyyy-mm-dd hh:mm:ss} with a time), enums by constant
 * name and {@code null} as an empty cell. Spreadsheet numbers are doubles, so a {@code long},
 * {@code BigDecimal} or {@code BigInteger} with more than 15 significant digits loses precision.
 * Text is always written as text, never as a formula.
 *
 * <pre>{@code
 * SpreadsheetWriter writer = new SpreadsheetWriter();
 * writer.write(employees, EmployeeRow.class, Path.of("employees.xlsx"));
 * }</pre>
 *
 * <p>The writer does not run Jakarta validation; validate with
 * {@link dev.mcoder.etl.pojospreadsheet.validation.PojoValidator} first if needed.
 *
 * <p>When writing to a {@code Path} or {@code OutputStream}, .xlsx output is streamed, keeping only
 * 100 rows in memory at a time. Thread-safe; reuse one instance.
 *
 * @see SpreadsheetReader
 */
public final class SpreadsheetWriter {

    // Rows kept in memory while streaming an .xlsx; older rows are flushed to a temporary file
    private static final int STREAMING_WINDOW = 100;

    /**
     * Writes to {@code file} with {@link WriteOptions#defaults()}: a sheet named {@code Sheet1}
     * with a header row.
     *
     * @see #write(Collection, Class, Path, WriteOptions)
     */
    public <T> void write(Collection<? extends T> items, Class<T> type, Path file) {
        write(items, type, file, WriteOptions.defaults());
    }

    /**
     * Writes to {@code file}, replacing it if it exists. The format follows the file extension,
     * {@code .xlsx} or {@code .xls}. The workbook is written to a temporary file in the same
     * directory first, so if writing fails an existing file is left unchanged.
     *
     * @param items   the POJOs to write, one row each, in iteration order; must not contain {@code null}
     * @param type    the POJO class whose {@code @SheetCol} fields define the columns
     * @param file    the file to create or replace; its directory must exist
     * @param options the sheet name and whether to write a header row
     * @throws IllegalArgumentException if the extension is neither {@code .xlsx} nor {@code .xls},
     *                                  {@code type} cannot be mapped, {@code items} contains
     *                                  {@code null}, or the items do not fit in one sheet
     * @throws UncheckedIOException     if the file cannot be written
     */
    public <T> void write(Collection<? extends T> items, Class<T> type, Path file, WriteOptions options) {
        SpreadsheetFormat format = SpreadsheetFormat.of(file);
        Path target = file.toAbsolutePath();
        Path temp = null;
        try {
            // Written next to the target so the final move stays on the same file system
            temp = Files.createTempFile(target.getParent(), ".pojo-spreadsheet-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                write(items, type, out, format, options);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort; the original exception, if any, matters more
                }
            }
        }
    }

    /**
     * Writes an .xlsx workbook to {@code out} with {@link WriteOptions#defaults()}: a sheet named
     * {@code Sheet1} with a header row. {@code out} is not closed.
     *
     * @see #write(Collection, Class, OutputStream, SpreadsheetFormat, WriteOptions)
     */
    public <T> void write(Collection<? extends T> items, Class<T> type, OutputStream out) {
        write(items, type, out, SpreadsheetFormat.XLSX, WriteOptions.defaults());
    }

    /**
     * Writes a workbook in {@code format} to {@code out}, such as an HTTP response.
     * {@code out} is not closed.
     *
     * @param items   the POJOs to write, one row each, in iteration order; must not contain {@code null}
     * @param type    the POJO class whose {@code @SheetCol} fields define the columns
     * @param out     where to write the workbook
     * @param format  {@link SpreadsheetFormat#XLSX} or {@link SpreadsheetFormat#XLS}
     * @param options the sheet name and whether to write a header row
     * @throws IllegalArgumentException if {@code type} cannot be mapped, {@code items} contains
     *                                  {@code null}, or the items do not fit in one sheet
     * @throws UncheckedIOException     if writing to {@code out} fails
     */
    public <T> void write(Collection<? extends T> items, Class<T> type, OutputStream out,
            SpreadsheetFormat format, WriteOptions options) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(format, "format");
        Workbook workbook = format == SpreadsheetFormat.XLSX ? new SXSSFWorkbook(STREAMING_WINDOW) : new HSSFWorkbook();
        try (workbook) {
            write(items, type, workbook, options);
            workbook.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write spreadsheet", e);
        } finally {
            if (workbook instanceof SXSSFWorkbook streaming) {
                streaming.dispose(); // delete the temporary files
            }
        }
    }

    /**
     * Adds a sheet to {@code workbook} and writes the items to it. The workbook is neither saved
     * nor closed, so further sheets can be added. The returned sheet can be customized further,
     * for example with column widths, before saving.
     *
     * <pre>{@code
     * try (Workbook workbook = new XSSFWorkbook()) {
     *     writer.write(active, EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Active"));
     *     writer.write(inactive, EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Inactive"));
     *     workbook.write(out);
     * }
     * }</pre>
     *
     * @param items    the POJOs to write, one row each, in iteration order; must not contain {@code null}
     * @param type     the POJO class whose {@code @SheetCol} fields define the columns
     * @param workbook the workbook to add the sheet to; its type decides the format and whether
     *                 rows are streamed
     * @param options  the sheet name and whether to write a header row
     * @return the new sheet
     * @throws IllegalArgumentException if {@code type} cannot be mapped, the workbook already has a
     *                                  sheet with that name, {@code items} contains {@code null},
     *                                  or the items do not fit in a sheet of the workbook's format
     */
    public <T> Sheet write(Collection<? extends T> items, Class<T> type, Workbook workbook, WriteOptions options) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(workbook, "workbook");
        Objects.requireNonNull(options, "options");
        SheetMapping<T> mapping = SheetMapping.of(type);

        int firstDataRow = options.header() ? 1 : 0;
        int maxRows = workbook.getSpreadsheetVersion().getMaxRows();
        if ((long) firstDataRow + items.size() > maxRows) {
            throw new IllegalArgumentException(items.size() + " items do not fit in a sheet of "
                    + maxRows + " rows; use .xlsx or split the collection");
        }

        Sheet sheet = workbook.createSheet(options.sheetName());
        Styles styles = new Styles(workbook);
        if (options.header()) {
            writeHeader(sheet, mapping, styles);
        }

        int rowIndex = firstDataRow;
        for (T item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items contains null at position " + (rowIndex - firstDataRow));
            }
            Row row = sheet.createRow(rowIndex++);
            for (Column column : mapping.columns()) {
                Object value = column.get(item);
                if (value != null) {
                    setCellValue(row.createCell(column.index()), value, styles);
                }
            }
        }
        return sheet;
    }

    private static void writeHeader(Sheet sheet, SheetMapping<?> mapping, Styles styles) {
        Row header = sheet.createRow(0);
        for (Column column : mapping.columns()) {
            Cell cell = header.createCell(column.index());
            cell.setCellValue(column.label());
            cell.setCellStyle(styles.header);
        }
        sheet.createFreezePane(0, 1);
    }

    private static void setCellValue(Cell cell, Object value, Styles styles) {
        if (value instanceof String text) {
            cell.setCellValue(text);
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date);
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(styles.dateTime);
        } else if (value instanceof Date date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.dateTime);
        } else if (value instanceof Enum<?> constant) {
            cell.setCellValue(constant.name());
        } else {
            // Unreachable: SheetMapping only accepts field types the converter supports
            throw new IllegalStateException("Unsupported value type " + value.getClass().getName());
        }
    }

    /**
     * Cell styles shared by every cell of one write; a workbook allows only a limited number of styles.
     */
    private static final class Styles {

        final CellStyle header;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);
            date = dateStyle(workbook, "yyyy-mm-dd");
            dateTime = dateStyle(workbook, "yyyy-mm-dd hh:mm:ss");
        }

        private static CellStyle dateStyle(Workbook workbook, String pattern) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(pattern));
            return style;
        }
    }
}
