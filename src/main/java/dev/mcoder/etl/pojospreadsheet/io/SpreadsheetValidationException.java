package dev.mcoder.etl.pojospreadsheet.io;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@link SpreadsheetReader} when a sheet holds invalid input: header labels that do not
 * match (with {@link ReadOptions#validateHeader()}), cells that cannot be converted to their field
 * type, or POJOs that fail Jakarta Bean Validation. When thrown, no rows are returned.
 *
 * <p>The message lists the first 20 errors. {@link #getErrors()} lists every error in the sheet,
 * for example to show them to the user who uploaded the file:
 *
 * <pre>
 * 2 invalid value(s) in sheet:
 *   A3 (Employee ID): must match EMP-00000 [value: EMP-2]
 *   D4 (Age): cannot convert 'forty' to Integer [value: forty]
 * </pre>
 */
public class SpreadsheetValidationException extends RuntimeException {

    private static final int MAX_ERRORS_IN_MESSAGE = 20;

    private final transient List<RowError> errors;

    /**
     * @param errors the errors found, in the order they should be reported
     */
    public SpreadsheetValidationException(List<RowError> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Every error in the sheet, ordered by row and then by column. When the header is invalid,
     * only header errors are listed, since data rows are not read.
     *
     * @return an unmodifiable list; never empty when thrown by {@link SpreadsheetReader}
     */
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
