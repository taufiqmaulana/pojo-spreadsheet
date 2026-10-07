package dev.mcoder.etl.pojospreadsheet.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.NumberToTextConverter;

/**
 * Converts a cell to a Java value. Formula cells use their cached result; formulas are never
 * evaluated. Not thread-safe, because {@link DataFormatter} is not.
 */
final class CellValueConverter {

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class);

    private static final Set<Class<?>> SUPPORTED = Set.of(
            String.class, Boolean.class,
            Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
            BigInteger.class, BigDecimal.class,
            LocalDate.class, LocalDateTime.class, Date.class);

    private final DataFormatter formatter = new DataFormatter();

    static boolean supports(Class<?> type) {
        Class<?> target = wrap(type);
        return SUPPORTED.contains(target) || target.isEnum();
    }

    /**
     * Returns {@code null} for a blank cell, and for blank text when the target is not {@code String}.
     *
     * @throws ConversionException when the cell value cannot be converted to {@code type}
     */
    Object convert(Cell cell, Class<?> type) {
        CellType cellType = effectiveType(cell);
        if (cellType == CellType.BLANK) {
            return null;
        }
        if (cellType == CellType.ERROR) {
            throw new ConversionException("cell contains an error value");
        }
        Class<?> target = wrap(type);
        if (target == String.class) {
            return text(cell);
        }
        if (cellType == CellType.STRING && cell.getStringCellValue().isBlank()) {
            return null;
        }
        try {
            if (target == Boolean.class) {
                return toBoolean(cell, cellType);
            }
            if (target == LocalDate.class || target == LocalDateTime.class || target == Date.class) {
                return toDate(cell, cellType, target);
            }
            if (target.isEnum()) {
                return toEnum(cell, target);
            }
            return toNumber(toBigDecimal(cell, cellType), target);
        } catch (IllegalArgumentException | ArithmeticException | DateTimeException e) {
            throw new ConversionException("cannot convert '" + text(cell) + "' to " + target.getSimpleName());
        }
    }

    /**
     * The cell value as text, formatted the way the spreadsheet application displays it.
     */
    String text(Cell cell) {
        switch (effectiveType(cell)) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                CellStyle style = cell.getCellStyle();
                return formatter.formatRawCellContents(
                        cell.getNumericCellValue(), style.getDataFormat(), style.getDataFormatString());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    static boolean isBlank(Cell cell) {
        CellType cellType = effectiveType(cell);
        return cellType == CellType.BLANK
                || cellType == CellType.STRING && cell.getStringCellValue().isBlank();
    }

    private static CellType effectiveType(Cell cell) {
        if (cell == null) {
            return CellType.BLANK;
        }
        CellType cellType = cell.getCellType();
        return cellType == CellType.FORMULA ? cell.getCachedFormulaResultType() : cellType;
    }

    private static Class<?> wrap(Class<?> type) {
        return WRAPPERS.getOrDefault(type, type);
    }

    private static Boolean toBoolean(Cell cell, CellType cellType) {
        switch (cellType) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                double number = cell.getNumericCellValue();
                if (number == 0 || number == 1) {
                    return number == 1;
                }
                break;
            case STRING:
                String text = cell.getStringCellValue().trim();
                if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                    return Boolean.valueOf(text);
                }
                break;
            default:
                break;
        }
        throw new IllegalArgumentException();
    }

    private static Object toDate(Cell cell, CellType cellType, Class<?> target) {
        LocalDateTime dateTime;
        if (cellType == CellType.NUMERIC) {
            dateTime = cell.getLocalDateTimeCellValue();
        } else if (cellType == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            dateTime = text.contains("T") ? LocalDateTime.parse(text) : LocalDate.parse(text).atStartOfDay();
        } else {
            throw new IllegalArgumentException();
        }
        if (target == LocalDate.class) {
            return dateTime.toLocalDate();
        }
        if (target == LocalDateTime.class) {
            return dateTime;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Object toEnum(Cell cell, Class<?> target) {
        String text = text(cell).trim();
        return Arrays.stream(target.getEnumConstants())
                .map(Enum.class::cast)
                .filter(constant -> constant.name().equalsIgnoreCase(text))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private static BigDecimal toBigDecimal(Cell cell, CellType cellType) {
        switch (cellType) {
            case NUMERIC:
                // Shortest text that round-trips the double, so 0.1 stays 0.1 instead of 0.1000000000000000055...
                return new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            case STRING:
                return new BigDecimal(cell.getStringCellValue().trim());
            default:
                throw new IllegalArgumentException();
        }
    }

    private static Object toNumber(BigDecimal value, Class<?> target) {
        if (target == BigDecimal.class) {
            return value;
        }
        if (target == BigInteger.class) {
            return value.toBigIntegerExact();
        }
        if (target == Long.class) {
            return value.longValueExact();
        }
        if (target == Integer.class) {
            return value.intValueExact();
        }
        if (target == Short.class) {
            return value.shortValueExact();
        }
        if (target == Byte.class) {
            return value.byteValueExact();
        }
        if (target == Double.class) {
            return value.doubleValue();
        }
        if (target == Float.class) {
            return value.floatValue();
        }
        throw new IllegalStateException("Unsupported type " + target);
    }

    static final class ConversionException extends RuntimeException {

        ConversionException(String message) {
            super(message);
        }
    }
}
