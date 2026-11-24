package es.foco.next.core.home;

import static org.junit.Assert.*;

import es.foco.next.core.home.HomeState.Estado;
import es.foco.next.core.home.HomeState.Fact;
import es.foco.next.core.home.HomeState.Kind;
import es.foco.next.core.home.HomeState.Snapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Etapa 1.2. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class HomeStateTest {
    private static final ZoneId Z = ZoneId.of("Europe/Madrid");
    private static final long M = HomeState.MIN;

    private static long at(int h, int m) {
        return LocalDateTime.of(2026, 11, 17, h, m).atZone(Z).toInstant().toEpochMilli();
    }

    private static Snapshot step(Snapshot s, List<Fact> f, long now) {
        return HomeState.step(s, f, now, Z, true, -1, false);
    }

    @Test
    public void nightIsImmediate() {
        Snapshot s = step(Snapshot.initial(at(20, 0)), new ArrayList<>(), at(21, 0));
        assertEquals(Estado.NOCHE, s.estado);
        assertEquals("Ocio cerrado hasta las 7:00", s.motivo);
    }

    @Test
    public void autoTimeOffIsNight() {
        Snapshot s =
                HomeState.step(Snapshot.initial(at(12, 0)), new ArrayList<>(), at(12, 0), Z, false, -1, false);
        assertEquals(Estado.NOCHE, s.estado);
    }

    @Test
    public void wakeAfterFirstUnlockUntilSevenOrDone() {
        long unlock = at(5, 40);
        Snapshot s =
                HomeState.step(Snapshot.initial(at(5, 0)), new ArrayList<>(), at(5, 41), Z, true, unlock, false);
        assertEquals(Estado.DESPERTAR, s.estado);
        s = HomeState.step(s, new ArrayList<>(), at(6, 10), Z, true, unlock, true);
        assertEquals(Estado.NOCHE, s.estado); // reto hecho: vuelve la noche (Regla 2 sigue)
        s = HomeState.step(s, new ArrayList<>(), at(7, 0), Z, true, unlock, false);
        assertEquals(Estado.NEUTRO, s.estado); // a las 7 se sale sin histéresis
    }

    @Test
    public void unlockBeforeFiveIsNotWake() {
        Snapshot s =
                HomeState.step(Snapshot.initial(at(4, 0)), new ArrayList<>(), at(5, 30), Z, true, at(4, 50), false);
        assertEquals(Estado.NOCHE, s.estado);
    }

    @Test
    public void distractedNeedsFiveMinutesOfHysteresis() {
        List<Fact> f = new ArrayList<>();
        f.add(new Fact(at(10, 0), Kind.OCIO, 5 * M));
        Snapshot s = step(Snapshot.initial(at(9, 0)), f, at(10, 0));
        assertEquals(Estado.NEUTRO, s.estado);
        assertEquals(Estado.DISTRAIDO, s.candidate);
        s = step(s, f, at(10, 4));
        assertEquals(Estado.NEUTRO, s.estado);
        s = step(s, f, at(10, 5));
        assertEquals(Estado.DISTRAIDO, s.estado);
    }

    @Test
    public void threeAttemptsAreDistracted() {
        List<Fact> f = new ArrayList<>();
        for (int i = 0; i < 3; i++) f.add(new Fact(at(10, i), Kind.ATTEMPT, 0));
        Snapshot s = step(Snapshot.initial(at(9, 0)), f, at(10, 3));
        s = step(s, f, at(10, 8));
        assertEquals(Estado.DISTRAIDO, s.estado);
    }

    @Test
    public void productiveNeedsTenMinutesAndZeroLeisure() {
        List<Fact> f = new ArrayList<>();
        f.add(new Fact(at(10, 0), Kind.PRODUCTIVE, 7 * M));
        f.add(new Fact(at(10, 1), Kind.RETO, 0)); // +3 min
        Snapshot s = step(Snapshot.initial(at(9, 0)), f, at(10, 1));
        s = step(s, f, at(10, 6));
        assertEquals(Estado.PRODUCTIVO, s.estado);
        f.add(new Fact(at(10, 7), Kind.OCIO, M));
        assertNotEquals(Estado.PRODUCTIVO, HomeState.raw(HomeState.window(f, at(10, 7))));
    }

    @Test
    public void productiveHoldsFifteenMinutesAgainstNeutral() {
        List<Fact> f = new ArrayList<>();
        f.add(new Fact(at(10, 0), Kind.PRODUCTIVE, 12 * M));
        Snapshot s = step(Snapshot.initial(at(9, 0)), f, at(10, 0));
        s = step(s, f, at(10, 5));
        assertEquals(Estado.PRODUCTIVO, s.estado);
        s = step(s, f, at(10, 29)); // último momento productivo: 10:29
        // A las 10:31 el hecho sale de la ventana: raw = NEUTRO, pero se mantiene 15 min.
        List<Fact> none = new ArrayList<>();
        s = step(s, none, at(10, 31));
        assertEquals(Estado.PRODUCTIVO, s.estado);
        s = step(s, none, at(10, 43));
        assertEquals(Estado.PRODUCTIVO, s.estado);
        // 10:44: 15 min desde el último productivo; después, 5 min de histéresis.
        s = step(s, none, at(10, 44));
        assertEquals(Estado.PRODUCTIVO, s.estado);
        s = step(s, none, at(10, 49));
        assertEquals(Estado.NEUTRO, s.estado);
    }

    @Test
    public void oldFactsAreOutsideTheWindow() {
        List<Fact> f = new ArrayList<>();
        f.add(new Fact(at(9, 0), Kind.OCIO, 10 * M));
        assertEquals(0, HomeState.window(f, at(9, 31)).ocioMs);
        assertTrue(HomeState.prune(f, at(9, 31)).isEmpty());
    }

    @Test
    public void daysDoneNeverResetAndMilestones() {
        LocalDate d = LocalDate.of(2026, 11, 17);
        int t = HomeState.countDay(2, "2026-11-10", d, true);
        assertEquals(3, t);
        assertEquals(3, HomeState.countDay(3, d.toString(), d, true)); // una vez por día
        assertEquals(3, HomeState.countDay(3, d.toString(), d.plusDays(1), false)); // descanso: no baja
        assertTrue(HomeState.milestone(2, 3));
        assertTrue(HomeState.milestone(6, 7));
        assertFalse(HomeState.milestone(7, 8));
        assertTrue(HomeState.dayDone(20 * M, 0));
        assertTrue(HomeState.dayDone(0, 1));
        assertFalse(HomeState.dayDone(19 * M, 0));
    }
}
