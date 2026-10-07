package dev.mcoder.etl.pojospreadsheet.validation.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

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
