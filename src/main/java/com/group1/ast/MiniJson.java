package com.group1.ast;

import java.util.Map;

final class MiniJson {
    private MiniJson() {
    }

    static String write(Object value, boolean pretty) {
        StringBuilder out = new StringBuilder();
        append(out, value, pretty, 0);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence) {
            quote(out, value.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map) {
            appendMap(out, (Map<?, ?>) value, pretty, depth);
        } else if (value instanceof Iterable) {
            appendList(out, (Iterable<?>) value, pretty, depth);
        } else {
            quote(out, value.toString());
        }
    }

    private static void appendMap(StringBuilder out, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, pretty, depth + 1);
            quote(out, String.valueOf(entry.getKey()));
            out.append(pretty ? ": " : ":");
            append(out, entry.getValue(), pretty, depth + 1);
        }
        newline(out, pretty, depth);
        out.append('}');
    }

    private static void appendList(StringBuilder out, Iterable<?> list, boolean pretty, int depth) {
        boolean first = true;
        out.append('[');
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, pretty, depth + 1);
            append(out, item, pretty, depth + 1);
        }
        if (!first) {
            newline(out, pretty, depth);
        }
        out.append(']');
    }

    private static void newline(StringBuilder out, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
