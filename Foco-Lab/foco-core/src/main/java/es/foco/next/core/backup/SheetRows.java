package es.foco.next.core.backup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v47: reenviar un día no duplica nada. Antes de escribir las filas de un día se borran las que ya
 * tenía (en cada pestaña la columna A es la fecha). En Drive, si el archivo ya existe se reemplaza
 * su contenido.
 */
public final class SheetRows {
    private SheetRows() {}

    /**
     * Tramos de filas a borrar (índice 0 = encabezado; [inicio, fin) ) cuya columna A cumple la
     * condición. Del último al primero, para que borrar uno no mueva los siguientes.
     */
    static List<int[]> ranges(List<String> columnA, java.util.function.Predicate<String> match) {
        List<int[]> out = new ArrayList<>();
        if (columnA == null) return out;
        int start = -1;
        for (int i = 1; i <= columnA.size(); i++) {
            boolean m = i < columnA.size() && match.test(norm(columnA.get(i)));
            if (m && start < 0) start = i;
            if (!m && start >= 0) {
                out.add(new int[] {start, i});
                start = -1;
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** Filas que ya tiene ese día (para borrarlas antes de volver a escribirlo). */
    public static List<int[]> rowsOf(List<String> columnA, String day) {
        return ranges(columnA, day::equals);
    }

    /** Filas anteriores a {@code firstKept} (pestaña Eventos: solo los últimos 30 días). */
    public static List<int[]> olderThan(List<String> columnA, String firstKept) {
        return ranges(columnA, d -> looksLikeDay(d) && d.compareTo(firstKept) < 0);
    }

    /** La hoja puede devolver la fecha como "2026-11-28" o con hora; se compara solo AAAA-MM-DD. */
    static String norm(String v) {
        if (v == null) return "";
        String s = v.trim();
        // v47b: una fecha sin formato llega como número de serie de la hoja (46353 = 2026-11-27).
        if (s.matches("\\d{5}(\\.\\d+)?")) {
            long n = (long) Double.parseDouble(s);
            if (n >= 20_000 && n <= 80_000)
                return java.time.LocalDate.of(1899, 12, 30).plusDays(n).toString();
        }
        return s.length() >= 10 && looksLikeDay(s.substring(0, 10)) ? s.substring(0, 10) : s;
    }

    static boolean looksLikeDay(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * Drive: de los archivos con ese nombre en la carpeta, cuál se reemplaza (el primero) y cuáles
     * sobran (duplicados de un fallo anterior, se borran). {@code files}: id → nombre.
     */
    public static String[] replaceTarget(Map<String, String> files, String name) {
        List<String> same = new ArrayList<>();
        if (files != null)
            for (Map.Entry<String, String> e : files.entrySet())
                if (name.equals(e.getValue())) same.add(e.getKey());
        if (same.isEmpty()) return new String[0];
        return same.toArray(new String[0]);
    }
}
