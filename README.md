# pojo-spreadsheet

Reads spreadsheet rows (`.xlsx` and `.xls`) into POJOs using a `@SheetCol` annotation, validates each POJO with Jakarta Bean Validation, and writes collections of POJOs back to spreadsheets.

- Maps columns to fields by column letter: `@SheetCol("A") private String name;`
- Converts cells to the field type: text, numbers, booleans, dates and enums
- Applies the standard Jakarta validation annotations (`@NotBlank`, `@Email`, `@Min`, ...) to every row
- Reports every invalid cell in the sheet at once, by cell reference (`D4 (Age): ...`)
- Writes collections to `.xlsx` or `.xls`, with a header row of column labels
- Lists a POJO's column mapping, for example to document an import template

Built on Apache POI 5.5 and Hibernate Validator 9 (Jakarta Validation 3.1).

## Requirements

- JDK 17+

## Installation

Available from Maven Central. Use the latest version from the [releases](https://github.com/taufiqmaulana/pojo-spreadsheet/releases).

```xml
<dependency>
    <groupId>dev.mcoder.etl</groupId>
    <artifactId>pojo-spreadsheet</artifactId>
    <version>X.Y.Z</version>
</dependency>
```

## Quick start

Annotate a POJO. It needs a no-argument constructor; fields can be private.

```java
public class EmployeeRow {

    @SheetCol(value = "A", label = "Employee ID")
    @NotBlank
    @Pattern(regexp = "EMP-\\d{5}", message = "must match EMP-00000")
    private String employeeId;

    @SheetCol(value = "B", label = "Full name")
    @NotBlank
    @NoFormula
    private String fullName;

    @SheetCol(value = "D", label = "Age")
    @NotNull
    @Min(18)
    private Integer age;

    @SheetCol(value = "F", label = "Join date")
    @PastOrPresent
    private LocalDate joinDate;

    @SheetCol("G")              // no label: error reports use the field name
    private Status status;

    // getters and setters
}
```

Read the sheet:

```java
PojoValidator validator = new PojoValidator();            // create once and reuse
SpreadsheetReader reader = new SpreadsheetReader(validator);

try {
    List<EmployeeRow> rows = reader.read(Path.of("employees.xlsx"), EmployeeRow.class);
} catch (SpreadsheetValidationException e) {
    e.getErrors().forEach(System.out::println);
}
```

`PojoValidator` and `SpreadsheetReader` are thread-safe. Close the `PojoValidator` when the application shuts down.

## `@SheetCol`

| Attribute | Required | Description |
|---|---|---|
| `value` | yes | Column letter(s), such as `"A"` or `"AB"`. Case-insensitive. |
| `label` | no | Readable column name used in error reports, such as the sheet's header text. Defaults to the field name. |

Fields in parent classes are mapped too. Fields without `@SheetCol` are left untouched.

### Supported field types

| Field type | Accepted cell values |
|---|---|
| `String` | Any cell. Numbers and dates keep their displayed form, so `12345` becomes `"12345"`, not `"12345.0"` |
| `int`, `long`, `short`, `byte`, `double`, `float` and their wrappers, `BigDecimal`, `BigInteger` | Number cells, or text containing a number. Whole-number types reject fractions and out-of-range values |
| `boolean` / `Boolean` | Boolean cells, `1`/`0`, or the text `true`/`false` (any case) |
| `LocalDate`, `LocalDateTime`, `java.util.Date` | Date cells, or ISO text such as `2024-05-06` or `2024-05-06T07:08` |
| Enums | Text matching a constant name (any case) |

How cells are read:

- **Empty cells** give `null`; primitive fields keep their default value instead. Use wrapper types such as `Integer` if `@NotNull` should catch empty cells.
- **Blank text** gives `null` for every type except `String`.
- **Formula cells** give the result last saved in the file; formulas are never recalculated.

## Read options

```java
reader.read(path, EmployeeRow.class);                     // first sheet, skips 1 header row
reader.read(path, EmployeeRow.class, ReadOptions.defaults()
        .sheet("Employees")                               // or .sheet(2) for a zero-based index
        .headerRows(2));
```

`read` accepts a `Path`, an `InputStream` or an open POI `Workbook`. The reader does not close an `InputStream` or `Workbook` you pass in.

Rows whose mapped cells are all empty are skipped.

## Validation and errors

Each row is converted into a POJO, then validated against its Jakarta annotations, including nested `@Valid` objects and constraints on list elements.

Reading is all-or-nothing. If any cell cannot be converted or any POJO is invalid, `read` throws a `SpreadsheetValidationException` and returns no rows. `getErrors()` returns one `RowError` per problem, across the whole sheet:

| Component | Example |
|---|---|
| `row` | `4` (one-based, as shown in the spreadsheet application) |
| `cell` | `"D4"` |
| `field` | `"age"` |
| `label` | `"Age"` |
| `invalidValue` | `"forty"` |
| `message` | `"cannot convert 'forty' to Integer"` |

Errors are ordered by row, then by column. If a cell cannot be converted, only the conversion error is reported, not an extra `@NotNull` error for the same field.

```
6 invalid value(s) in sheet:
  A3 (Employee ID): must match EMP-00000 [value: EMP-2]
  B3 (Full name): must not start with a spreadsheet formula character (=, +, -, @) [value: =cmd|' /C calc'!A0]
  C3 (Email): must be a well-formed email address [value: jane(at)example]
  D3 (Age): must be greater than or equal to 18 [value: 17]
  D4 (Age): cannot convert 'forty' to Integer [value: forty]
  G4 (status): cannot convert 'RETIRED' to Status [value: RETIRED]
```

### `@NoFormula`

`@NoFormula` is a custom constraint included in this library. It rejects text starting with `=`, `+`, `-`, `@`, a tab or a carriage return. A spreadsheet application treats such text as a formula, so this protects against formula (CSV) injection when the value is later written to another spreadsheet.

### Validating POJOs directly

`PojoValidator` can also be used without a spreadsheet:

```java
Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
validator.validateOrThrow(employee);   // throws ConstraintViolationException when invalid
```

## Writing

`SpreadsheetWriter` writes one row per POJO, each `@SheetCol` field in its column. By default the first row is a bold, frozen header holding each column's label.

```java
SpreadsheetWriter writer = new SpreadsheetWriter();       // thread-safe, reuse

writer.write(employees, EmployeeRow.class, Path.of("employees.xlsx"));   // format from extension: .xlsx or .xls
writer.write(employees, EmployeeRow.class, outputStream);                // .xlsx
writer.write(employees, EmployeeRow.class, outputStream, SpreadsheetFormat.XLS,
        WriteOptions.defaults()
                .sheet("Employees")                       // default "Sheet1"
                .header(false));                          // default true
```

To put several sheets in one file, write each to an open workbook, then save it yourself:

```java
try (Workbook workbook = new XSSFWorkbook()) {
    writer.write(active, EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Active"));
    writer.write(inactive, EmployeeRow.class, workbook, WriteOptions.defaults().sheet("Inactive"));
    workbook.write(out);
}
```

How values are written:

| Field value | Cell |
|---|---|
| `String` | Text. Text starting with `=` is still written as text, never as a formula |
| Numbers | Number |
| `boolean` / `Boolean` | Boolean |
| `LocalDate` | Date formatted `yyyy-mm-dd` |
| `LocalDateTime`, `java.util.Date` | Date formatted `yyyy-mm-dd hh:mm:ss` |
| Enums | Text of the constant name |
| `null` | Empty cell |

- **Precision:** spreadsheet numbers are doubles, so a `long`, `BigDecimal` or `BigInteger` with more than 15 significant digits loses precision. Use a `String` field if exact digits matter.
- **Large collections:** `.xlsx` output is streamed, keeping only 100 rows in memory at a time.
- **Row limits:** a collection that doesn't fit the format (65,536 rows for `.xls`, 1,048,576 for `.xlsx`, header included) is rejected with `IllegalArgumentException`.
- **Safe file writes:** writing to a `Path` goes to a temporary file first, so if writing fails an existing file is left unchanged.
- **No validation:** the writer does not run the Jakarta validation annotations; validate with `PojoValidator` first if needed.

Files written by `SpreadsheetWriter` can be read back with `SpreadsheetReader`.

## Column metadata

`SheetMetadata.columns` lists a POJO's mapped columns, ordered by column. Use it to document an import template or build a custom export.

```java
for (ColumnMetadata column : SheetMetadata.columns(EmployeeRow.class)) {
    System.out.println(column.column() + " " + column.fieldName() + " " + column.label());
}
// A employeeId Employee ID
// B fullName Full name
// ...
// G status status
```

## Logging

Apache POI logs through the Log4j API. If your application provides no Log4j implementation, the message `Log4j API could not find a logging provider` is printed; it is harmless. To send POI's logs to your logging framework, add a bridge such as `log4j-slf4j2-impl` (for SLF4J) to your application.

## Build

```sh
./mvnw verify
```

`JAVA_HOME` must point to a JDK 17+. The Maven wrapper downloads Maven, so no Maven installation is needed.

## Samples

Runnable samples are in `src/test/java/dev/mcoder/etl/pojospreadsheet/sample`:

- `SpreadsheetReadSample`: reads `EmployeeRow`s from an `.xlsx` file given as the first argument, or from a generated demo workbook
- `ValidationSample`: validates `Employee` POJOs, including a nested object and list elements

## Releasing

Creating a GitHub release publishes to Maven Central. The tag sets the version: `v1.2.3` publishes `1.2.3`. Published versions can never be changed or deleted, so each release needs a new tag.

The workflow (`.github/workflows/maven-publish.yml`) needs four repository secrets: `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` (a user token from central.sonatype.com), `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`.

To check a release build locally without signing:

```sh
./mvnw verify -P release -Dgpg.skip
```

## License

[Apache License 2.0](LICENSE)
