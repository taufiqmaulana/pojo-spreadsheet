package dev.mcoder.etl.pojospreadsheet.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

class RecordMappingTest {

    private static PojoValidator validator;
    private static SpreadsheetReader reader;
    private static final SpreadsheetWriter writer = new SpreadsheetWriter();

    @BeforeAll
    static void setUp() {
        validator = new PojoValidator();
        reader = new SpreadsheetReader(validator);
    }

    @AfterAll
    static void tearDown() {
        validator.close();
    }

    record Product(
            @SheetCol(value = "A", label = "SKU") @NotBlank String sku,
            String note,                                   // not mapped: always null
            @SheetCol(value = "C", label = "Quantity") @Min(1) int quantity,
            @SheetCol(value = "B", label = "Price") BigDecimal price,
            @SheetCol(value = "D", label = "Released") LocalDate released) {
    }

    record Range(@SheetCol("A") Integer from, @SheetCol("B") Integer to) {
        Range {
            if (from != null && to != null && from > to) {
                throw new IllegalArgumentException("from must not be greater than to");
            }
        }
    }

    @Test
    void readsRowsThroughTheCanonicalConstructor() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            row(sheet, 0, "SKU", "Price", "Quantity", "Released");
            row(sheet, 1, "P-1", 9.5, 3, LocalDate.of(2024, 5, 6));
            row(sheet, 2, "P-2", 12, 1);
        });

        List<Product> rows = reader.read(new ByteArrayInputStream(xlsx), Product.class);

        assertEquals(List.of(
                new Product("P-1", null, 3, new BigDecimal("9.5"), LocalDate.of(2024, 5, 6)),
                new Product("P-2", null, 1, new BigDecimal("12"), null)),
                rows);
    }

    @Test
    void passesPrimitiveDefaultsForEmptyCells() throws IOException {
        record Flags(@SheetCol("A") String name, @SheetCol("B") int count, @SheetCol("C") boolean enabled) {
        }
        byte[] xlsx = xlsx(sheet -> {
            row(sheet, 0, "Name", "Count", "Enabled");
            row(sheet, 1, "x");
        });

        assertEquals(List.of(new Flags("x", 0, false)), reader.read(new ByteArrayInputStream(xlsx), Flags.class));
    }

    @Test
    void validatesRecordComponents() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            row(sheet, 0, "SKU", "Price", "Quantity");
            row(sheet, 1, " ", 1, 0);
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), Product.class));

        assertEquals(List.of("A2 (SKU): must not be blank [value:  ]",
                "C2 (Quantity): must be greater than or equal to 1 [value: 0]"),
                e.getErrors().stream().map(RowError::toString).toList());
    }

    @Test
    void reportsConstructorExceptionAsRowError() throws IOException {
        byte[] xlsx = xlsx(sheet -> {
            row(sheet, 0, "From", "To");
            row(sheet, 1, 1, 5);
            row(sheet, 2, 9, 2);
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), Range.class));

        assertEquals(1, e.getErrors().size());
        RowError error = e.getErrors().get(0);
        assertEquals(3, error.row());
        assertNull(error.cell());
        assertNull(error.field());
        assertEquals("from must not be greater than to", error.message());
        assertEquals("row 3: from must not be greater than to [value: null]", error.toString());
    }

    @Test
    void reportsOnlyTheConversionErrorWhenTheConstructorAlsoFails() throws IOException {
        record Strict(@SheetCol(value = "A", label = "Count") Integer count) {
            Strict {
                if (count == null) {
                    throw new NullPointerException("count");
                }
            }
        }
        byte[] xlsx = xlsx(sheet -> {
            row(sheet, 0, "Count");
            row(sheet, 1, "many");
        });

        SpreadsheetValidationException e = assertThrows(SpreadsheetValidationException.class,
                () -> reader.read(new ByteArrayInputStream(xlsx), Strict.class));

        assertEquals(List.of("A2 (Count): cannot convert 'many' to Integer [value: many]"),
                e.getErrors().stream().map(RowError::toString).toList());
    }

    @Test
    void writesRecordsAndReadsThemBack() throws IOException {
        List<Product> products = List.of(
                new Product("P-1", "ignored", 3, new BigDecimal("9.5"), LocalDate.of(2024, 5, 6)),
                new Product("P-2", null, 7, null, null));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(products, Product.class, out);
        List<Product> rows = reader.read(new ByteArrayInputStream(out.toByteArray()), Product.class,
                ReadOptions.defaults().validateHeader(true));

        assertEquals(List.of(
                new Product("P-1", null, 3, new BigDecimal("9.5"), LocalDate.of(2024, 5, 6)),
                new Product("P-2", null, 7, null, null)), rows);
    }

    @Test
    void listsRecordColumnsByColumnLetter() {
        assertEquals(List.of(
                new ColumnMetadata("A", "sku", "SKU"),
                new ColumnMetadata("B", "price", "Price"),
                new ColumnMetadata("C", "quantity", "Quantity"),
                new ColumnMetadata("D", "released", "Released")),
                SheetMetadata.columns(Product.class));
    }

    @Test
    void rejectsRecordsWithoutMappedComponents() {
        record Unmapped(String value) {
        }
        assertThrows(IllegalArgumentException.class, () -> SheetMetadata.columns(Unmapped.class));
    }

    private static byte[] xlsx(Consumer<Sheet> content) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            content.accept(workbook.createSheet("Sheet1"));
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
                row.createCell(c).setCellValue((String) value);
            }
        }
    }
}
