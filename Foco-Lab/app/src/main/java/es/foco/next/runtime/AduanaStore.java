package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.home.Aduana;
import es.foco.next.log.InspectorLog;

/** Guarda el límite elegido en la Aduana y lo lee el servicio (barato, sin hilos). */
public final class AduanaStore {
    private AduanaStore() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("foco-aduana", Context.MODE_PRIVATE);
    }

    public static void choose(Context c, String pkg, int minutes) {
        if (!Aduana.validChoice(minutes)) minutes = 5;
        long now = System.currentTimeMillis();
        Aduana.Limit l = new Aduana.Limit(pkg, now, minutes);
        p(c).edit()
                .putString("pkg", l.pkg)
                .putLong("at", l.chosenAt)
                .putLong("until", l.until)
                .apply();
        InspectorLog.event(
                c, "aduana", pkg, now, 0, 1, minutes, "elegido_" + minutes + "_min", null);
        FocusAccessibilityService.checkpoint();
    }

    static Aduana.Limit current(Context c) {
        SharedPreferences s = p(c);
        return new Aduana.Limit(s.getString("pkg", ""), s.getLong("at", 0), s.getLong("until", 0));
    }

    public static int chosenMinutes(Context c) {
        Aduana.Limit l = current(c);
        return (int) Math.max(1, (l.until - l.chosenAt) / 60_000);
    }
}
