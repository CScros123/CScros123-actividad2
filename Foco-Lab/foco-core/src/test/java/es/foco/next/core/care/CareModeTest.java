package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.home.Companion;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;

public class CareModeTest {
    private static final ZoneId Z = ZoneId.of("America/Mexico_City");
    private static final long MIN = 60_000L;

    private static ZonedDateTime at(int h, int m) {
        return ZonedDateTime.of(2026, 11, 23, h, m, 0, 0, Z);
    }

    @After
    public void resetNight() {
        AbsoluteRules.useNight(21, 7);
    }

    // ---- Pruebas pedidas en la guía ----

    @Test
    public void normalPasarLosCincoMinutosNoBloqueaYAvisaComoMucho3PorHora() {
        assertEquals(CareMode.Leisure.REMIND, CareMode.leisure(CareMode.Modo.NORMAL, false, true));
        assertFalse(CareMode.blocksAtLimit(CareMode.Modo.NORMAL));
        assertFalse(CareMode.usesBank(CareMode.Modo.NORMAL));
        long now = at(15, 0).toInstant().toEpochMilli();
        // Límite de Companion (el que usa el notificador): 3 en la última hora y nunca dos en 5 min.
        Companion.Budget b = new Companion.Budget();
        assertTrue(Companion.allowed(Companion.Trigger.LEISURE_OVER, b, now, false, false, false));
        b.leisureRemindersLastHour.addAll(Arrays.asList(now - 50 * MIN, now - 30 * MIN, now - 10 * MIN));
        b.lastLeisureReminder = now - 10 * MIN;
        assertFalse(Companion.allowed(Companion.Trigger.LEISURE_OVER, b, now, false, false, false));
        // Pasada la hora del primero, cabe otro.
        assertTrue(Companion.allowed(Companion.Trigger.LEISURE_OVER, b, now + 11 * MIN, false, false, false));
        // El mismo límite en CareMode.
        assertFalse(CareMode.normalReminderAllowed(now, Arrays.asList(now - 50 * MIN, now - 30 * MIN, now - 10 * MIN)));
        assertTrue(CareMode.normalReminderAllowed(now, Arrays.asList(now - 70 * MIN, now - 30 * MIN, now - 10 * MIN)));
    }

    @Test
    public void enfoqueDeUnaHoraNoVuelveANormalAntesPeroSiACuidado() {
        CareMode.Change ch = CareMode.toFocus(CareMode.State.inicial(), CareMode.Duracion.UNA_HORA, at(15, 0), 21);
        assertNotNull(ch);
        CareMode.State s = ch.next;
        long t0 = at(15, 0).toInstant().toEpochMilli();
        assertEquals(CareMode.Modo.ENFOQUE, CareMode.effective(s, t0 + 30 * MIN));
        assertFalse(CareMode.canSwitch(s, CareMode.Modo.NORMAL, t0 + 59 * MIN));
        assertNull(CareMode.to(s, CareMode.Modo.NORMAL, t0 + 59 * MIN, "elegido"));
        CareMode.Change care = CareMode.to(s, CareMode.Modo.CUIDADO, t0 + 10 * MIN, "elegido");
        assertNotNull(care);
        assertEquals(CareMode.Modo.CUIDADO, care.next.modo);
        assertEquals("de=ENFOQUE;a=CUIDADO;motivo=elegido", care.detail());
        // Al vencer vuelve al modo anterior (NORMAL) y ya se puede elegir NORMAL.
        assertEquals(CareMode.Modo.NORMAL, CareMode.effective(s, t0 + 60 * MIN));
        assertTrue(CareMode.canSwitch(s, CareMode.Modo.NORMAL, t0 + 60 * MIN));
        CareMode.Change back = CareMode.settle(s, t0 + 61 * MIN);
        assertNotNull(back);
        assertEquals("fin_enfoque", back.motivo);
    }

