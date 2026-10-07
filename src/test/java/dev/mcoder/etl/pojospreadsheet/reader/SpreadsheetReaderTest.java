package dev.mcoder.etl.pojospreadsheet.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.sample.EmployeeRow;
import dev.mcoder.etl.pojospreadsheet.sample.EmployeeRow.Status;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import jakarta.validation.constraints.NotNull;

class SpreadsheetReaderTest {

    private static PojoValidator validator;
    private static SpreadsheetReader reader;

    @BeforeAll
    static void setUp() {
        validator = new PojoValidator();
        reader = new SpreadsheetReader(validator);
    }

    @AfterAll
    static void tearDown() {
        validator.close();
    }

    @Test
    void mapsEachColumnToItsAnnotatedField() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            employee(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", 30, 15000000.5, LocalDate.of(2020, 1, 15), "active");
            employee(sheet, 2, "EMP-00002", "John Roe", "john@example.com", 45, 9000000, LocalDate.of(2019, 7, 1), "INACTIVE");
        });

        List<EmployeeRow> rows = reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class);

        assertEquals(2, rows.size());
        EmployeeRow jane = rows.get(0);
        assertEquals("EMP-00001", jane.getEmployeeId());
        assertEquals("Jane Doe", jane.getFullName());
        assertEquals("jane@example.com", jane.getEmail());
        assertEquals(30, jane.getAge());
        assertEquals(new BigDecimal("15000000.5"), jane.getSalary());
        assertEquals(LocalDate.of(2020, 1, 15), jane.getJoinDate());
        assertEquals(Status.ACTIVE, jane.getStatus());
        assertNull(jane.getRemarks());
        assertEquals(Status.INACTIVE, rows.get(1).getStatus());
    }

    @Test
    void skipsBlankRowsAndRespectsHeaderRows() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            sheet.createRow(1).createCell(0).setCellValue("   ");
            employee(sheet, 3, "EMP-00004", "Jane Doe", "jane@example.com", 30, 1, LocalDate.of(2020, 1, 15), "ACTIVE");
        });

        List<EmployeeRow> rows = reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class);

        assertEquals(1, rows.size());
        assertEquals("EMP-00004", rows.get(0).getEmployeeId());
    }

    @Test
    void reportsEveryValidationErrorWithItsCell() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            employee(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", 30, 100, LocalDate.of(2020, 1, 15), "ACTIVE");
            employee(sheet, 2, "EMP-2", "=HYPERLINK(\"x\")", "not-an-email", 17, -5, LocalDate.now().plusDays(1), "ACTIVE");
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class));

        assertEquals(Set.of("A3", "B3", "C3", "D3", "E3", "F3"), cells(e));
        assertTrue(e.getErrors().stream().allMatch(error -> error.row() == 3));
        assertTrue(e.getMessage().startsWith("6 invalid value(s)"));
    }

    @Test
    void reportsConversionErrorsWithoutDuplicateNotNullErrors() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            employee(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", "thirty", 12.345, "2020-13-45", "RETIRED");
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class));

        assertEquals(Set.of("D2", "E2", "F2", "G2"), cells(e));
        RowError age = e.getErrors().stream().filter(error -> "D2".equals(error.cell())).findFirst().orElseThrow();
        assertEquals("age", age.field());
        assertEquals("thirty", age.invalidValue());
        assertEquals("cannot convert 'thirty' to Integer", age.message());
        // 12.345 converts, but has 3 fraction digits
        RowError salary = e.getErrors().stream().filter(error -> "E2".equals(error.cell())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("12.345"), salary.invalidValue());
    }

    @Test
    void reportsLabelOrFallsBackToFieldName() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            employee(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", 17, 1, LocalDate.of(2020, 1, 15), "RETIRED");
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class));

        RowError age = e.getErrors().get(0);
        assertEquals("Age", age.label());
        assertEquals("age", age.field());
        assertEquals("D2 (Age): must be greater than or equal to 18 [value: 17]", age.toString());
        RowError status = e.getErrors().get(1);
        assertEquals("status", status.label());
        assertTrue(e.getMessage().contains("G2 (status): cannot convert 'RETIRED' to Status"));
    }

    @Test
    void readsMissingCellsAsNull() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            header(sheet);
            sheet.createRow(1).createCell(0).setCellValue("EMP-00001");
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class));

        assertEquals(Set.of("B2", "C2", "D2", "E2", "F2", "G2"), cells(e));
    }

    @Test
    void convertsCellTypesToFieldTypes() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            Workbook workbook = sheet.getWorkbook();
            CellStyle dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(12345);                       // numeric -> String
            row.createCell(1).setCellValue("42");                        // text -> int
            row.createCell(2).setCellValue(1);                           // numeric -> boolean
            row.createCell(3).setCellValue(0.1);                         // numeric -> double
            row.createCell(4).setCellValue("2024-05-06T07:08");          // text -> LocalDateTime
            Cell dateCell = row.createCell(5);                           // date -> LocalDateTime
            dateCell.setCellValue(LocalDateTime.of(2024, 5, 6, 7, 8));
            dateCell.setCellStyle(dateTime);
            row.createCell(6).setCellFormula("B1*2");                    // formula -> cached result
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        });

        List<Types> rows = reader.read(new ByteArrayInputStream(xlsx), Types.class, ReadOptions.defaults().headerRows(0));

        Types types = rows.get(0);
        assertEquals("12345", types.text);
        assertEquals(42, types.primitiveInt);
        assertTrue(types.flag);
        assertEquals(0.1, types.decimal);
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8), types.parsed);
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8), types.dateCell);
        assertEquals(84L, types.formula);
    }

    @Test
    void readsXlsFileFromPathAndSelectsSheetByName(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("employees.xls");
        try (Workbook workbook = new HSSFWorkbook()) {
            workbook.createSheet("Other");
            Sheet sheet = workbook.createSheet("Employees");
            header(sheet);
            employee(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", 30, 1, LocalDate.of(2020, 1, 15), "ACTIVE");
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        List<EmployeeRow> rows = reader.read(file, EmployeeRow.class, ReadOptions.defaults().sheet("Employees"));

        assertEquals(1, rows.size());
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(file, EmployeeRow.class, ReadOptions.defaults().sheet("Missing")));
    }

    @Test
    void rejectsInvalidMappings() throws IOException {
        byte[] xlsx = xlsx(sheet -> header(sheet));

        assertThrows(IllegalArgumentException.class, () -> reader.read(new ByteArrayInputStream(xlsx), String.class));
        assertThrows(IllegalArgumentException.class, () -> reader.read(new ByteArrayInputStream(xlsx), BadColumn.class));
        assertThrows(IllegalArgumentException.class, () -> reader.read(new ByteArrayInputStream(xlsx), UnsupportedType.class));
    }

    @Test
    void returnsEmptyListForSheetWithOnlyHeader() throws IOException {
        byte[] xlsx = xlsx(sheet -> header(sheet));

        assertFalse(reader.read(new ByteArrayInputStream(xlsx), EmployeeRow.class).iterator().hasNext());
    }

    static class Types {
        @SheetCol("A") String text;
        @SheetCol("B") int primitiveInt;
        @SheetCol("C") boolean flag;
        @SheetCol("D") double decimal;
        @SheetCol("E") LocalDateTime parsed;
        @SheetCol("F") LocalDateTime dateCell;
        @SheetCol("G") @NotNull Long formula;
    }

    static class BadColumn {
        @SheetCol("A1") String value;
    }

    static class UnsupportedType {
        @SheetCol("A") List<String> values;
    }

    private static byte[] xlsx(Consumer<Sheet> content) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            content.accept(workbook.createSheet("Sheet1"));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void header(Sheet sheet) {
        employee(sheet, 0, "Employee ID", "Full name", "Email", "Age", "Salary", "Join date", "Status");
    }

    private static void employee(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            Object value = values[c];
            if (value instanceof Number number) {
                row.createCell(c).setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                row.createCell(c).setCellValue(date);
            } else {
                row.createCell(c).setCellValue((String) value);
            }
        }
    }

    private static Set<String> cells(SpreadsheetValidationException e) {
        return e.getErrors().stream().map(RowError::cell).collect(Collectors.toSet());
    }
}
