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
    public void automaticoPorHoraYLoQueHaces() {
        assertEquals(PhotoTheme.CAFE, PhotoTheme.auto(at(7, 30)));
        PhotoTheme.Facts reto = at(8, 0);
        reto.retosHoy = 1;
        assertEquals(PhotoTheme.HORIZONTE, PhotoTheme.auto(reto));
        assertEquals(PhotoTheme.CASCADA, PhotoTheme.auto(at(10, 0)));
        assertEquals(PhotoTheme.CASCADA, PhotoTheme.auto(at(13, 0)));
        assertEquals(PhotoTheme.LUNA, PhotoTheme.auto(at(18, 0)));
        PhotoTheme.Facts night = at(22, 0);
        night.night = true;
        assertEquals(PhotoTheme.CUMBRE, PhotoTheme.auto(night));
        PhotoTheme.Facts prod = at(10, 0);
        prod.productive = true;
        assertEquals(PhotoTheme.RUTA, PhotoTheme.auto(prod));
        PhotoTheme.Facts focus = at(10, 0);
        focus.focus = true;
        assertEquals(PhotoTheme.DUNAS, PhotoTheme.auto(focus));
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
        assertEquals(PhotoTheme.CASCADA, PhotoTheme.choose("AUTO", at(10, 0)));
        assertEquals(PhotoTheme.CASCADA, PhotoTheme.choose("NO_EXISTE", at(10, 0)));
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
