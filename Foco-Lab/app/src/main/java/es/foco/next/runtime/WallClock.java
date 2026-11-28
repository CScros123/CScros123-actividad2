package es.foco.next.runtime;

import android.os.SystemClock;

/**
 * Hora civil que leen el servicio y el Weekend Lock. En la app es siempre {@link
 * System#currentTimeMillis()}. Las pruebas la fijan con {@link #fixForTests} (Regla 2: de 21:00 a
 * 07:00 el ocio se cierra, así que sin esto el resultado de las pruebas dependía de la hora a la
 * que se ejecutaban); a partir de ahí avanza con el reloj monotónico simulado.
 */
public final class WallClock {
    private WallClock() {}

    private static volatile long fixedWall = -1, fixedElapsed = 0;

    public static long now() {
        long f = fixedWall;
        return f < 0
                ? System.currentTimeMillis()
                : f + (SystemClock.elapsedRealtime() - fixedElapsed);
    }

    static void fixForTests(long wall) {
        fixedElapsed = SystemClock.elapsedRealtime();
        fixedWall = wall;
    }

    static void resetForTests() {
        fixedWall = -1;
    }
}
