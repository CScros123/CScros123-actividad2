package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FocoCoreTest {
    @Test
    public void states() {
        assertEquals(FocoCore.State.NO_PLAN, FocoCore.state(1, 0, 0, 100));
        assertEquals(FocoCore.State.RUNNING, FocoCore.state(4, 1, 0, 100));
        assertEquals(FocoCore.State.COMPLETE, FocoCore.state(4, 4, 0, 100));
        assertEquals(FocoCore.State.PENALTY, FocoCore.state(4, 4, 200, 100));
        // Rutinas de siempre (8) pero solo 1 tarea propia de hoy: sin plan.
        assertEquals(FocoCore.State.NO_PLAN, FocoCore.state(9, 3, 1, 0, 100));
        assertEquals(FocoCore.State.RUNNING, FocoCore.state(10, 3, 2, 0, 100));
    }

    @Test
    public void missedOnlyAfterGrace() {
        Routine.Task t = Routine.parse("Estudiar · 60 · 10:30").get(0);
        assertFalse(FocoCore.missed(t, false, LocalTime.of(10, 39)));
        assertTrue(FocoCore.warnDue(t, false, LocalTime.of(10, 35)));
        assertTrue(FocoCore.missed(t, false, LocalTime.of(10, 40)));
        assertFalse(FocoCore.missed(t, true, LocalTime.of(11, 0)));
    }

    @Test
    public void despuesDeMedianocheSigueElDiaLogico() {
        // v32 (R9): a las 00:30 todavía es el día anterior; una tarea de las 23:50 ya venció.
        Routine.Task t = Routine.parse("Leer · 10 · 23:50").get(0);
        assertEquals(24 * 60 + 30, FocoCore.logicalMinute(LocalTime.of(0, 30)));
        assertEquals(10 * 60, FocoCore.logicalMinute(LocalTime.of(10, 0)));
        assertTrue(FocoCore.missedAt(t, false, FocoCore.logicalMinute(LocalTime.of(0, 30))));
        assertFalse(
                FocoCore.missed(t, false, LocalTime.of(0, 30))); // la hora natural se equivocaba
        assertTrue(FocoCore.warnDueAt(t, false, FocoCore.logicalMinute(LocalTime.of(23, 55))));
    }

    @Test
    public void penaltyDoesNotStackNorAtNight() {
        assertEquals(100 + FocoCore.PENALTY_MS, FocoCore.penaltyUntil(100, 0, false));
        assertEquals(0, FocoCore.penaltyUntil(100, 500, false));
        assertEquals(0, FocoCore.penaltyUntil(100, 0, true));
    }

    @Test
    public void freeHourStopsAtNight() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 23, 20, 30);
        assertEquals(30 * 60_000L, FocoCore.freeUntil(t, 0, 21));
        assertEquals(
                FocoCore.FREE_MS, FocoCore.freeUntil(LocalDateTime.of(2026, 11, 23, 15, 0), 0, 21));
        assertEquals(0, FocoCore.freeUntil(LocalDateTime.of(2026, 11, 23, 21, 30), 0, 21));
    }

    @Test
    public void nextPrefersEarliestWindow() {
        List<Routine.Task> t =
                Routine.parse("Leer · 10\nEstudiar · 60 · 10:30\nCorrer · 20 · 9:00");
        Set<Integer> done = new HashSet<>();
        assertEquals(2, FocoCore.next(t, done));
        done.add(2);
        assertEquals(1, FocoCore.next(t, done));
    }

    @Test
    public void weeklyNamesHardestBlockAndMoney() {
        List<FocoCore.Day> days = new ArrayList<>();
        for (int i = 0; i < 5; i++) days.add(new FocoCore.Day(2, 1, 2, 2, 0, 0, 1, 1000, 1000));
        String s = FocoCore.weekly(days, 7000, 9000);
        assertTrue(s, s.contains("la mañana (50 %)"));
        assertTrue(s, s.contains("Te pasaste $20"));
        assertTrue(s, s.contains("5 bloqueos"));
    }

    @Test
    public void insisteCadaCincoMinutosAntesDeLaHora() {
        int nine = FocoCore.MORNING_DEADLINE; // 9:00
        assertFalse(FocoCore.nagAt(nine, false, 8 * 60 + 29));
        assertTrue(FocoCore.nagAt(nine, false, 8 * 60 + 30));
        assertTrue(FocoCore.nagAt(nine, false, 9 * 60 + 9)); // margen de 10 min
        assertFalse(FocoCore.nagAt(nine, false, 9 * 60 + 10));
        assertFalse(FocoCore.nagAt(nine, true, 8 * 60 + 45)); // hecha: silencio
        assertFalse(FocoCore.nagAt(-1, false, 8 * 60 + 45)); // sin hora
        assertTrue(FocoCore.nagAgain(0, 1000));
        assertFalse(FocoCore.nagAgain(1000, 1000 + 2 * 60_000));
        assertTrue(FocoCore.nagAgain(1000, 1000 + 5 * 60_000));
        assertEquals("Quedan 12 min (hasta las 9:00)", FocoCore.nagText(nine, 8 * 60 + 48));
        assertEquals("Ya son las 9:00: 7 min de margen", FocoCore.nagText(nine, 9 * 60 + 3));
        // La noche: 0:30 es 24:30 en el día de Foco.
        assertEquals(24 * 60 + 30, FocoCore.logicalDeadline(30));
        assertEquals(FocoCore.NIGHT_DEADLINE, FocoCore.logicalDeadline(23 * 60));
        assertEquals(
                "Quedan 20 min (hasta las 0:30)", FocoCore.nagText(24 * 60 + 30, 24 * 60 + 10));
    }
}
