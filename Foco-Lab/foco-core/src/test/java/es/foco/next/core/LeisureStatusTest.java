package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.LocalDateTime;
import org.junit.Test;

/** Etapa 0. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class LeisureStatusTest {
    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 11, 17, h, m); // martes
    }

    @Test
    public void nightAt0626ShowsZeroAndReason() {
        // El caso reportado: 6:26, motor sin uso (de noche no cuenta) -> antes "5 min 0 s".
        LeisureStatus s = LeisureStatus.of(at(6, 26), true, true, true, 0, false, 0);
        assertEquals(0, s.availableMs);
        assertTrue(s.night);
        assertEquals("Ocio cerrado hasta las 7:00", s.reason);
    }

    @Test
    public void nightBoundaries() {
        assertTrue(LeisureStatus.of(at(21, 0), true, true, true, 0, false, 0).night);
        assertTrue(LeisureStatus.of(at(6, 59), true, true, true, 0, false, 0).night);
        assertFalse(LeisureStatus.of(at(7, 0), true, true, true, 0, false, 0).night);
        assertFalse(LeisureStatus.of(at(20, 59), true, true, true, 0, false, 0).night);
    }

    @Test
    public void autoTimeOffIsNightAtNoon() {
        LeisureStatus s = LeisureStatus.of(at(12, 0), false, true, true, 0, false, 0);
        assertEquals(0, s.availableMs);
        assertEquals(LeisureStatus.NO_AUTOTIME_REASON, s.reason);
    }

    @Test
    public void dayShowsRemainingBudget() {
        LeisureStatus s = LeisureStatus.of(at(10, 0), true, true, true, 60_000, false, 30 * 60_000);
        assertEquals(4 * 60_000, s.availableMs);
        assertEquals("Ocio disponible · la ventana termina en 30 min", s.reason);
    }

    @Test
    public void lockedShowsZero() {
        LeisureStatus s =
                LeisureStatus.of(at(10, 0), true, true, true, FocusEngine.BUDGET, true, 60_000);
        assertEquals(0, s.availableMs);
        assertTrue(s.reason.startsWith("Ocio bloqueado"));
    }

    @Test
    public void worstHourWithoutLeisureHasNoData() {
        assertEquals("Sin datos todavía", LeisureStatus.worstHourLine(0, 14));
        assertEquals("Sin datos todavía", LeisureStatus.worstHourLine(60_000, -1));
        assertEquals("de 14:00 a 15:00", LeisureStatus.worstHourLine(60_000, 14));
    }
}
