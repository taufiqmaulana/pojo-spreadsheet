package dev.mcoder.etl.pojospreadsheet.validation.constraints;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated text must not start with a character that a spreadsheet application would
 * interpret as a formula ({@code = + - @}, tab or carriage return), guarding against
 * formula (CSV) injection when the value is written to a cell. {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = NoFormulaValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface NoFormula {

    String message() default "must not start with a spreadsheet formula character (=, +, -, @)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
