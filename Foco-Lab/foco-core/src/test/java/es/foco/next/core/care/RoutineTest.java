package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class RoutineTest {
    @Test
    public void parsesLines() {
        List<Routine.Task> t = Routine.parse("Bañarte · 10\nOrdenar 5\nBarba|3\nLeer\n\n");
        assertEquals(4, t.size());
        assertEquals("Bañarte", t.get(0).label);
        assertEquals(10, t.get(0).minutes);
        assertEquals("Ordenar", t.get(1).label);
        assertEquals(3, t.get(2).minutes);
        assertEquals(5, t.get(3).minutes);
        assertEquals(t.size(), Routine.parse(Routine.encode(t)).size());
    }

    @Test
    public void defaultsAreValid() {
        assertEquals(4, Routine.parse(Routine.DEFAULT_MORNING).size());
        assertEquals(5, Routine.parse(Routine.DEFAULT_NIGHT).size());
        assertEquals(35, Routine.totalMinutes(Routine.parse(Routine.DEFAULT_NIGHT)));
    }

    @Test
    public void windows() {
        assertEquals(Routine.Kind.MORNING, Routine.window(LocalDateTime.of(2026, 11, 22, 6, 12), 21));
        assertNull(Routine.window(LocalDateTime.of(2026, 11, 22, 13, 0), 21));
        assertEquals(Routine.Kind.NIGHT, Routine.window(LocalDateTime.of(2026, 11, 22, 19, 0), 21));
        assertEquals(Routine.Kind.NIGHT, Routine.window(LocalDateTime.of(2026, 11, 22, 23, 0), 21));
        assertNull(Routine.window(LocalDateTime.of(2026, 11, 22, 18, 59), 21));
    }

    @Test
    public void blocksUntilDoneAndMinimumTime() {
        List<Routine.Task> t = Routine.parse(Routine.DEFAULT_MORNING); // 28 min → espera mínima 14
        Set<Integer> done = new HashSet<>();
        long start = 1_000_000;
        assertTrue(Routine.blocks(true, t, done, start, start + 60 * 60_000));
        for (int i = 0; i < t.size(); i++) done.add(i);
        assertTrue(Routine.blocks(true, t, done, start, start + 5 * 60_000)); // todo marcado en 5 min
        assertFalse(Routine.blocks(true, t, done, start, start + 14 * 60_000));
        assertFalse(Routine.blocks(false, t, new HashSet<>(), start, start));
    }

    @Test
    public void gateText() {
        List<Routine.Task> t = Routine.parse("Bañarte · 10\nBarba · 5");
        Set<Integer> done = new HashSet<>();
        done.add(0);
        assertEquals("Primero tu rutina de la noche: falta 1 (~5 min). Márcala en el inicio.",
                Routine.gateText(Routine.Kind.NIGHT, t, done, 0, 0));
    }

    @Test
    public void resolvesDateThenWeekdayThenGeneral() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        java.time.LocalDate mon = java.time.LocalDate.of(2026, 11, 23); // lunes
        assertEquals(Routine.DEFAULT_MORNING, Routine.resolve(Routine.Kind.MORNING, mon, m::get));
        m.put(Routine.generalKey(Routine.Kind.MORNING), "General · 5");
        assertEquals("General · 5", Routine.resolve(Routine.Kind.MORNING, mon, m::get));
        m.put(Routine.weekdayKey(Routine.Kind.MORNING, java.time.DayOfWeek.MONDAY), "Gimnasio · 45");
        assertEquals("Gimnasio · 45", Routine.resolve(Routine.Kind.MORNING, mon, m::get));
        m.put(Routine.dateKey(Routine.Kind.MORNING, mon), "Médico · 30");
        assertEquals("Médico · 30", Routine.resolve(Routine.Kind.MORNING, mon, m::get));
        assertEquals("Gimnasio · 45", Routine.resolve(Routine.Kind.MORNING, mon.plusWeeks(1), m::get));
    }

    @Test
    public void sundayPlanPrompt() {
        assertTrue(Routine.planDue(LocalDateTime.of(2026, 11, 22, 18, 0), ""));
        assertFalse(Routine.planDue(LocalDateTime.of(2026, 11, 22, 18, 0), "2026-11-22"));
        assertFalse(Routine.planDue(LocalDateTime.of(2026, 11, 22, 9, 0), ""));
        assertFalse(Routine.planDue(LocalDateTime.of(2026, 11, 23, 18, 0), ""));
    }

    @Test
    public void parsesWindowAndDifficulty() {
        Routine.Task t = Routine.parse("Estudiar · 60 · 10:30 · difícil").get(0);
        assertEquals("Estudiar", t.label);
        assertEquals(60, t.minutes);
        assertEquals(630, t.window);
        assertEquals(3, t.difficulty);
        assertEquals("Estudiar · 60 · 10:30 · difícil", Routine.encode(Routine.parse("Estudiar · 60 · 10:30 · difícil")));
        assertEquals(LocalDateTime.of(2026, 11, 22, 3, 0).toLocalDate().minusDays(1), Routine.logicalDay(LocalDateTime.of(2026, 11, 22, 3, 0)));
    }

    // v30 (escrito sin ejecutar, por indicación del usuario).
    @Test
    public void bulletFromKeyboardNeverStaysInTheName() {
        Routine.Task t = Routine.parse("Hacer el desayuno • · 15").get(0);
        assertEquals("Hacer el desayuno", t.label);
        assertEquals(15, t.minutes);
        assertEquals("Bailar Jumpstyle", Routine.parse("Bailar Jumpstyle • · 6").get(0).label);
        assertEquals("Leer un libro", Routine.cleanLabel("  Leer · un\nlibro "));
    }

    @Test
    public void photoFlagRoundTrips() {
        Routine.Task t = Routine.parse("Gimnasio · 45 · 7:30 · media · foto").get(0);
        assertTrue(t.photo);
        assertEquals(450, t.window);
        assertEquals("Gimnasio · 45 · 7:30 · media · foto", Routine.encode(Routine.parse("Gimnasio · 45 · 7:30 · media · foto")));
        assertFalse(Routine.parse("Leer · 10").get(0).photo);
    }

    @Test
    public void addReplaceAndRemoveWithoutFormats() {
        String l = Routine.withAdded("Leer · 10", new Routine.Task("Correr", 30, -1, 3, true));
        assertEquals("Leer · 10\nCorrer · 30 · difícil · foto", l);
        l = Routine.withReplaced(l, 0, new Routine.Task("Leer", 20));
        assertEquals(20, Routine.parse(l).get(0).minutes);
        assertEquals(1, Routine.indexOf(l, "correr"));
        assertEquals("Leer · 20", Routine.withRemoved(l, 1));
    }
}
