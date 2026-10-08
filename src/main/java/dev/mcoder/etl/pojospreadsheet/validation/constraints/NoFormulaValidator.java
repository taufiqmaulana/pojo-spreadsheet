package dev.mcoder.etl.pojospreadsheet.validation.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link NoFormula}: the text is valid when it is {@code null}, empty, or does not start
 * with {@code = + - @}, a tab or a carriage return. Leading spaces are not skipped, matching how
 * spreadsheet applications detect formulas.
 */
public class NoFormulaValidator implements ConstraintValidator<NoFormula, CharSequence> {

    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return FORMULA_PREFIXES.indexOf(value.charAt(0)) < 0;
    }
}
