package dev.mcoder.etl.pojospreadsheet.io;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
 *
 * <p>A class is built by calling its no-argument constructor, then setting each mapped field. A
 * record is built by calling its canonical constructor with every component's value; components
 * without {@code @SheetCol} get {@code null}, or the default value for a primitive.
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
    // Records only: the canonical constructor argument each column fills, in columns() order
    private final int[] argumentIndexes;

    private SheetMapping(Constructor<T> constructor, Map<String, Column> columnsByField, int[] argumentIndexes) {
        this.constructor = constructor;
        this.columnsByField = columnsByField;
        this.argumentIndexes = argumentIndexes;
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
        return type.isRecord() ? createForRecord(type) : createForClass(type);
    }

    private static <T> SheetMapping<T> createForClass(Class<T> type) {
        Map<String, Column> columns = new LinkedHashMap<>();
        for (Class<?> current : hierarchy(type)) {
            for (Field field : current.getDeclaredFields()) {
                SheetCol sheetCol = field.getAnnotation(SheetCol.class);
                if (sheetCol != null) {
                    String where = type.getName() + "." + field.getName();
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                        throw new IllegalArgumentException("@SheetCol field must not be static or final: " + where);
                    }
                    columns.put(field.getName(), column(where, field, sheetCol));
                }
            }
        }
        requireColumns(type, columns);
        return new SheetMapping<>(noArgConstructor(type), Collections.unmodifiableMap(columns), null);
    }

    private static <T> SheetMapping<T> createForRecord(Class<T> type) {
        // @SheetCol targets fields, so on a record component it lands on the component's private field
        RecordComponent[] components = type.getRecordComponents();
        Map<String, Column> columns = new LinkedHashMap<>();
        List<Integer> argumentIndexes = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            Field field = componentField(type, components[i]);
            SheetCol sheetCol = field.getAnnotation(SheetCol.class);
            if (sheetCol != null) {
                columns.put(field.getName(), column(type.getName() + "." + field.getName(), field, sheetCol));
                argumentIndexes.add(i);
            }
        }
        requireColumns(type, columns);
        return new SheetMapping<>(canonicalConstructor(type, components), Collections.unmodifiableMap(columns),
                argumentIndexes.stream().mapToInt(Integer::intValue).toArray());
    }

    Iterable<Column> columns() {
        return columnsByField.values();
    }

    int columnCount() {
        return columnsByField.size();
    }

    /**
     * The column mapped to {@code fieldName}, or {@code null} if that field has no {@link SheetCol}.
     */
    Column column(String fieldName) {
        return fieldName == null ? null : columnsByField.get(fieldName);
    }

    /**
     * Builds a POJO from one row's values, given in {@link #columns()} order. A {@code null}
     * value leaves a primitive at its default.
     *
     * @throws ConstructorException if a record's constructor rejects the values
     */
    T newInstance(Object[] values) throws ConstructorException {
        if (argumentIndexes == null) {
            T pojo = invoke(constructor);
            int i = 0;
            for (Column column : columns()) {
                column.set(pojo, values[i++]);
            }
            return pojo;
        }
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int p = 0; p < parameterTypes.length; p++) {
            arguments[p] = defaultValue(parameterTypes[p]);
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                arguments[argumentIndexes[i]] = values[i];
            }
        }
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            // A compact constructor that validates its arguments; the values come from the sheet
            throw new ConstructorException(e.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private static <T> T invoke(Constructor<T> constructor) {
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    private static List<Class<?>> hierarchy(Class<?> type) {
        // Superclass fields first, so columns keep declaration order from the top down
        Deque<Class<?>> classes = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            classes.addFirst(current);
        }
        return List.copyOf(classes);
    }

    private static void requireColumns(Class<?> type, Map<String, Column> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " has no @SheetCol fields");
        }
    }

    private static Column column(String where, Field field, SheetCol sheetCol) {
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

    private static Field componentField(Class<?> type, RecordComponent component) {
        try {
            return type.getDeclaredField(component.getName());
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("No field for record component " + component, e);
        }
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

    private static <T> Constructor<T> canonicalConstructor(Class<T> type, RecordComponent[] components) {
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No canonical constructor on record " + type.getName(), e);
        }
    }

    /**
     * A record's constructor threw an exception for one row's values.
     */
    static final class ConstructorException extends Exception {

        ConstructorException(Throwable cause) {
            super(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(), cause);
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
