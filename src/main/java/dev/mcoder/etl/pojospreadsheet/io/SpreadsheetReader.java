package dev.mcoder.etl.pojospreadsheet.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.io.CellValueConverter.ConversionException;
import dev.mcoder.etl.pojospreadsheet.io.SheetMapping.Column;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path.Node;

/**
 * Reads the rows of a spreadsheet (.xlsx or .xls) into POJOs whose fields are annotated with
 * {@link SheetCol}, then validates each POJO against its Jakarta Bean Validation annotations.
 *
 * <pre>{@code
 * SpreadsheetReader reader = new SpreadsheetReader(new PojoValidator());
 * try {
 *     List<EmployeeRow> rows = reader.read(Path.of("employees.xlsx"), EmployeeRow.class);
 * } catch (SpreadsheetValidationException e) {
 *     e.getErrors().forEach(System.out::println);   // D4 (Age): must be greater than or equal to 18 [value: 17]
 * }
 * }</pre>
 *
 * <p>Reading proceeds as follows:
 * <ol>
 *   <li>The sheet is selected by {@link ReadOptions} (the first sheet by default).</li>
 *   <li>With {@link ReadOptions#validateHeader()}, the last header row is checked against the
 *       column labels; on a mismatch, no data rows are read.</li>
 *   <li>The header rows are skipped, as are data rows whose mapped cells are all blank.</li>
 *   <li>Each remaining row is converted into a new POJO, one cell per {@code @SheetCol} field,
 *       then validated.</li>
 * </ol>
 *
 * <p>Reading is all-or-nothing: if any header label, cell or POJO is invalid, a
 * {@link SpreadsheetValidationException} listing every error in the sheet is thrown, and no rows
 * are returned.
 *
 * <p>Thread-safe; reuse one instance.
 *
 * @see SpreadsheetWriter
 */
public final class SpreadsheetReader {

    private final PojoValidator validator;

