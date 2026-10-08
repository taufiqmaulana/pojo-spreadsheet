package dev.mcoder.etl.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetReader;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetWriter;
import dev.mcoder.etl.sample.Employee.Status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleSpringbootApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository repository;

    @Autowired
    SpreadsheetReader reader;

    @Autowired
    SpreadsheetWriter writer;

    @BeforeEach
    void resetTable() {
        repository.deleteAllInBatch();
        repository.saveAll(List.of(
                employee("EMP-00001", "Alice Johnson"),
                employee("EMP-00002", "Bob Smith")));
    }

    @Test
    void exportWritesEveryEmployee() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/employees/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();

        List<Employee> rows = reader.read(new ByteArrayInputStream(xlsx), Employee.class);
        assertThat(rows).extracting(Employee::getEmployeeId).containsExactly("EMP-00001", "EMP-00002");
        assertThat(rows.get(0).getSalary()).isEqualByComparingTo("75000");
        assertThat(rows.get(0).getJoinDate()).isEqualTo(LocalDate.of(2020, 1, 2));
    }

    @Test
    void importAddsRows() throws Exception {
        byte[] xlsx = toXlsx(List.of(employee("EMP-00010", "David Lee"), employee("EMP-00011", "Emma Brown")));

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.total").value(4));

        assertThat(repository.findAllByOrderByEmployeeIdAsc()).extracting(Employee::getFullName)
                .containsExactly("Alice Johnson", "Bob Smith", "David Lee", "Emma Brown");
    }

    @Test
    void importWithReplaceDeletesExistingRows() throws Exception {
        byte[] xlsx = toXlsx(List.of(employee("EMP-00001", "Alice Johnson-Smith")));

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)).param("replace", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        assertThat(repository.findAll()).extracting(Employee::getFullName).containsExactly("Alice Johnson-Smith");
    }

    @Test
    void importReportsInvalidCellsAndSavesNothing() throws Exception {
        byte[] xlsx;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            textRow(sheet, 0, "Employee ID", "Full name", "Email", "Age", "Salary", "Join date", "Status");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("EMP-2");
            row.createCell(1).setCellValue("=cmd|' /C calc'!A0");
            row.createCell(2).setCellValue("jane(at)example");
            row.createCell(3).setCellValue("forty");
            row.createCell(6).setCellValue("RETIRED");
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)).param("replace", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[0].cell").value("A2"))
                .andExpect(jsonPath("$.errors[3].message").value("cannot convert 'forty' to Integer"));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void importRejectsWrongHeaderLabels() throws Exception {
        byte[] xlsx;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            // Email and Full name swapped
            textRow(sheet, 0, "Employee ID", "Email", "Full name", "Age", "Salary", "Join date", "Status");
            textRow(sheet, 1, "EMP-00010", "david@example.com", "David Lee");
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)).param("replace", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].cell").value("B1"))
                .andExpect(jsonPath("$.errors[0].message").value("header must be 'Full name'"));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void importRejectsExistingEmployeeIds() throws Exception {
        byte[] xlsx = toXlsx(List.of(employee("EMP-00002", "Bob Smith")));

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate employees"));
    }

    @Test
    void failedReplaceKeepsExistingRows() throws Exception {
        byte[] xlsx = toXlsx(List.of(employee("EMP-00010", "David Lee"), employee("EMP-00010", "David Lee")));

        mvc.perform(multipart("/api/employees/import").file(upload(xlsx)).param("replace", "true"))
                .andExpect(status().isConflict());

        assertThat(repository.findAllByOrderByEmployeeIdAsc()).extracting(Employee::getEmployeeId)
                .containsExactly("EMP-00001", "EMP-00002");
    }

    @Test
    void importRejectsFilesThatAreNotSpreadsheets() throws Exception {
        mvc.perform(multipart("/api/employees/import").file(upload("hello".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unreadable file"));
    }

    @Test
    void templateHasOnlyTheHeaderRow() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/employees/template"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Employees");
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Employee ID");
        }
    }

    private static Employee employee(String employeeId, String fullName) {
        return new Employee(employeeId, fullName, employeeId.toLowerCase() + "@example.com", 30,
                new BigDecimal("75000"), LocalDate.of(2020, 1, 2), Status.ACTIVE);
    }

    private byte[] toXlsx(List<Employee> employees) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.write(employees, Employee.class, out);
            return out.toByteArray();
        }
    }

    private static void textRow(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c]);
        }
    }

    private static MockMultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "employees.xlsx", null, content);
    }
}
