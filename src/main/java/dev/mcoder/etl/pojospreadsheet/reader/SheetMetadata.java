package dev.mcoder.etl.pojospreadsheet.reader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.mcoder.etl.pojospreadsheet.annotation.SheetCol;
import dev.mcoder.etl.pojospreadsheet.reader.SheetMapping.Column;

/**
 * Describes how a POJO maps to spreadsheet columns, for example to build a header row or
 * document an import template.
 */
public final class SheetMetadata {

    private SheetMetadata() {
    }

    /**
     * The {@link SheetCol} fields of {@code type}, including inherited ones, ordered by column
     * (A, B, ..., Z, AA, ...).
     *
     * @throws IllegalArgumentException if {@code type} cannot be mapped, for the same reasons
     *                                  {@link SpreadsheetReader} would reject it
     */
    public static List<ColumnMetadata> columns(Class<?> type) {
        List<Column> columns = new ArrayList<>();
        SheetMapping.of(type).columns().forEach(columns::add);
        return columns.stream()
                .sorted(Comparator.comparingInt(Column::index))
                .map(column -> new ColumnMetadata(column.letters(), column.fieldName(), column.label()))
                .toList();
    }
}
