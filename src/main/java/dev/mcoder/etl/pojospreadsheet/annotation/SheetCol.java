package dev.mcoder.etl.pojospreadsheet.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Maps a field to a spreadsheet column. For each data row {@code n}, the value of cell
 * {@code <column><n>} is converted to the field type and assigned to the field.
 *
 * <pre>{@code
 * @SheetCol("A")
 * private String name;   // A2 -> name, A3 -> name, ...
 *
 * @SheetCol(value = "B", label = "Employee ID")
 * private String employeeId;
 * }</pre>
 *
 * <p>On a record, annotate the components:
 *
 * <pre>{@code
 * public record EmployeeRow(@SheetCol("A") String employeeId, @SheetCol("B") @NotNull Integer age) {}
 * }</pre>
 *
 * <p>Supported field types: {@code String}, {@code boolean}/{@code Boolean}, every primitive
 * number type and its wrapper, {@code BigDecimal}, {@code BigInteger}, {@code LocalDate},
 * {@code LocalDateTime}, {@code java.util.Date} and enums (matched by constant name).
 */
@Documented
@Target(FIELD)
@Retention(RUNTIME)
public @interface SheetCol {

    /**
     * Column letter(s), for example {@code "A"} or {@code "AB"}; case-insensitive.
     */
    String value();

    /**
     * Human-readable column name, normally the sheet's header text. It is used in error reports,
     * written as the header by {@code SpreadsheetWriter}, and checked against the header when
     * reading with {@code ReadOptions.validateHeader(true)}. Defaults to the field name when empty.
     */
    String label() default "";
}
