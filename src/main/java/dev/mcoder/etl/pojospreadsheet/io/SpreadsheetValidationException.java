package dev.mcoder.etl.pojospreadsheet.io;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when one or more cells could not be converted or failed Jakarta Bean Validation.
 * {@link #getErrors()} lists every error in the sheet, not only the first.
 */
public class SpreadsheetValidationException extends RuntimeException {

    private static final int MAX_ERRORS_IN_MESSAGE = 20;

    private final transient List<RowError> errors;

    public SpreadsheetValidationException(List<RowError> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public List<RowError> getErrors() {
        return errors;
    }

    private static String buildMessage(List<RowError> errors) {
        String details = errors.stream()
                .limit(MAX_ERRORS_IN_MESSAGE)
                .map(error -> "  " + error)
                .collect(Collectors.joining(System.lineSeparator()));
        String more = errors.size() > MAX_ERRORS_IN_MESSAGE
                ? System.lineSeparator() + "  ... and " + (errors.size() - MAX_ERRORS_IN_MESSAGE) + " more"
                : "";
        return errors.size() + " invalid value(s) in sheet:" + System.lineSeparator() + details + more;
    }
}