    @Test
    public void cuidadoALas22TikTokSigueBloqueadoPorLaNoche() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 23, 22, 0);
        boolean night = AbsoluteRules.night(t, true);
        assertTrue(night);
        assertEquals(CareMode.Leisure.NIGHT, CareMode.leisure(CareMode.Modo.CUIDADO, night, false));
        assertTrue(AbsoluteRules.social("com.zhiliaoapp.musically"));
        // De día, en CUIDADO, TikTok no se cuenta ni se limita.
        assertEquals(
                CareMode.Leisure.NOT_COUNTED,
                CareMode.leisure(CareMode.Modo.CUIDADO, AbsoluteRules.night(t.withHour(16), true), true));
        // Telegram, siempre.
        assertTrue(CareMode.alwaysBlocked("org.telegram.messenger"));
    }

    @Test
    public void cambiarElHorarioDeLaNocheQueAflojaSeAplicaAlDiaSiguiente() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        NightHours.Setting s = NightHours.Setting.initial();
        // Aflojar (empezar a las 22): mañana.
        NightHours.Setting loose = NightHours.request(s, new NightHours(22, 7), today);
        assertEquals(new NightHours(21, 7), NightHours.effective(loose, today));
        assertEquals(new NightHours(22, 7), NightHours.effective(loose, today.plusDays(1)));
        // Endurecer (empezar a las 20): al instante, y descarta lo pendiente.
        NightHours.Setting hard = NightHours.request(loose, new NightHours(20, 7), today);
        assertEquals(new NightHours(20, 7), NightHours.effective(hard, today));
        assertNull(hard.pending);
        // Cambio mixto (antes pero acabando antes): suelta horas, así que mañana.
        NightHours.Setting mixed = NightHours.request(s, new NightHours(20, 6), today);
        assertEquals(new NightHours(21, 7), NightHours.effective(mixed, today));
        // Fuera de límites: sin cambios.
        assertEquals(new NightHours(21, 7), NightHours.effective(NightHours.request(s, new NightHours(2, 7), today), today.plusDays(2)));
        // AbsoluteRules usa el horario vigente.
        AbsoluteRules.useNight(20, 7);
        assertTrue(AbsoluteRules.esNocheAbsoluta(LocalDateTime.of(2026, 11, 23, 20, 30)));
        AbsoluteRules.useNight(3, 7); // inválido: se ignora
        assertTrue(AbsoluteRules.esNocheAbsoluta(LocalDateTime.of(2026, 11, 23, 20, 30)));
    }

    // ---- Más ----

    @Test
    public void enfoqueHastaLaNocheYAmpliar() {
        assertEquals(-1, CareMode.focusEnd(CareMode.Duracion.HASTA_LA_NOCHE, at(20, 50), 21));
        assertTrue(CareMode.focusEnd(CareMode.Duracion.HASTA_LA_NOCHE, at(10, 0), 21) > 0);
        assertNull(CareMode.toFocus(CareMode.State.inicial(), CareMode.Duracion.HASTA_LA_NOCHE, at(22, 0), 21));
        CareMode.State s = CareMode.toFocus(CareMode.State.inicial(), CareMode.Duracion.DOS_HORAS, at(15, 0), 21).next;
        // Pedir 1 h dentro de un Enfoque de 2 h no lo acorta.
        CareMode.State s2 = CareMode.toFocus(s, CareMode.Duracion.UNA_HORA, at(15, 10), 21).next;
        assertEquals(s.enfoqueHasta, s2.enfoqueHasta);
        // Desde CUIDADO, al vencer vuelve a CUIDADO.
        CareMode.State fromCare = CareMode.toFocus(new CareMode.State(CareMode.Modo.CUIDADO, CareMode.Modo.CUIDADO, -1), CareMode.Duracion.UNA_HORA, at(15, 0), 21).next;
        assertEquals(CareMode.Modo.CUIDADO, CareMode.effective(fromCare, at(16, 1).toInstant().toEpochMilli()));
    }

    @Test
    public void cuidadoSugerenciaComoMuchoCada2Horas() {
        long now = 10 * 60 * MIN;
        assertFalse(CareMode.careSuggestionDue(now, -1, 10 * MIN));
        assertTrue(CareMode.careSuggestionDue(now, -1, 30 * MIN));
        assertFalse(CareMode.careSuggestionDue(now, now - 119 * MIN, 90 * MIN));
        assertTrue(CareMode.careSuggestionDue(now, now - 121 * MIN, 30 * MIN));
        assertFalse(CareMode.showNumbers(CareMode.Modo.CUIDADO, false));
        assertFalse(CareMode.showNumbers(CareMode.Modo.NORMAL, true));
        assertTrue(CareMode.showNumbers(CareMode.Modo.ENFOQUE, false));
    }

    @Test
    public void animoSugiereCuidadoConUnoODosYNuncaCambiaSolo() {
        assertTrue(Mood.suggestCare(1));
        assertTrue(Mood.suggestCare(2));
        assertFalse(Mood.suggestCare(3));
        assertFalse(Mood.suggestCare(0));
        assertEquals("cansado", Mood.word("  cansado hoy\n"));
        assertEquals("", Mood.word(null));
        assertTrue(Mood.good(4));
    }

    @Test
    public void loBuenoPrimeroSinNumeros() {
        java.util.Set<GoodApps.Kind> k = new java.util.HashSet<>();
        k.add(GoodApps.kind("com.duolingo", "Duolingo"));
        k.add(GoodApps.kind("com.sirma.mobile.bible.android", "Biblia"));
        String p = GoodApps.phrase(k, 1);
        assertEquals("Hoy leíste la Biblia, practicaste idiomas y superaste un reto.", p);
        assertFalse(p.matches(".*\\d.*"));
        assertTrue(GoodApps.good("com.anthropic.claude"));
        assertFalse(GoodApps.good("com.zhiliaoapp.musically"));
        assertFalse(GoodApps.phrase(new java.util.HashSet<>(), 0).matches(".*\\d.*"));
    }

    @Test
    public void cuidadoConTiempoVuelveSoloAlModoDeAntes() {
        long t0 = at(15, 0).toInstant().toEpochMilli();
        CareMode.Change ch = CareMode.toCare(CareMode.State.inicial(), 15, t0, 0, false, "elegido");
        assertNotNull(ch);
        assertEquals("elegido_15min", ch.motivo);
        assertEquals(CareMode.Modo.CUIDADO, CareMode.effective(ch.next, t0 + 14 * MIN));
        assertEquals(15, CareMode.careMinutesLeft(ch.next, t0));
        assertEquals(CareMode.Modo.NORMAL, CareMode.effective(ch.next, t0 + 15 * MIN));
        CareMode.Change back = CareMode.settle(ch.next, t0 + 16 * MIN);
        assertNotNull(back);
        assertEquals("fin_cuidado", back.motivo);
        assertEquals(CareMode.Modo.NORMAL, back.a);
    }

    @Test
    public void cuidadoDesdeEnfoqueVuelveAlEnfoqueSiLeQueda() {
        long t0 = at(15, 0).toInstant().toEpochMilli();
        CareMode.State focus = CareMode.toFocus(CareMode.State.inicial(), CareMode.Duracion.UNA_HORA, at(15, 0), 21).next;
        CareMode.State care = CareMode.toCare(focus, 15, t0 + 10 * MIN, 0, false, "elegido").next;
        assertEquals(CareMode.Modo.CUIDADO, CareMode.effective(care, t0 + 20 * MIN));
        // Desde un Cuidado que vuelve a un Enfoque activo no se puede saltar a Normal.
        assertFalse(CareMode.canSwitch(care, CareMode.Modo.NORMAL, t0 + 20 * MIN));
        // Al acabar el Cuidado (15:25) vuelve al Enfoque, que dura hasta las 16:00.
        assertEquals(CareMode.Modo.ENFOQUE, CareMode.effective(care, t0 + 26 * MIN));
        assertEquals(CareMode.Modo.NORMAL, CareMode.effective(care, t0 + 61 * MIN));
        // Cuidado de 3 h desde un Enfoque de 1 h: al acabar, el Enfoque ya venció → Normal.
        CareMode.State long3 = CareMode.toCare(focus, 180, t0 + 10 * MIN, 0, false, "elegido").next;
        assertEquals(CareMode.Modo.NORMAL, CareMode.effective(long3, t0 + 191 * MIN));
    }

    @Test
    public void topeDiarioDeDosHorasSalvoAnimoBajo() {
        long t0 = at(15, 0).toInstant().toEpochMilli();
        assertNull(CareMode.toCare(CareMode.State.inicial(), 15, t0, 2 * 60 * MIN, false, "elegido"));
        assertNotNull(CareMode.toCare(CareMode.State.inicial(), 15, t0, 2 * 60 * MIN, true, "animo_bajo"));
        // Quedan 10 min del tope: se conceden 10 aunque pida 1 h.
        CareMode.State s = CareMode.toCare(CareMode.State.inicial(), 60, t0, 110 * MIN, false, "elegido").next;
        assertEquals(10, CareMode.careMinutesLeft(s, t0));
        assertEquals(2 * 60 * MIN, CareMode.careBudgetLeftMs(0, false));
        // v32: con el ánimo bajo, 3 h (antes sin tope).
        assertEquals(60 * MIN, CareMode.careBudgetLeftMs(2 * 60 * MIN, true));
        assertEquals(0, CareMode.careBudgetLeftMs(5 * 60 * MIN, true));
        assertNull(CareMode.toCare(CareMode.State.inicial(), 15, t0, 3 * 60 * MIN, true, "animo_bajo"));
    }

    @Test
    public void pieDelBuhoConMetricasCompactas() {
        assertEquals("3 hábitos hoy · 5 min/h disponibles", CareMode.owlLine(3, CareMode.Modo.NORMAL, false, 7, 5, true));
        assertEquals("1 hábito hoy · ocio con tu hora libre de Foco Core", CareMode.owlLine(1, CareMode.Modo.ENFOQUE, false, 7, 2, true));
        assertEquals("empieza con algo pequeño · ocio cerrado hasta las 7:00", CareMode.owlLine(0, CareMode.Modo.NORMAL, true, 7, 0, true));
        String hidden = CareMode.owlLine(2, CareMode.Modo.CUIDADO, false, 7, 5, false);
        assertFalse(hidden, hidden.matches(".*\\d.*"));
    }

    @Test
    public void mensajesDeCuidadoSinCulpa() {
        for (Companion.Category c : Companion.Category.values()) {
            assertTrue(c + " tiene al menos 8", Companion.of(c).size() >= 8);
            for (Companion.Message m : Companion.of(c)) {
                String t = m.text.toLowerCase();
                assertFalse(m.text, t.contains("fallaste") || t.contains("perdiste") || t.contains("otra vez") || t.contains("sin saldo"));
            }
        }
        // De noche, en llamada o en No molestar: nada (salvo el resumen de la noche).
        Companion.Budget b = new Companion.Budget();
        assertFalse(Companion.allowed(Companion.Trigger.CARE_SUGGESTION, b, 0, true, false, false));
        assertFalse(Companion.allowed(Companion.Trigger.CARE_SUGGESTION, b, 0, false, true, false));
        assertFalse(Companion.allowed(Companion.Trigger.CARE_SUGGESTION, b, 0, false, false, true));
        assertTrue(Companion.allowed(Companion.Trigger.CARE_SUGGESTION, b, 0, false, false, false));
    }
}
