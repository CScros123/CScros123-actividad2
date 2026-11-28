package es.foco.next.core.backup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v47: JSON mínimo para escribir (sin librerías: foco-core es Java puro). Objetos con orden de
 * inserción, listas, textos, números, booleanos y null. Solo escribe; leer lo hace la app con
 * org.json.
 */
public final class Json {
    private Json() {}

    public static final class Obj {
        final Map<String, Object> map = new LinkedHashMap<>();

        public Obj put(String key, Object value) {
            map.put(key, value);
            return this;
        }

        public boolean has(String key) {
            return map.containsKey(key);
        }

        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            write(b, this);
            return b.toString();
        }
    }

    public static final class Arr {
        final List<Object> list = new ArrayList<>();

        public Arr add(Object value) {
            list.add(value);
            return this;
        }

        public int size() {
            return list.size();
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            write(b, this);
            return b.toString();
        }
    }

    public static Obj obj() {
        return new Obj();
    }

    public static Arr arr(Object... values) {
        Arr a = new Arr();
        for (Object v : values) a.add(v);
        return a;
    }

    public static String quote(String s) {
        StringBuilder b = new StringBuilder();
        str(b, s);
        return b.toString();
    }

    static void write(StringBuilder b, Object v) {
        if (v == null) b.append("null");
        else if (v instanceof Obj) {
            b.append('{');
            Iterator<Map.Entry<String, Object>> it = ((Obj) v).map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                str(b, e.getKey());
                b.append(':');
                write(b, e.getValue());
                if (it.hasNext()) b.append(',');
            }
            b.append('}');
        } else if (v instanceof Arr) writeList(b, ((Arr) v).list);
        else if (v instanceof List) writeList(b, (List<?>) v);
        else if (v instanceof Map) {
            Obj o = new Obj();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
                o.put(String.valueOf(e.getKey()), e.getValue());
            write(b, o);
        } else if (v instanceof long[]) {
            b.append('[');
            long[] a = (long[]) v;
            for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ",").append(a[i]);
            b.append(']');
        } else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (v instanceof Number) b.append(((Number) v).longValue());
        else str(b, String.valueOf(v));
    }

    private static void writeList(StringBuilder b, List<?> list) {
        b.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            write(b, list.get(i));
        }
        b.append(']');
    }

    private static void str(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
