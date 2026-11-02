package io.github.jageobyong358.limbusarchive.unity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON: parse to Map / List / String / Double / Boolean / null, and write Java values back. */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String s) {
        Json j = new Json(s);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != s.length()) throw new IllegalArgumentException("trailing JSON at " + j.i);
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        char c = s.charAt(i);
        switch (c) {
            case '{': {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = str();
                    ws();
                    expect(':');
                    ws();
                    m.put(k, value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    expect('}');
                    return m;
                }
            }
            case '[': {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    ws();
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    expect(']');
                    return l;
                }
            }
            case '"':
                return str();
            case 't':
                i += 4;
                return Boolean.TRUE;
            case 'f':
                i += 5;
                return Boolean.FALSE;
            case 'n':
                i += 4;
                return null;
            default: {
                int st = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
                return Double.parseDouble(s.substring(st, i));
            }
        }
    }

    private void expect(char c) {
        if (s.charAt(i) != c) throw new IllegalArgumentException("expected " + c + " at " + i);
        i++;
    }

    private String str() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    // ---- writing ----

    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, -1, 0);
        return b.toString();
    }

    public static String pretty(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 1, 0);
        return b.toString();
    }

    private static void nl(StringBuilder b, int indent, int depth) {
        if (indent < 0) return;
        b.append('\n');
        for (int k = 0; k < indent * depth; k++) b.append(' ');
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int indent, int depth) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String || v instanceof UStr) {
            quote(b, v.toString());
        } else if (v instanceof Boolean) {
            b.append(v);
        } else if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) quote(b, String.valueOf(d));
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(v instanceof Float ? Float.toString((Float) v) : Double.toString(d));
        } else if (v instanceof Number) {
            b.append(v);
        } else if (v instanceof byte[]) {
            quote(b, "<" + ((byte[]) v).length + " bytes>");
        } else if (v instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) v;
            b.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                nl(b, indent, depth + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(indent < 0 ? ":" : ": ");
                write(b, e.getValue(), indent, depth + 1);
            }
            if (!m.isEmpty()) nl(b, indent, depth);
            b.append('}');
        } else if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            b.append('[');
            int n = l.size(), shown = Math.min(n, 2000);
            for (int k = 0; k < shown; k++) {
                if (k > 0) b.append(',');
                nl(b, indent, depth + 1);
                write(b, l.get(k), indent, depth + 1);
            }
            if (n > shown) {
                b.append(',');
                nl(b, indent, depth + 1);
                quote(b, "… " + (n - shown) + " more");
            }
            if (n > 0) nl(b, indent, depth);
            b.append(']');
        } else if (v instanceof Object[]) {
            write(b, java.util.Arrays.asList((Object[]) v), indent, depth);
        } else if (v.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(v);
            List<Object> l = new ArrayList<>(Math.min(n, 2001));
            for (int k = 0; k < Math.min(n, 2000); k++) l.add(java.lang.reflect.Array.get(v, k));
            if (n > 2000) l.add("… " + (n - 2000) + " more");
            write(b, l, indent, depth);
        } else {
            quote(b, v.toString());
        }
    }

    public static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
