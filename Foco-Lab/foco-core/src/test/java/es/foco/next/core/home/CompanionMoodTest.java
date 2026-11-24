package es.foco.next.core.home;

import static org.junit.Assert.*;

import es.foco.next.core.home.HomeState.Estado;
import org.junit.Test;

/** Compañero y Aduana. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class CompanionMoodTest {
    @Test
    public void growsBloomsAndRests() {
        CompanionMood m = CompanionMood.of(Estado.PRODUCTIVO, 65, 2, 0, 0, 15);
        assertEquals(3, m.growth);
        assertEquals(2, m.blooms);
        assertTrue(m.bright);
        assertFalse(m.dew);
        CompanionMood n = CompanionMood.of(Estado.NOCHE, 10, 0, 0, 1, 23);
        assertTrue(n.resting);
        assertFalse(n.dew); // de noche no hay rocío
    }

    @Test
    public void dewInTheMorningOrAfterRealRest() {
        assertTrue(CompanionMood.of(Estado.NEUTRO, 0, 0, 0, 0, 8).dew);
        assertTrue(CompanionMood.of(Estado.NEUTRO, 0, 0, 0, 1, 16).dew);
        assertFalse(CompanionMood.of(Estado.NEUTRO, 0, 0, 0, 0, 16).dew);
    }

    @Test
    public void leisureWithoutBalanceDropsSomeLeavesNeverAll() {
        assertEquals(0, CompanionMood.of(Estado.NEUTRO, 0, 0, 0, 0, 12).fallen);
        assertEquals(1, CompanionMood.of(Estado.NEUTRO, 0, 0, 2, 0, 12).fallen);
        assertEquals(3, CompanionMood.of(Estado.NEUTRO, 0, 0, 90, 0, 12).fallen);
        String c = CompanionMood.of(Estado.NEUTRO, 0, 0, 3, 0, 12).caption(CompanionMood.Variant.BONSAI, 0);
        assertTrue(c.contains("se recupera"));
    }

    @Test
    public void aduanaOnlyShortens() {
        long t = 1_000_000;
        Aduana.Limit l = new Aduana.Limit("com.game", t, 3);
        assertEquals(0, l.msLeft("com.other", t));
        assertEquals(3 * 60_000L, l.msLeft("com.game", t));
        assertFalse(l.expired("com.game", t + 2 * 60_000L));
        assertTrue(l.expired("com.game", t + 3 * 60_000L));
        assertFalse(l.expired("com.game", t + Aduana.VALID_MS + 1)); // se olvida tras la ventana
        assertEquals(t + 5 * 60_000L, new Aduana.Limit("x", t, 99).until); // nunca más de 5
        assertTrue(Aduana.validChoice(3));
        assertFalse(Aduana.validChoice(4));
    }
}
