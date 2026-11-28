package es.foco.next.core.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DayReportTest {
    static final String KEY = "abcdef1234567890ABCDEF";

    static List<DayReport.Row> sample() {
        List<DayReport.Row> r = new ArrayList<>();
        r.add(new DayReport.Row("uso_productivo", "com.duolingo", 9, 30 * 60_000L, 2, 0, ""));
        r.add(new DayReport.Row("uso_productivo", "com.duolingo", 10, 15 * 60_000L, 1, 0, ""));
        r.add(new DayReport.Row("ocio", "com.zhiliaoapp.musically", 21, 40 * 60_000L, 3, 0, ""));
        r.add(new DayReport.Row("ocio", "com.instagram.android", 22, 20 * 60_000L, 1, 0, ""));
        r.add(new DayReport.Row("ocio_premio", "com.instagram.android", 22, 5 * 60_000L, 1, 0, ""));
        r.add(new DayReport.Row("reto_completado", "", 11, 0, 0, 5, "memoria"));
        r.add(new DayReport.Row("minutos_ganados", "", 11, 0, 0, 10, "reto"));
        r.add(new DayReport.Row("minutos_gastados", "", 21, 0, 0, 6, "ocio"));
        r.add(new DayReport.Row("bloqueo_por_limite", "com.zhiliaoapp.musically", 22, 0, 1, 0, ""));
        r.add(new DayReport.Row("intento_panel_nextdns", "", 23, 0, 1, 0, ""));
        r.add(new DayReport.Row("incidente", "", 23, 0, 1, 0, "vpn"));
        r.add(new DayReport.Row("modo_cambiado", "", 14, 0, 0, 0, "de=NORMAL;a=ENFOQUE;motivo=x"));
        r.add(new DayReport.Row("modo_cambiado", "", 16, 0, 0, 0, "de=ENFOQUE;a=NORMAL;motivo=fin"));
        r.add(new DayReport.Row("animo", "", 8, 0, 1, 4, "inicio:bien"));
        return r;
    }

    static DayReport day(boolean mood) {
        DayReport d =
                DayReport.build(
                        LocalDate.of(2026, 11, 28), sample(), false, p -> p, "NORMAL", 28, mood);
        d.complete = true;
        d.generated = "2026-11-28T23:30:00";
        d.streak = 4;
        List<DayReport.Item> trackers = new ArrayList<>();
        trackers.add(new DayReport.Item("app-measurement.com", 90));
        d.nextDns =
                new DayReport.NextDns(
                        1000,
                        125,
                        trackers,
                        NextDnsPlan.socialActivity(List.of(new DayReport.Item("tiktok.com", 7))));
        return d;
    }

    @Test
    public void elJsonDeUnDiaTieneTodosLosCampos() {
        String j = day(false).toJson(false);
        for (String k :
                new String[] {
                    "\"formato\":1",
                    "\"fecha\":\"2026-11-28\"",
                    "\"invertido_min\":45",
                    "\"retos\":1",
                    "\"apps_productivas\":[{\"app\":\"com.duolingo\",\"min\":45,\"paquete\":\"com.duolingo\"}]",
                    "\"modos\":",
                    "\"ocio_por_app\":",
                    "\"ocio_por_hora\":[",
                    "\"bloqueos\":1",
                    "\"intentos\":1",
                    "\"incidentes\":1",
                    "\"minutos_ganados\":10",
                    "\"minutos_gastados\":6",
                    "\"nextdns\":{",
                    "\"consultas\":1000",
                    "\"bloqueadas\":125",
                    "\"pct_bloqueado\":12.5",
                    "\"top_rastreadores\":[{\"dominio\":\"app-measurement.com\",\"bloqueos\":90}]",
                    "\"actividad_redes\":{\"tiktok\":7"
                })
            assertTrue(k + " en " + j, j.contains(k));
        // Lo invertido va antes que el ocio.
        assertTrue(j.indexOf("invertido_min") < j.indexOf("ocio_min"));
    }

    @Test
    public void sinAnimoSiEstaApagado() {
        String j = day(false).toJson(false);
        assertFalse(j.contains("\"animo\""));
        assertFalse(j.contains("inicio:bien"));
        assertTrue(day(true).toJson(true).contains("\"animo\":{\"media\":4"));
    }

    @Test
    public void ocioPorHoraYPorApp() {
        DayReport d = day(false);
        assertEquals(24, d.leisureByHour.length);
        assertEquals(40, d.leisureByHour[21]);
        assertEquals(25, d.leisureByHour[22]);
        assertEquals(65, d.leisureMin());
        assertEquals("com.zhiliaoapp.musically", d.leisureApps.get(0).name);
    }

    @Test
    public void conAccesoDeUsoElTotalLoDaElSistema() {
        DayReport d =
                DayReport.build(
                        LocalDate.of(2026, 11, 28), sample(), true, p -> p, "NORMAL", 28, false);
        // 60 min del sistema; los 5 de premio ya están dentro.
        assertEquals(60, d.leisureMin());
    }

    @Test
    public void horasDeCadaModo() {
        Map<String, Double> h = day(false).modeHours;
        assertEquals(2.0, h.get("ENFOQUE"), 0.001);
        assertEquals(22.0, h.get("NORMAL"), 0.001);
        assertEquals("NORMAL", day(false).mainMode());
    }

    @Test
    public void laClaveNuncaApareceEnLoExportado() {
        List<DayReport.Row> rows = sample();
        // Aunque la clave llegara a un detalle por error, lo exportado la oculta.
        rows.add(new DayReport.Row("incidente", "", 23, 0, 1, 0, "fallo X-Api-Key: " + KEY));
        DayReport d = DayReport.build(LocalDate.of(2026, 11, 28), rows, false, p -> p, "", 28, false);
        String out = Secrets.redact(d.toJson(false), KEY);
        assertFalse(out.contains(KEY));
        assertFalse(Secrets.leaks(out, KEY));
        StringBuilder sheet = new StringBuilder();
        for (List<Object> r : SheetLayout.rowsFor(SheetLayout.EVENTOS, d))
            sheet.append(Secrets.redact(r.toString(), KEY));
        assertFalse(sheet.toString().contains(KEY));
        assertFalse(Secrets.redact("Authorization: Bearer ya29.abcDEF-123", KEY).contains("ya29"));
    }
}
