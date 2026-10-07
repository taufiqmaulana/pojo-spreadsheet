package dev.mcoder.etl.pojospreadsheet.sample;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.validation.constraints.NoFormula;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One row of an employee sheet: {@code @SheetCol} maps the columns, the Jakarta annotations
 * validate the values.
 */
public class EmployeeRow {

    public enum Status {
        ACTIVE, INACTIVE
    }

    @SheetCol(value = "A", label = "Employee ID")
    @NotBlank
    @Pattern(regexp = "EMP-\\d{5}", message = "must match EMP-00000")
    private String employeeId;

    @SheetCol(value = "B", label = "Full name")
    @NotBlank
    @Size(max = 100)
    @NoFormula
    private String fullName;

    @SheetCol(value = "C", label = "Email")
    @NotBlank
    @Email
    private String email;

    @SheetCol(value = "D", label = "Age")
    @NotNull
    @Min(18)
    @Max(65)
    private Integer age;

    @SheetCol(value = "E", label = "Salary")
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    private BigDecimal salary;

    @SheetCol(value = "F", label = "Join date")
    @NotNull
    @PastOrPresent
    private LocalDate joinDate;

    // No label: errors show the field name
    @SheetCol("G")
    @NotNull
    private Status status;

    // Not mapped: left untouched by the reader
    private String remarks;

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        return "EmployeeRow[" + employeeId + ", " + fullName + ", " + email + ", " + age + ", "
                + salary + ", " + joinDate + ", " + status + "]";
    }
}
