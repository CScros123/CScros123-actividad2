package es.foco.next.core.home;

import static org.junit.Assert.*;

import es.foco.next.core.home.HomeState.Estado;
import es.foco.next.core.home.OwlBrain.Mode;
import java.util.List;
import org.junit.Test;

/** Búho de Foco. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class OwlBrainTest {
    private static OwlBrain.Facts facts() {
        OwlBrain.Facts f = new OwlBrain.Facts();
        f.game = "La maleta del técnico";
        f.reader = "El Libro Total";
        f.bible = "Santa Biblia";
        return f;
    }

    @Test
    public void autoModeFollowsRealState() {
        OwlBrain.Facts f = facts();
        assertEquals(Mode.CALMA, OwlBrain.auto(f));
        f.estado = Estado.DISTRAIDO;
        assertEquals(Mode.OCIO, OwlBrain.auto(f));
        f.estado = Estado.PRODUCTIVO;
        f.productiveRunMin = 46;
        assertEquals(Mode.LEVANTATE, OwlBrain.auto(f));
        f.estado = Estado.NOCHE;
        assertEquals(Mode.NOCHE, OwlBrain.auto(f));
    }

    @Test
    public void nightOnlyCalmOrWord() {
        OwlBrain.Facts f = facts();
        f.estado = Estado.NOCHE;
        f.night = true;
        List<Mode> c = OwlBrain.cycle(f, true);
        assertEquals(2, c.size());
        assertEquals(Mode.BIBLIA, c.get(1));
        assertEquals(1, OwlBrain.cycle(f, false).size());
    }

    @Test
    public void neverPromisesMinutesThatDoNotExist() {
        OwlBrain.Facts f = facts();
        for (Mode m : Mode.values())
            for (int i = 0; i < 8; i++) {
                String s = OwlBrain.say(m, f, i, true) + " " + OwlBrain.cta(m, f);
                assertFalse(
                        s, s.contains("+1 min") || s.contains("+3 min") || s.contains("te sumo"));
                assertFalse(
                        s,
                        s.toLowerCase().contains("fallaste") || s.toLowerCase().contains("culpa"));
            }
        // v26: ni los retos ni la lectura dan minutos; el búho nunca habla de banco.
        for (Mode m : Mode.values())
            for (int i = 0; i < 4; i++) {
                String s = OwlBrain.say(m, f, i, false);
                assertFalse(s, s.contains("banco") || s.matches(".*\\+\\d+ min.*"));
            }
    }

    @Test
    public void neutralToneHasNoBro() {
        OwlBrain.Facts f = facts();
        // Busca el apelativo («, bro»), no la sílaba: «El Libro Total» no es tono cercano.
        for (Mode m : Mode.values()) assertFalse(OwlBrain.say(m, f, 0, false).contains(", bro"));
    }
}
