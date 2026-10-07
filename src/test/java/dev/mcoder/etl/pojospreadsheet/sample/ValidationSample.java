package dev.mcoder.etl.pojospreadsheet.sample;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import jakarta.validation.ConstraintViolation;

/**
 * Runnable demo: validates one valid and one invalid {@link Employee} and prints the result.
 */
public final class ValidationSample {

    private ValidationSample() {
    }

    public static void main(String[] args) {
        try (PojoValidator validator = new PojoValidator()) {
            print("Valid employee", validator.validate(validEmployee()));
            print("Invalid employee", validator.validate(invalidEmployee()));
        }
    }

    public static Employee validEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeId("EMP-00042");
        employee.setFullName("Jane Doe");
        employee.setEmail("jane.doe@example.com");
        employee.setAge(30);
        employee.setSalary(new BigDecimal("15000000.00"));
        employee.setJoinDate(LocalDate.of(2020, 1, 15));
        employee.setAddress(new Address("Jl. Sudirman 1", "Jakarta", "10210"));
        employee.setSkills(List.of("Java", "SQL"));
        return employee;
    }

    public static Employee invalidEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeId("42");
        employee.setFullName("=HYPERLINK(\"http://evil.example\")");
        employee.setEmail("not-an-email");
        employee.setAge(17);
        employee.setSalary(new BigDecimal("-1"));
        employee.setJoinDate(LocalDate.now().plusDays(1));
        employee.setAddress(new Address("", "Jakarta", "ABC"));
        employee.setSkills(List.of("Java", " "));
        return employee;
    }

    private static <T> void print(String title, Set<ConstraintViolation<T>> violations) {
        System.out.println(title + ": " + (violations.isEmpty() ? "OK" : violations.size() + " violation(s)"));
        violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .forEach(v -> System.out.printf("  %-28s %s (was: %s)%n",
                        v.getPropertyPath(), v.getMessage(), v.getInvalidValue()));
    }
}