    /**
     * @param validator validates each row's POJO; the reader does not close it
     */
    public SpreadsheetReader(PojoValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Reads {@code file} with {@link ReadOptions#defaults()}: the first sheet, skipping one header row.
     *
     * @see #read(Path, Class, ReadOptions)
     */
    public <T> List<T> read(Path file, Class<T> type) {
        return read(file, type, ReadOptions.defaults());
    }

    /**
     * Reads {@code file}, an .xlsx or .xls workbook; the format is detected from its content,
     * not its extension. The file is opened read-only.
     *
     * @param file    the workbook to read
     * @param type    the POJO class; it needs a no-argument constructor and at least one
     *                {@code @SheetCol} field
     * @param options the sheet to read, the number of header rows, and whether to validate the header
     * @return one POJO per non-blank data row, in sheet order
     * @throws SpreadsheetValidationException if any header label, cell or POJO is invalid
     * @throws IllegalArgumentException       if {@code type} cannot be mapped, the sheet does not
     *                                        exist, or the file is a damaged .xlsx
     * @throws UncheckedIOException           if the file cannot be read or is not a spreadsheet
     */
    public <T> List<T> read(Path file, Class<T> type, ReadOptions options) {
        try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            return read(workbook, type, options);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /**
     * Reads from {@code in} with {@link ReadOptions#defaults()}: the first sheet, skipping one
     * header row. {@code in} is not closed.
     *
     * @see #read(InputStream, Class, ReadOptions)
     */
    public <T> List<T> read(InputStream in, Class<T> type) {
        return read(in, type, ReadOptions.defaults());
    }

    /**
     * Reads an .xlsx or .xls workbook from {@code in}, such as an uploaded file. The whole
     * workbook is loaded into memory. {@code in} is not closed.
     *
     * @param in      the workbook content
     * @param type    the POJO class; it needs a no-argument constructor and at least one
     *                {@code @SheetCol} field
     * @param options the sheet to read, the number of header rows, and whether to validate the header
     * @return one POJO per non-blank data row, in sheet order
     * @throws SpreadsheetValidationException if any header label, cell or POJO is invalid
     * @throws IllegalArgumentException       if {@code type} cannot be mapped, the sheet does not
     *                                        exist, or the content is a damaged .xlsx
     * @throws UncheckedIOException           if {@code in} cannot be read or is not a spreadsheet
     */
    public <T> List<T> read(InputStream in, Class<T> type, ReadOptions options) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            return read(workbook, type, options);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read spreadsheet", e);
        }
    }

    /**
     * Reads from an already open {@code workbook}, which is not closed. Use this to read several
     * sheets of one file without opening it again.
     *
     * @param workbook the workbook to read
     * @param type     the POJO class; it needs a no-argument constructor and at least one
     *                 {@code @SheetCol} field
     * @param options  the sheet to read, the number of header rows, and whether to validate the header
     * @return one POJO per non-blank data row, in sheet order
     * @throws SpreadsheetValidationException if any header label, cell or POJO is invalid
     * @throws IllegalArgumentException       if {@code type} cannot be mapped or the sheet does not exist
     */
    public <T> List<T> read(Workbook workbook, Class<T> type, ReadOptions options) {
        Objects.requireNonNull(workbook, "workbook");
        Objects.requireNonNull(options, "options");
        SheetMapping<T> mapping = SheetMapping.of(type);
        Sheet sheet = sheet(workbook, options);
        CellValueConverter converter = new CellValueConverter();
        if (options.validateHeader()) {
            validateHeader(sheet, options.headerRows() - 1, mapping, converter);
        }

        List<T> result = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (int rowIndex = options.headerRows(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlank(row, mapping)) {
                continue;
            }
            int rowNumber = rowIndex + 1;
            T pojo = mapping.newInstance();
            List<RowError> rowErrors = new ArrayList<>();

            Set<String> unconvertedFields = new HashSet<>();
            for (Column column : mapping.columns()) {
                Cell cell = row.getCell(column.index(), MissingCellPolicy.RETURN_BLANK_AS_NULL);
                try {
                    column.set(pojo, converter.convert(cell, column.type()));
                } catch (ConversionException e) {
                    unconvertedFields.add(column.fieldName());
                    rowErrors.add(new RowError(rowNumber, column.cellRef(rowNumber), column.fieldName(),
                            column.label(), converter.text(cell), e.getMessage()));
                }
            }

            for (ConstraintViolation<T> violation : validator.validate(pojo)) {
                String fieldName = rootFieldName(violation);
                if (unconvertedFields.contains(fieldName)) {
                    continue; // already reported; the field is only null because conversion failed
                }
                Column column = mapping.column(fieldName);
                rowErrors.add(new RowError(rowNumber,
                        column == null ? null : column.cellRef(rowNumber),
                        fieldName,
                        column == null ? fieldName : column.label(),
                        violation.getInvalidValue(), violation.getMessage()));
            }

            if (rowErrors.isEmpty()) {
                result.add(pojo);
            } else {
                // Violations arrive in no particular order; list them left to right, class-level ones last
                rowErrors.sort(Comparator.comparingInt(error -> columnIndex(mapping, error.field())));
                errors.addAll(rowErrors);
            }
        }

        if (!errors.isEmpty()) {
            throw new SpreadsheetValidationException(errors);
        }
        return result;
    }

    private static Sheet sheet(Workbook workbook, ReadOptions options) {
        if (options.sheetName() != null) {
            Sheet sheet = workbook.getSheet(options.sheetName());
            if (sheet == null) {
                throw new IllegalArgumentException("No sheet named '" + options.sheetName() + "'");
            }
            return sheet;
        }
        if (options.sheetIndex() >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("No sheet at index " + options.sheetIndex()
                    + "; the workbook has " + workbook.getNumberOfSheets());
        }
        return workbook.getSheetAt(options.sheetIndex());
    }

    /**
     * Checks every mapped column's header cell against its label. Data rows are not read when the
     * header is wrong, since a shifted or renamed column would otherwise surface as many data errors.
     */
    private static void validateHeader(Sheet sheet, int rowIndex, SheetMapping<?> mapping,
            CellValueConverter converter) {
        Row row = sheet.getRow(rowIndex);
        int rowNumber = rowIndex + 1;
        List<RowError> errors = new ArrayList<>();
        for (Column column : mapping.columns()) {
            Cell cell = row == null ? null : row.getCell(column.index(), MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String text = converter.text(cell);
            if (text == null || !text.trim().equalsIgnoreCase(column.label())) {
                errors.add(new RowError(rowNumber, column.cellRef(rowNumber), column.fieldName(), column.label(),
                        text, "header must be '" + column.label() + "'"));
            }
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(error -> columnIndex(mapping, error.field())));
            throw new SpreadsheetValidationException(errors);
        }
    }

    private static boolean isBlank(Row row, SheetMapping<?> mapping) {
        if (row == null) {
            return true;
        }
        for (Column column : mapping.columns()) {
            if (!CellValueConverter.isBlank(row.getCell(column.index()))) {
                return false;
            }
        }
        return true;
    }

    private static int columnIndex(SheetMapping<?> mapping, String fieldName) {
        Column column = mapping.column(fieldName);
        return column == null ? Integer.MAX_VALUE : column.index();
    }

    private static String rootFieldName(ConstraintViolation<?> violation) {
        Iterator<Node> nodes = violation.getPropertyPath().iterator();
        return nodes.hasNext() ? nodes.next().getName() : null;
    }
}
