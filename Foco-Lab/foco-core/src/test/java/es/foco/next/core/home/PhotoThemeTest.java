package es.foco.next.core.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoThemeTest {
    private static PhotoTheme.Facts at(int h, int m) {
        PhotoTheme.Facts f = new PhotoTheme.Facts();
        f.hour = h;
        f.minute = m;
        return f;
    }

    @Test
    public void automaticoSoloPorLaHora() {
        assertEquals(PhotoTheme.CAFE, PhotoTheme.auto(at(7, 30)));
        PhotoTheme.Facts reto = at(7, 0);
        reto.retosHoy = 1;
        assertEquals(PhotoTheme.HORIZONTE, PhotoTheme.auto(reto));
        assertEquals(PhotoTheme.DUNAS, PhotoTheme.auto(at(10, 0)));
        PhotoTheme.Facts odd = at(10, 0);
        odd.dayOfYear = 1;
        assertEquals(PhotoTheme.CASCADA, PhotoTheme.auto(odd));
        assertEquals(PhotoTheme.PRADERA, PhotoTheme.auto(at(13, 0)));
        assertEquals(PhotoTheme.TARDE, PhotoTheme.auto(at(16, 0)));
        assertEquals(PhotoTheme.RUTA, PhotoTheme.auto(at(18, 30)));
        assertEquals(PhotoTheme.LUNA, PhotoTheme.auto(at(20, 0)));
        PhotoTheme.Facts night = at(22, 0);
        night.night = true;
        assertEquals(PhotoTheme.CUMBRE, PhotoTheme.auto(night));
        // Lo que haces ya no cambia el fondo.
        PhotoTheme.Facts prod = at(13, 0);
        prod.productive = true;
        prod.focus = true;
        assertEquals(PhotoTheme.PRADERA, PhotoTheme.auto(prod));
        // Los fondos ganados entran solos en días alternos.
        PhotoTheme.Facts won = at(23, 0);
        won.night = true;
        won.aurora = true;
        won.dayOfYear = 3;
        assertEquals(PhotoTheme.AURORA, PhotoTheme.auto(won));
    }

    @Test
    public void nuncaUnCieloDeNocheDeDia() {
        for (int m = 5 * 60; m < 19 * 60 + 30; m += 15)
            for (int day = 0; day < 2; day++) {
                PhotoTheme.Facts f = at(m / 60, m % 60);
                f.dayOfYear = day;
                f.aurora = true;
                f.mar = true;
                assertTrue(m + " " + PhotoTheme.auto(f), !PhotoTheme.auto(f).nightSky());
            }
    }

    @Test
    public void elOcioNuncaPoneLava() {
        for (int h = 0; h < 24; h++)
            for (boolean n : new boolean[] {false, true}) {
                PhotoTheme.Facts f = at(h, 0);
                f.night = n;
                assertNotEquals(PhotoTheme.LAVA, PhotoTheme.auto(f));
            }
        assertEquals(PhotoTheme.LAVA, PhotoTheme.choose("LAVA", at(10, 0)));
        assertEquals(PhotoTheme.DUNAS, PhotoTheme.choose("AUTO", at(10, 0)));
        assertEquals(PhotoTheme.DUNAS, PhotoTheme.choose("NO_EXISTE", at(10, 0)));
    }

    @Test
    public void paletasSinRojoNiNegroYDockLegible() {
        for (PhotoTheme t : PhotoTheme.values()) {
            for (int c : t.palette.all()) assertTrue(t + " " + Integer.toHexString(c), HomePalette.safe(c));
            int on = t.palette.onHill;
            int lum = (((on >> 16) & 255) * 3 + ((on >> 8) & 255) * 6 + (on & 255)) / 10;
            assertTrue(t + ": texto claro sobre el velo del dock", lum > 140);
        }
    }
}
