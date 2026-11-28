package es.foco.next.core.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SheetRowsTest {
    @Test
    public void reenviarUnDiaNoDuplicaFilas() {
        List<String> a = new ArrayList<>(List.of("Fecha", "2026-11-26", "2026-11-27", "2026-11-27", "2026-11-28"));
        List<int[]> del = SheetRows.rowsOf(a, "2026-11-27");
        assertEquals(1, del.size());
        assertArrayEquals(new int[] {2, 4}, del.get(0));
        // Simula borrar y volver a añadir: el día queda una sola vez.
        for (int[] r : del) for (int i = r[1] - 1; i >= r[0]; i--) a.remove(i);
        a.add("2026-11-27");
        assertEquals(1, a.stream().filter("2026-11-27"::equals).count());
        assertTrue(SheetRows.rowsOf(List.of("Fecha"), "2026-11-27").isEmpty());
        // La hoja puede devolver la fecha con hora.
        assertEquals(1, SheetRows.rowsOf(List.of("Fecha", "2026-11-27 00:00:00"), "2026-11-27").size());
    }

    @Test
    public void tramosDelUltimoAlPrimero() {
        List<int[]> del = SheetRows.olderThan(List.of("Fecha", "2026-10-01", "2026-10-02", "2026-11-01"), "2026-10-29");
        assertArrayEquals(new int[] {1, 3}, del.get(0));
        List<int[]> two = SheetRows.rowsOf(List.of("Fecha", "x", "d", "y", "d"), "d");
        assertArrayEquals(new int[] {4, 5}, two.get(0));
        assertArrayEquals(new int[] {2, 3}, two.get(1));
    }

    @Test
    public void reenviarReemplazaElArchivo() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("id1", "2026-11-27.json");
        files.put("id2", "2026-11-27.png");
        files.put("id3", "2026-11-27.json");
        assertArrayEquals(new String[] {"id1", "id3"}, SheetRows.replaceTarget(files, "2026-11-27.json"));
        assertEquals(0, SheetRows.replaceTarget(files, "2026-11-28.json").length);
    }

    @Test
    public void elPanelYLaHojaUsanVerdes() {
        String design = SheetLayout.designBody(false);
        assertTrue(design.contains("addChart"));
        assertTrue(design.contains("addBanding"));
        assertTrue(design.contains("gradientRule"));
        assertEquals(4, design.split("\"addChart\"").length - 1);
        String create = SheetLayout.createBody("America/Mexico_City", false);
        assertTrue(create.contains("Foco · Historial"));
        assertTrue(!create.contains("Ánimo"));
        assertTrue(SheetLayout.createBody("UTC", true).contains("Ánimo"));
        assertTrue(SheetLayout.headersAndPanelBody(false).contains("VLOOKUP"));
    }
}
