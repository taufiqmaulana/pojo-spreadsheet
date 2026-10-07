package dev.mcoder.etl.pojospreadsheet.sample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dev.mcoder.etl.pojospreadsheet.reader.SpreadsheetReader;
import dev.mcoder.etl.pojospreadsheet.reader.SpreadsheetValidationException;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;

/**
 * Runnable demo: reads {@link EmployeeRow}s from an .xlsx file given as the first argument, or
 * from a small generated workbook containing one valid and two invalid rows.
 */
public final class SpreadsheetReadSample {

    private SpreadsheetReadSample() {
    }

    public static void main(String[] args) throws IOException {
        try (PojoValidator validator = new PojoValidator()) {
            SpreadsheetReader reader = new SpreadsheetReader(validator);
            try {
                List<EmployeeRow> rows = args.length > 0
                        ? reader.read(Path.of(args[0]), EmployeeRow.class)
                        : readGenerated(reader);
                rows.forEach(System.out::println);
            } catch (SpreadsheetValidationException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private static List<EmployeeRow> readGenerated(SpreadsheetReader reader) throws IOException {
        try (InputStream in = new ByteArrayInputStream(sampleWorkbook())) {
            return reader.read(in, EmployeeRow.class);
        }
    }

    private static byte[] sampleWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Sheet sheet = workbook.createSheet("Employees");
            row(sheet, 0, "Employee ID", "Full name", "Email", "Age", "Salary", "Join date", "Status");
            row(sheet, 1, "EMP-00001", "Jane Doe", "jane@example.com", 30, 15000000.50, LocalDate.of(2020, 1, 15), "active");
            row(sheet, 2, "EMP-2", "=cmd|' /C calc'!A0", "jane(at)example", 17, 5000000, LocalDate.of(2021, 3, 1), "ACTIVE");
            row(sheet, 3, "EMP-00003", "John Roe", "john@example.com", "forty", 7000000, LocalDate.of(2022, 6, 1), "RETIRED");
            for (int r = 1; r <= 3; r++) {
                sheet.getRow(r).getCell(5).setCellStyle(dateStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            Object value = values[c];
            if (value instanceof Number number) {
                row.createCell(c).setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                row.createCell(c).setCellValue(date);
            } else {
                row.createCell(c).setCellValue(String.valueOf(value));
            }
        }
    }
}
