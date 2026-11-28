package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.AbsoluteRules;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * v50: de noche, los avisos de las redes sociales (Regla 1: TikTok, Instagram, Facebook, X…) se
 * quitan en cuanto llegan, para que no te despierten ni te enganchen. Usa el mismo acceso a
 * notificaciones que la música: Foco solo mira de qué app viene un aviso, nunca su contenido, y no
 * guarda nada salvo cuántos quitó cada día.
 */
public final class NightQuiet {
    private NightQuiet() {}

    static final String PREFS = "foco-noche-avisos";
    static final int KEEP_DAYS = 14;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Activado por defecto (se puede apagar en Ajustes). */
    public static boolean enabled(Context c) {
        return prefs(c).getBoolean("activo", true);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("activo", on).apply();
    }

    /** ¿Quitar este aviso? Solo de noche, solo redes sociales y solo si está activado. */
    static boolean shouldQuiet(Context c, String pkg) {
        if (pkg == null || !AbsoluteRules.social(pkg) || !enabled(c)) return false;
        CareStore.install(c);
        return AbsoluteRules.esNocheAbsoluta(LocalDateTime.now());
    }

    static void count(Context c) {
        SharedPreferences p = prefs(c);
        String day = RoutineStore.today().toString();
        SharedPreferences.Editor e = p.edit().putInt("n_" + day, p.getInt("n_" + day, 0) + 1);
        String old = RoutineStore.today().minusDays(KEEP_DAYS).toString();
        for (String k : p.getAll().keySet())
            if (k.startsWith("n_") && k.substring(2).compareTo(old) < 0) e.remove(k);
        e.apply();
    }

    /** Avisos quitados en los últimos 7 días. */
    public static int lastWeek(Context c) {
        SharedPreferences p = prefs(c);
        int n = 0;
        LocalDate d = RoutineStore.today();
        for (int i = 0; i < 7; i++) n += p.getInt("n_" + d.minusDays(i), 0);
        return n;
    }
}
