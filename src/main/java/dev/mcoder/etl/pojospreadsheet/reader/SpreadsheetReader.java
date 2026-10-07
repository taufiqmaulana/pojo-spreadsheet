package dev.mcoder.etl.pojospreadsheet.reader;

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
import dev.mcoder.etl.pojospreadsheet.reader.CellValueConverter.ConversionException;
import dev.mcoder.etl.pojospreadsheet.reader.SheetMapping.Column;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path.Node;

/**
 * Reads the rows of a spreadsheet (.xlsx or .xls) into POJOs whose fields are annotated with
 * {@link SheetCol}, then validates each POJO against its Jakarta Bean Validation annotations.
 *
 * <p>Rows whose mapped cells are all blank are skipped. If any cell cannot be converted or any
 * POJO is invalid, a {@link SpreadsheetValidationException} listing every error is thrown, so
 * the caller gets either all rows or none.
 *
 * <p>Thread-safe; reuse one instance.
 */
public final class SpreadsheetReader {

    private final PojoValidator validator;

    public SpreadsheetReader(PojoValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public <T> List<T> read(Path file, Class<T> type) {
        return read(file, type, ReadOptions.defaults());
    }

    public <T> List<T> read(Path file, Class<T> type, ReadOptions options) {
        try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            return read(workbook, type, options);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /**
     * Reads from {@code in}, which is not closed.
     */
    public <T> List<T> read(InputStream in, Class<T> type) {
        return read(in, type, ReadOptions.defaults());
    }

    /**
     * Reads from {@code in}, which is not closed.
     */
    public <T> List<T> read(InputStream in, Class<T> type, ReadOptions options) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            return read(workbook, type, options);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read spreadsheet", e);
        }
    }

    /**
     * Reads from an already open {@code workbook}, which is not closed.
     *
     * @throws SpreadsheetValidationException if any cell cannot be converted or any POJO is invalid
     */
    public <T> List<T> read(Workbook workbook, Class<T> type, ReadOptions options) {
        Objects.requireNonNull(workbook, "workbook");
        Objects.requireNonNull(options, "options");
        SheetMapping<T> mapping = SheetMapping.of(type);
        Sheet sheet = sheet(workbook, options);
        CellValueConverter converter = new CellValueConverter();

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
