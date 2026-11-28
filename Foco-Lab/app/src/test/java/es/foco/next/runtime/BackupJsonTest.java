package es.foco.next.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import es.foco.next.core.backup.DayReport;
import es.foco.next.log.LogDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

/** v47: lectura de las respuestas de NextDNS y del JSON del día para recuperar desde Drive. */
public class BackupJsonTest {
    @Test
    public void leeServiciosYListaDeBloqueo() throws Exception {
        Map<String, Boolean> m =
                NextDnsApi.actives(
                        "{\"data\":[{\"id\":\"tiktok\",\"active\":true},{\"id\":\"Facebook\",\"active\":false}]}");
        assertEquals(Boolean.TRUE, m.get("tiktok"));
        assertEquals(Boolean.FALSE, m.get("facebook"));
        long[] s =
                NextDnsApi.status(
                        "{\"data\":[{\"status\":\"default\",\"queries\":90},{\"status\":\"blocked\",\"queries\":10}]}");
        assertEquals(100, s[0]);
        assertEquals(10, s[1]);
        List<DayReport.Item> d =
                NextDnsApi.domains(
                        "{\"data\":[{\"domain\":\"a.b.com\",\"root\":\"b.com\",\"queries\":3},{\"domain\":\"c.com\",\"queries\":1}]}");
        assertEquals("b.com", d.get(0).name);
        assertEquals("c.com", d.get(1).name);
    }

    @Test
    public void elJsonDelDiaSeRecuperaComoResumen() throws Exception {
        List<DayReport.Row> rows = new ArrayList<>();
        rows.add(new DayReport.Row("uso_productivo", "com.duolingo", 9, 20 * 60_000L, 1, 0, ""));
        rows.add(new DayReport.Row("ocio", "com.instagram.android", 21, 15 * 60_000L, 1, 0, ""));
        rows.add(new DayReport.Row("reto_completado", "", 10, 0, 0, 1, ""));
        DayReport day =
                DayReport.build(
                        LocalDate.of(2026, 11, 28), rows, false, p -> p, "NORMAL", 28, false);
        List<LogDatabase.InspectorRow> out = new ArrayList<>();
        assertTrue(DriveRestore.parse(new JSONObject(day.toJson(false)), out));
        assertEquals(3, out.size());
        assertEquals("uso_productivo", out.get(0).type);
        assertEquals(20 * 60_000L, out.get(0).ms);
        assertEquals("com.instagram.android", out.get(1).pkg);
        assertFalse(DriveRestore.parse(new JSONObject("{\"hola\":1}"), new ArrayList<>()));
    }
}
