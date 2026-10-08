package dev.mcoder.etl.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.mcoder.etl.pojospreadsheet.io.ReadOptions;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetFormat;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetReader;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetValidationException;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetWriter;
import dev.mcoder.etl.pojospreadsheet.io.WriteOptions;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    // Reject files whose header labels don't match, e.g. columns in the wrong order
    private static final ReadOptions READ_OPTIONS = ReadOptions.defaults().validateHeader(true);
    private static final WriteOptions WRITE_OPTIONS = WriteOptions.defaults().sheet("Employees");

    private final SpreadsheetReader reader;
    private final SpreadsheetWriter writer;
    private final EmployeeRepository repository;

    public EmployeeController(SpreadsheetReader reader, SpreadsheetWriter writer, EmployeeRepository repository) {
        this.reader = reader;
        this.writer = writer;
        this.repository = repository;
    }

    @GetMapping
    public List<Employee> list() {
        return repository.findAllByOrderByEmployeeIdAsc();
    }

    /**
     * Imports an .xlsx (or .xls) file into the employee table, all rows or none.
     *
     * <pre>curl -F file=@employees.xlsx "http://localhost:8080/api/employees/import?replace=true"</pre>
     *
     * @param replace delete all existing employees first; otherwise the rows are added
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> importXlsx(@RequestParam MultipartFile file,
            @RequestParam(defaultValue = "false") boolean replace) throws IOException {
        List<Employee> employees;
        try (InputStream in = file.getInputStream()) {
            employees = reader.read(in, Employee.class, READ_OPTIONS);
        } catch (UncheckedIOException | IllegalArgumentException e) {
            // POI rejects files that are not spreadsheets with one of these
            return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Unreadable file",
                    "The file is not a readable .xlsx or .xls spreadsheet"));
        }
        if (replace) {
            repository.deleteAllInBatch();
        }
        repository.saveAll(employees);
        return ResponseEntity.ok(new ImportResult(employees.size(), replace, repository.count()));
    }

    /**
     * Downloads the employee table as an .xlsx file.
     *
     * <pre>curl -OJ http://localhost:8080/api/employees/export</pre>
     */
    @GetMapping("/export")
    public void exportXlsx(HttpServletResponse response) throws IOException {
        download(response, "employees.xlsx", repository.findAllByOrderByEmployeeIdAsc());
    }

    /**
     * Downloads an .xlsx file holding only the header row, ready to fill in and import.
     */
    @GetMapping("/template")
    public void templateXlsx(HttpServletResponse response) throws IOException {
        download(response, "employees-template.xlsx", List.of());
    }

    private void download(HttpServletResponse response, String filename, List<Employee> employees) throws IOException {
        response.setContentType(XLSX);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        writer.write(employees, Employee.class, response.getOutputStream(), SpreadsheetFormat.XLSX, WRITE_OPTIONS);
    }

    @ExceptionHandler
    ProblemDetail invalidRows(SpreadsheetValidationException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid spreadsheet rows",
                e.getErrors().size() + " invalid value(s) in the spreadsheet; nothing was imported");
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    // The employee_id unique constraint; the import transaction is rolled back
    @ExceptionHandler
    ProblemDetail duplicateEmployee(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Duplicate employees",
                "An employee ID appears twice in the file or already exists; nothing was imported");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    public record ImportResult(int imported, boolean replaced, long total) {
    }
}
