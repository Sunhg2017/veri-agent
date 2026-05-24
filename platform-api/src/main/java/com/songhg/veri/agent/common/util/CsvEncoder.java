package com.songhg.veri.agent.common.util;

import java.util.List;

/**
 * Shared CSV encoding utilities.
 *
 * <p>Eliminates duplicated appendCsvValue/appendCsvLine logic that previously
 * lived in multiple service classes.
 */
public final class CsvEncoder {

    private CsvEncoder() {
    }

    /**
     * Appends one CSV value, properly quoting and escaping, followed by a comma.
     */
    public static void appendValue(StringBuilder csv, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            csv.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            csv.append(text);
        }
        csv.append(',');
    }

    /**
     * Appends a full CSV row and terminates it with a newline.
     */
    public static void appendLine(StringBuilder csv, Object... values) {
        for (Object value : values) {
            appendValue(csv, value);
        }
        csv.setCharAt(csv.length() - 1, '\n');
    }

    /**
     * Encodes a header row followed by data rows.
     */
    public static StringBuilder encode(String headerLine, List<?> rows, RowEncoder<?> rowEncoder) {
        StringBuilder csv = new StringBuilder();
        csv.append(headerLine).append('\n');
        for (Object row : rows) {
            rowEncoder.encode(csv, row);
            csv.setCharAt(csv.length() - 1, '\n');
        }
        return csv;
    }

    @FunctionalInterface
    public interface RowEncoder<T> {
        void encode(StringBuilder csv, T row);
    }
}
