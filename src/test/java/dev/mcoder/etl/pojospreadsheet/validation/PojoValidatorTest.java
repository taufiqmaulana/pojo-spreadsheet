package dev.mcoder.etl.pojospreadsheet.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.mcoder.etl.pojospreadsheet.sample.Employee;
import dev.mcoder.etl.pojospreadsheet.sample.ValidationSample;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

class PojoValidatorTest {

    private static PojoValidator validator;

    @BeforeAll
    static void setUp() {
        validator = new PojoValidator();
    }

    @AfterAll
    static void tearDown() {
        validator.close();
    }

    @Test
    void validPojoHasNoViolations() {
        assertTrue(validator.validate(ValidationSample.validEmployee()).isEmpty());
    }

    @Test
    void invalidPojoReportsEveryViolatedProperty() {
        Set<String> paths = paths(validator.validate(ValidationSample.invalidEmployee()));

        assertEquals(Set.of(
                "employeeId",
                "fullName",
                "email",
                "age",
                "salary",
                "joinDate",
                "address.street",
                "address.postalCode",
                "skills[1].<list element>"), paths);
    }

    @Test
    void missingRequiredFieldsAreReported() {
        Set<String> paths = paths(validator.validate(new Employee()));

        assertEquals(Set.of("employeeId", "fullName", "email", "age", "salary", "joinDate", "address"), paths);
    }

    @Test
    void noFormulaRejectsFormulaPrefixes() {
        Employee employee = ValidationSample.validEmployee();
        for (String name : new String[] {"=1+1", "+1", "-1", "@SUM(A1)", "\tJane"}) {
            employee.setFullName(name);
            assertEquals(Set.of("fullName"), paths(validator.validate(employee)), name);
        }
    }

    @Test
    void validateOrThrowReturnsValidPojo() {
        Employee employee = ValidationSample.validEmployee();
        assertSame(employee, validator.validateOrThrow(employee));
    }

    @Test
    void validateOrThrowThrowsWithViolations() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class,
                () -> validator.validateOrThrow(ValidationSample.invalidEmployee()));
        assertEquals(9, e.getConstraintViolations().size());
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
