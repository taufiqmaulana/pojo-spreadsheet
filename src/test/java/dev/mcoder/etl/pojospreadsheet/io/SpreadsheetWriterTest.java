package dev.mcoder.etl.pojospreadsheet.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.sample.EmployeeRow;
import dev.mcoder.etl.pojospreadsheet.sample.EmployeeRow.Status;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;

class SpreadsheetWriterTest {

    private static final SpreadsheetWriter writer = new SpreadsheetWriter();
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
    void writtenXlsxReadsBackToEqualPojos() throws IOException {
        List<EmployeeRow> employees = List.of(
                employee("EMP-00001", "Jane Doe", 30, "15000000.5", LocalDate.of(2020, 1, 15), Status.ACTIVE),
                employee("EMP-00002", "John Roe", 45, "9000000", LocalDate.of(2019, 7, 1), Status.INACTIVE));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(employees, EmployeeRow.class, out);
        List<EmployeeRow> read = reader.read(new ByteArrayInputStream(out.toByteArray()), EmployeeRow.class);

        assertEquals(employees.toString(), read.toString());
    }

    @Test
    void writesLabelHeaderAndNativeCellTypes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(List.of(employee("EMP-00001", "Jane Doe", 30, "1.5", LocalDate.of(2020, 1, 15), Status.ACTIVE)),
                EmployeeRow.class, out);

        try (Workbook workbook = open(out)) {
            Sheet sheet = workbook.getSheet("Sheet1");
            assertEquals(List.of("Employee ID", "Full name", "Email", "Age", "Salary", "Join date", "status"), texts(sheet.getRow(0)));
            assertTrue(workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());

            Row row = sheet.getRow(1);
            assertEquals(CellType.NUMERIC, row.getCell(3).getCellType());
            assertEquals(30.0, row.getCell(3).getNumericCellValue());
            assertEquals(1.5, row.getCell(4).getNumericCellValue());
            assertTrue(DateUtil.isCellDateFormatted(row.getCell(5)));
            assertEquals("yyyy-mm-dd", row.getCell(5).getCellStyle().getDataFormatString());
            assertEquals("ACTIVE", row.getCell(6).getStringCellValue());
        }
    }

    @Test
    void writesNullAsEmptyCellAndFormulaTextAsText() throws IOException {
        EmployeeRow employee = new EmployeeRow();
        employee.setFullName("=SUM(A1:A9)");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(List.of(employee), EmployeeRow.class, out);

        try (Workbook workbook = open(out)) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertNull(row.getCell(0));
            assertEquals(CellType.STRING, row.getCell(1).getCellType());
            assertEquals("=SUM(A1:A9)", row.getCell(1).getStringCellValue());
        }
    }

    @Test
    void writesColumnsAtTheirLettersWithoutHeader() throws IOException {
        Sparse sparse = new Sparse();
        sparse.flag = true;
        sparse.when = LocalDateTime.of(2024, 5, 6, 7, 8, 9);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(List.of(sparse), Sparse.class, out, SpreadsheetFormat.XLSX,
                WriteOptions.defaults().sheet("Data").header(false));

        try (Workbook workbook = open(out)) {
            Row row = workbook.getSheet("Data").getRow(0);
            assertEquals(2, row.getPhysicalNumberOfCells());
            assertTrue(row.getCell(1).getBooleanCellValue());
            assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), row.getCell(27).getLocalDateTimeCellValue());
            assertEquals("yyyy-mm-dd hh:mm:ss", row.getCell(27).getCellStyle().getDataFormatString());
        }
    }

    @Test
    void choosesFormatFromFileExtension(@TempDir Path dir) throws IOException {
        List<EmployeeRow> employees = List.of(
                employee("EMP-00001", "Jane Doe", 30, "1", LocalDate.of(2020, 1, 15), Status.ACTIVE));

        Path xls = dir.resolve("employees.xls");
        writer.write(employees, EmployeeRow.class, xls);
        try (InputStream in = Files.newInputStream(xls); Workbook workbook = WorkbookFactory.create(in)) {
            assertTrue(workbook instanceof HSSFWorkbook);
        }
        assertEquals(employees.toString(), reader.read(xls, EmployeeRow.class).toString());

        Path xlsx = dir.resolve("employees.XLSX");
        writer.write(employees, EmployeeRow.class, xlsx);
        try (InputStream in = Files.newInputStream(xlsx); Workbook workbook = WorkbookFactory.create(in)) {
            assertTrue(workbook instanceof XSSFWorkbook);
        }

        assertThrows(IllegalArgumentException.class,
                () -> writer.write(employees, EmployeeRow.class, dir.resolve("employees.csv")));
        assertFalse(Files.exists(dir.resolve("employees.csv")));
    }

    @Test
    void failedWriteLeavesExistingFileUnchanged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("employees.xlsx");
        Files.writeString(file, "original");

        assertThrows(IllegalArgumentException.class,
                () -> writer.write(Arrays.asList(new EmployeeRow(), null), EmployeeRow.class, file));

        assertEquals("original", Files.readString(file));
        try (var files = Files.list(dir)) {
            assertEquals(List.of(file), files.toList()); // temporary file removed
        }
    }

    @Test
    void addsSheetsToAnOpenWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            writer.write(List.of(employee("EMP-00001", "Jane Doe", 30, "1", LocalDate.of(2020, 1, 15), Status.ACTIVE)),
                    EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Active"));
            writer.write(List.of(), EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Inactive"));

            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(0, workbook.getSheet("Inactive").getLastRowNum()); // header only
            assertThrows(IllegalArgumentException.class,
                    () -> writer.write(List.of(), EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Active")));
        }
    }

    @Test
    void rejectsNullItemsAndCollectionsTooLargeForTheFormat() {
        EmployeeRow employee = new EmployeeRow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IllegalArgumentException nullItem = assertThrows(IllegalArgumentException.class,
                () -> writer.write(Arrays.asList(employee, null), EmployeeRow.class, out));
        assertEquals("items contains null at position 1", nullItem.getMessage());

        List<EmployeeRow> tooMany = Collections.nCopies(65_536, employee);
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(tooMany, EmployeeRow.class, out, SpreadsheetFormat.XLS, WriteOptions.defaults()));
    }

    static class Sparse {
        @SheetCol("B") boolean flag;
        @SheetCol("AB") LocalDateTime when;
        @SheetCol("C") BigDecimal unset;
    }

    private static EmployeeRow employee(String id, String name, int age, String salary, LocalDate joined, Status status) {
        EmployeeRow employee = new EmployeeRow();
        employee.setEmployeeId(id);
        employee.setFullName(name);
        employee.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        employee.setAge(age);
        employee.setSalary(new BigDecimal(salary));
        employee.setJoinDate(joined);
        employee.setStatus(status);
        return employee;
    }

    private static Workbook open(ByteArrayOutputStream out) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()));
    }

    private static List<String> texts(Row row) {
        return IntStream.range(0, row.getLastCellNum())
                .mapToObj(c -> row.getCell(c).getStringCellValue())
                .toList();
    }
}
