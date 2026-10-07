package dev.mcoder.etl.pojospreadsheet.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.poi.ss.util.CellReference;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;

/**
 * The {@link SheetCol} fields of a POJO class, resolved once and reused for every row.
 */
final class SheetMapping<T> {

    private static final Pattern COLUMN = Pattern.compile("[A-Z]{1,3}");

    // ClassValue lets a mapping be garbage-collected together with its class
    private static final ClassValue<SheetMapping<?>> CACHE = new ClassValue<>() {
        @Override
        protected SheetMapping<?> computeValue(Class<?> type) {
            return create(type);
        }
    };

    private final Constructor<T> constructor;
    private final Map<String, Column> columnsByField;

    private SheetMapping(Constructor<T> constructor, Map<String, Column> columnsByField) {
        this.constructor = constructor;
        this.columnsByField = columnsByField;
    }

    /**
     * The mapping of {@code type}, built on first use and cached.
     *
     * @throws IllegalArgumentException if {@code type} cannot be mapped
     */
    @SuppressWarnings("unchecked")
    static <T> SheetMapping<T> of(Class<T> type) {
        return (SheetMapping<T>) CACHE.get(Objects.requireNonNull(type, "type"));
    }

    private static <T> SheetMapping<T> create(Class<T> type) {
        Map<String, Column> columns = new LinkedHashMap<>();
        for (Class<?> current : hierarchy(type)) {
            for (Field field : current.getDeclaredFields()) {
                SheetCol sheetCol = field.getAnnotation(SheetCol.class);
                if (sheetCol != null) {
                    columns.put(field.getName(), column(type, field, sheetCol));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " has no @SheetCol fields");
        }
        return new SheetMapping<>(noArgConstructor(type), Collections.unmodifiableMap(columns));
    }

    Iterable<Column> columns() {
        return columnsByField.values();
    }

    /**
     * The column mapped to {@code fieldName}, or {@code null} if that field has no {@link SheetCol}.
     */
    Column column(String fieldName) {
        return fieldName == null ? null : columnsByField.get(fieldName);
    }

    T newInstance() {
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private static List<Class<?>> hierarchy(Class<?> type) {
        // Superclass fields first, so columns keep declaration order from the top down
        Deque<Class<?>> classes = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            classes.addFirst(current);
        }
        return List.copyOf(classes);
    }

    private static Column column(Class<?> type, Field field, SheetCol sheetCol) {
        String where = type.getName() + "." + field.getName();
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException("@SheetCol field must not be static or final: " + where);
        }
        if (!CellValueConverter.supports(field.getType())) {
            throw new IllegalArgumentException("Unsupported @SheetCol field type " + field.getType().getName() + ": " + where);
        }
        String letters = sheetCol.value().trim().toUpperCase(Locale.ROOT);
        if (!COLUMN.matcher(letters).matches()) {
            throw new IllegalArgumentException("Invalid column '" + sheetCol.value() + "' on " + where);
        }
        String label = sheetCol.label().isBlank() ? field.getName() : sheetCol.label().trim();
        field.setAccessible(true);
        return new Column(field, letters, CellReference.convertColStringToIndex(letters), label);
    }

    private static <T> Constructor<T> noArgConstructor(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getName() + " must have a no-argument constructor", e);
        }
    }

    record Column(Field field, String letters, int index, String label) {

        String fieldName() {
            return field.getName();
        }

        Class<?> type() {
            return field.getType();
        }

        String cellRef(int rowNumber) {
            return letters + rowNumber;
        }

        Object get(Object source) {
            try {
                return field.get(source);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read " + field, e);
            }
        }

        void set(Object target, Object value) {
            if (value == null && field.getType().isPrimitive()) {
                return; // keep the primitive default
            }
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set " + field, e);
            }
        }
    }
}
