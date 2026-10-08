# springboot-web-jpa

Spring Boot 4.1 (Web MVC + Data JPA + embedded H2) sample that imports and exports an `employee` table as `.xlsx` with [pojo-spreadsheet](../..).

## Run

The sample depends on the library's current snapshot, so install it from the repository root first (JDK 17+):

```sh
cd ../.. && ./mvnw install -DskipTests      # from example/springboot-web-jpa/
cd example/springboot-web-jpa && ./mvnw spring-boot:run
```

Open http://localhost:8080 for an upload form and the table contents. Three sample employees are seeded at startup.

Run the tests with `./mvnw test`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/employees` | Table rows as JSON |
| `GET` | `/api/employees/export` | Table as `.xlsx` |
| `GET` | `/api/employees/template` | Empty `.xlsx` with the header row |
| `POST` | `/api/employees/import?replace=false` | Multipart `file` (`.xlsx`/`.xls`); `replace=true` deletes existing rows first |

```sh
curl -OJ http://localhost:8080/api/employees/export
curl -F file=@employees.xlsx "http://localhost:8080/api/employees/import?replace=true"
```

Import is all-or-nothing. Wrong header labels or invalid cells return `400` with one error per cell (`B1`, `D4`, ...); employee IDs duplicated in the file or already in the table return `409`.

## How it fits together

- `Employee` is both the JPA entity and the spreadsheet row: `@SheetCol` maps fields to columns A–G, and the Jakarta constraints (`@Email`, `@Min`, `@NoFormula`, ...) validate each row on import.
- `SampleSpringbootApplication` registers `PojoValidator`, `SpreadsheetReader` and `SpreadsheetWriter` as singleton beans, and seeds three employees.
- `EmployeeController` reads uploads into entities and saves them in one transaction, and writes the table straight to the response on export. Duplicate IDs are caught by the `employee_id` unique constraint, which rolls the import back.
- The database is in-memory H2 (`jdbc:h2:mem:employees`); browse it at http://localhost:8080/h2-console (user `sa`, no password).
