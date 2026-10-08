package dev.mcoder.etl.sample;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetReader;
import dev.mcoder.etl.pojospreadsheet.io.SpreadsheetWriter;
import dev.mcoder.etl.pojospreadsheet.validation.PojoValidator;
import dev.mcoder.etl.sample.Employee.Status;

@SpringBootApplication
public class SampleSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleSpringbootApplication.class, args);
    }

    // The pojo-spreadsheet classes are thread-safe, so one instance of each is shared.
    // Spring calls PojoValidator.close() on shutdown.
    @Bean
    PojoValidator pojoValidator() {
        return new PojoValidator();
    }

    @Bean
    SpreadsheetReader spreadsheetReader(PojoValidator pojoValidator) {
        return new SpreadsheetReader(pojoValidator);
    }

    @Bean
    SpreadsheetWriter spreadsheetWriter() {
        return new SpreadsheetWriter();
    }

    // Seeds the embedded database so the export returns rows right after startup
    @Bean
    ApplicationRunner sampleData(EmployeeRepository repository) {
        return args -> repository.saveAll(List.of(
                new Employee("EMP-00001", "Alice Johnson",  "alice.johnson@example.com",  34, new BigDecimal("85000"),  LocalDate.of(2019, 3, 11), Status.ACTIVE),
                new Employee("EMP-00002", "Bob Smith",      "bob.smith@example.com",      41, new BigDecimal("120000"), LocalDate.of(2015, 8, 3),  Status.ON_LEAVE),
                new Employee("EMP-00003", "Carol Martinez", "carol.martinez@example.com", 27, new BigDecimal("64500"),  LocalDate.of(2023, 1, 16), Status.ACTIVE)
            ));
    }
}
