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
        assertFalse(
                Awareness.noteOk("Estoy viendo cómo hacer pan sin horno, quiero probar la receta"));
        assertTrue(
                Awareness.noteOk(
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
    public void conMasOcioHoyLaPreguntaLlegaAntes() {
        // Menos de 1 h hoy: 15 min.
        assertEquals(15 * M, Awareness.dueIn(0, 0, 1000, false, 30 * M));
        // Entre 1 y 2 h: 10 min.
        assertEquals(10 * M, Awareness.dueIn(0, 0, 1000, false, 70 * M));
        // 2 h o más (como hoy: TikTok 1 h 07 + Instagram 1 h 01): 5 min.
        assertEquals(5 * M, Awareness.dueIn(0, 0, 1000, false, 128 * M));
        // Cruzar la hora a mitad de sesión: 55 min antes → toca a los 10 min seguidos.
        assertEquals(10 * M, Awareness.dueIn(0, 0, 1000, false, 55 * M));
        // Cruzar las 2 h: 112 min antes → a los 8 min (al llegar a 2 h), no a los 10.
        assertEquals(8 * M, Awareness.dueIn(0, 0, 1000, false, 112 * M));
        assertEquals(3 * M, Awareness.dueIn(5 * M, 0, 1000, false, 112 * M));
        // Tras un «sí», manda la ventana del «sí».
        assertEquals(2 * M, Awareness.dueIn(30 * M, 1000 + 2 * M, 1000, false, 200 * M));
    }

    @Test
    public void elSiDuraMenosConMuchoOcio() {
        assertEquals(5 * M, Awareness.extendMs(90 * M));
        assertEquals(3 * M, Awareness.extendMs(120 * M));
        assertEquals("", Awareness.stageText(40 * M));
        assertEquals(
                "Hoy llevas 2 h 08 de ocio: te pregunto a los 5 min", Awareness.stageText(128 * M));
    }

    @Test
    public void elCorteEsperaAlSiguienteVideo() {
        // Duración típica mientras no hay datos; luego tu ritmo real.
        assertEquals(45_000, Awareness.videoMs("com.zhiliaoapp.musically", 60_000, 2));
        assertEquals(30_000, Awareness.videoMs("com.instagram.android", 0, 0));
        assertEquals(20_000, Awareness.videoMs("com.zhiliaoapp.musically", 10 * M, 30));
        // Espera: dos videos, entre 30 s y 3 min.
        assertEquals(90_000, Awareness.cutGraceMs("com.zhiliaoapp.musically", 0, 0));
        assertEquals(60_000, Awareness.cutGraceMs("com.instagram.android", 0, 0));
        assertEquals(30_000, Awareness.cutGraceMs("x", 10 * M, 100)); // 6 s por video → mínimo 30 s
        assertEquals(3 * M, Awareness.cutGraceMs("x", 60 * M, 5)); // videos largos → máximo 3 min
        // Se abre al deslizar, al volver tras un «no» o al agotar la espera.
        assertTrue(Awareness.cutNow(true, false, 1_000, 90_000));
        assertTrue(Awareness.cutNow(false, true, 0, 90_000));
        assertFalse(Awareness.cutNow(false, false, 89_000, 90_000));
        assertTrue(Awareness.cutNow(false, false, 90_000, 90_000));
        assertEquals("", Awareness.videosText(5 * M, 3));
        assertEquals("≈ 30 videos · unos 20 s cada uno", Awareness.videosText(10 * M, 30));
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
