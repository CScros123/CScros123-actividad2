package es.foco.next.core.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import es.foco.next.core.care.CareMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class NextDnsPlanTest {
    static final ZoneId Z = ZoneId.of("America/Mexico_City");

    static Map<String, Boolean> telegram() {
        Map<String, Boolean> m = new HashMap<>();
        for (String d : NextDnsPlan.TELEGRAM) m.put(d, true);
        return m;
    }

    static Map<String, Boolean> services(boolean active) {
        Map<String, Boolean> m = new HashMap<>();
        for (String s : NextDnsPlan.SERVICES) m.put(s, active);
        return m;
    }

    static long count(List<NextDnsPlan.Op> ops, NextDnsPlan.Op.Kind k, String list) {
        return ops.stream().filter(o -> o.kind == k && o.list.equals(list)).count();
    }

    @Test
    public void entrarAEnfoqueActivaLosServiciosYSalirLosQuita() {
        boolean on = NextDnsPlan.blockServices(CareMode.Modo.ENFOQUE, false);
        assertTrue(on);
        List<NextDnsPlan.Op> enter = NextDnsPlan.diff(on, new HashMap<>(), telegram());
        assertEquals(8, count(enter, NextDnsPlan.Op.Kind.ADD, NextDnsPlan.SERVICES_PATH));
        boolean off = NextDnsPlan.blockServices(CareMode.Modo.NORMAL, false);
        assertFalse(off);
        List<NextDnsPlan.Op> leave = NextDnsPlan.diff(off, services(true), telegram());
        assertEquals(8, count(leave, NextDnsPlan.Op.Kind.REMOVE, NextDnsPlan.SERVICES_PATH));
        assertEquals(0, count(leave, NextDnsPlan.Op.Kind.ADD, NextDnsPlan.SERVICES_PATH));
        // Cuidado tampoco bloquea por servicio (de día).
        assertFalse(NextDnsPlan.blockServices(CareMode.Modo.CUIDADO, false));
        // Ya está como debe: nada que hacer.
        assertTrue(NextDnsPlan.diff(true, services(true), telegram()).isEmpty());
    }

    @Test
    public void alasNueveSeActivanYALasSieteSeQuitan() {
        long at2059 = ZonedDateTime.of(2026, 11, 28, 20, 59, 0, 0, Z).toInstant().toEpochMilli();
        long at21 = ZonedDateTime.of(2026, 11, 28, 21, 0, 0, 0, Z).toInstant().toEpochMilli();
        long at659 = ZonedDateTime.of(2026, 11, 29, 6, 59, 0, 0, Z).toInstant().toEpochMilli();
        long at7 = ZonedDateTime.of(2026, 11, 29, 7, 0, 0, 0, Z).toInstant().toEpochMilli();
        assertFalse(NextDnsPlan.night(at2059, Z, 21, 7));
        assertTrue(NextDnsPlan.night(at21, Z, 21, 7));
        assertTrue(NextDnsPlan.night(at659, Z, 21, 7));
        assertFalse(NextDnsPlan.night(at7, Z, 21, 7));
        // De noche bloquea en cualquier modo.
        assertTrue(NextDnsPlan.blockServices(CareMode.Modo.CUIDADO, true));
        // El siguiente cambio se programa justo a las 21:00 y luego a las 07:00.
        ZonedDateTime evening = ZonedDateTime.of(2026, 11, 28, 18, 10, 0, 0, Z);
        assertEquals(at21, NextDnsPlan.nextBoundary(evening, -1, 21, 7));
        assertEquals(
                at7,
                NextDnsPlan.nextBoundary(
                        ZonedDateTime.of(2026, 11, 28, 21, 0, 0, 0, Z), -1, 21, 7));
        // Un Enfoque que acaba antes manda.
        long focusEnd = evening.plusMinutes(50).toInstant().toEpochMilli();
        assertEquals(focusEnd, NextDnsPlan.nextBoundary(evening, focusEnd, 21, 7));
    }

    @Test
    public void siLoCambiaronDesdeLaWebSeRestauraYSeRegistra() {
        String applied = NextDnsPlan.signature(true);
        Map<String, Boolean> s = services(true);
        s.put("tiktok", false); // alguien lo apagó en my.nextdns.io
        List<NextDnsPlan.Op> ops = NextDnsPlan.diff(true, s, telegram());
        assertEquals(2, ops.size()); // quitar el inactivo y volver a ponerlo activo
        assertEquals(NextDnsPlan.Op.Kind.REMOVE, ops.get(0).kind);
        assertEquals("tiktok", ops.get(1).id);
        assertTrue(NextDnsPlan.changedOutside(ops, applied, true));
        // Un cambio de modo no es un cambio desde fuera.
        assertFalse(
                NextDnsPlan.changedOutside(
                        NextDnsPlan.diff(false, services(true), telegram()), applied, false));
    }

    @Test
    public void telegramSiempreEnLaListaDeBloqueo() {
        List<NextDnsPlan.Op> ops = NextDnsPlan.diff(false, new HashMap<>(), new HashMap<>());
        assertEquals(4, count(ops, NextDnsPlan.Op.Kind.ADD, NextDnsPlan.DENYLIST_PATH));
        Map<String, Boolean> deny = telegram();
        deny.put("t.me", false);
        assertEquals(2, NextDnsPlan.diff(false, new HashMap<>(), deny).size());
    }

    @Test
    public void rastreadoresPorDominioRaiz() {
        List<DayReport.Item> top =
                NextDnsPlan.topRoots(
                        List.of(
                                new DayReport.Item("a.doubleclick.net", 5),
                                new DayReport.Item("b.doubleclick.net", 7),
                                new DayReport.Item("x.bbc.co.uk", 3)),
                        15);
        assertEquals("doubleclick.net", top.get(0).name);
        assertEquals(12, top.get(0).value);
        assertEquals("bbc.co.uk", top.get(1).name);
        assertEquals("youtube", NextDnsPlan.network("r3.googlevideo.com"));
        assertEquals(null, NextDnsPlan.network("netflix.com"));
    }
}
