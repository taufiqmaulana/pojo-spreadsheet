package dev.mcoder.etl.pojospreadsheet.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.sample.EmployeeRow;

class SheetMetadataTest {

    @Test
    void listsEveryMappedColumnWithItsFieldAndLabel() {
        assertEquals(List.of(
                new ColumnMetadata("A", "employeeId", "Employee ID"),
                new ColumnMetadata("B", "fullName", "Full name"),
                new ColumnMetadata("C", "email", "Email"),
                new ColumnMetadata("D", "age", "Age"),
                new ColumnMetadata("E", "salary", "Salary"),
                new ColumnMetadata("F", "joinDate", "Join date"),
                new ColumnMetadata("G", "status", "status")), SheetMetadata.columns(EmployeeRow.class));
    }

    @Test
    void ordersByColumnAndIncludesInheritedFields() {
        assertEquals(List.of(
                new ColumnMetadata("A", "id", "ID"),
                new ColumnMetadata("C", "code", "code"),
                new ColumnMetadata("Z", "name", "Name"),
                new ColumnMetadata("AA", "note", "Note")), SheetMetadata.columns(Child.class));
    }

    @Test
    void rejectsClassWithoutSheetColFields() {
        assertThrows(IllegalArgumentException.class, () -> SheetMetadata.columns(String.class));
    }

    static class Base {
        @SheetCol(value = "Z", label = "Name") String name;
        @SheetCol(value = "a", label = " ID ") String id;
    }

    static class Child extends Base {
        @SheetCol(value = "AA", label = "Note") String note;
        @SheetCol("C") String code;
    }
}
