package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AwarenessTest {
    private static final long M = Awareness.MIN;

    @Test
    public void laPrimeraPreguntaLlegaALos15Minutos() {
        assertEquals(15 * M, Awareness.dueIn(0, 0, 1000, false));
        assertEquals(5 * M, Awareness.dueIn(10 * M, 0, 1000, false));
        assertEquals(0, Awareness.dueIn(16 * M, 0, 1000, false));
    }

    @Test
    public void trasUnSiSonCincoMinutosYVuelve() {
        long now = 100 * M;
        assertEquals(5 * M, Awareness.dueIn(20 * M, now + 5 * M, now, false));
        assertEquals(0, Awareness.dueIn(25 * M, now - 1, now, false));
    }

    @Test
    public void volverJustoDespuesDeDecirNoPreguntaAlAbrir() {
        assertEquals(0, Awareness.dueIn(0, 0, 1000, true));
    }

    @Test
    public void laNotaPideQuincePalabrasDistintas() {
        assertEquals(1, Awareness.words("asdf asdf asdf ASDF"));
        assertEquals(0, Awareness.words("a b c"));
        assertFalse(Awareness.noteOk("Estoy viendo cómo hacer pan sin horno, quiero probar la receta"));
        assertTrue(Awareness.noteOk(
                "Estoy viendo cómo hacer pan sin horno porque quiero probar la receta este fin de semana con mi familia"));
        assertEquals(0, Awareness.words(null));
    }

    @Test
    public void valioLaPenaSoloTrasSesionLargaSinPregunta() {
        assertTrue(Awareness.askWorth(12 * M, false, 0));
        assertFalse(Awareness.askWorth(12 * M, true, 0));
        assertFalse(Awareness.askWorth(8 * M, false, 0));
        assertFalse(Awareness.askWorth(12 * M, false, Awareness.WORTH_PER_DAY));
    }

    @Test
    public void textos() {
        assertEquals("32 min", Awareness.duration(32 * M));
        assertEquals("1 h 05", Awareness.duration(65 * M));
        assertEquals("2 h", Awareness.duration(120 * M));
        assertEquals(Awareness.Kind.RELAJA, Awareness.Kind.parse("relaja"));
        assertEquals(Awareness.Worth.MAS_O_MENOS, Awareness.Worth.parse("MAS_O_MENOS"));
        assertNull(Awareness.Worth.parse("?"));
    }
}
