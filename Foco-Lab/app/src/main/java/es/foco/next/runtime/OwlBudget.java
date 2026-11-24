package es.foco.next.runtime;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.AbsoluteRules;
import java.time.LocalDateTime;

/**
 * v32 (R10): un solo presupuesto diario para los avisos NO críticos, para que el búho sea la voz de
 * Foco y no una lluvia de notificaciones de módulos distintos.
 *
 * <p>Pasan por aquí: el acompañamiento del búho (CompanionNotifier), los logros y el ánimo
 * (RewardStore) y el aviso cálido de la noche (OwlWatch). Quedan fuera, porque son importantes: la
 * rutina pendiente, las horas límite y su pausa, el DNS, Accesibilidad apagada, el inicio perdido,
 * el cupón en uso, el anuncio del Weekend Lock y los servicios en primer plano.
 */
public final class OwlBudget {
    private OwlBudget() {}

    /** Avisos no críticos por día lógico (de 04:00 a 04:00), entre todos. */
    public static final int PER_DAY = 8;

    private static final String PREFS = "foco-buho-presupuesto";

    /**
     * ¿Puede salir ahora un aviso no crítico? Si sí, lo cuenta. Nunca con No molestar; de noche solo
     * si {@code nightOk} (el aviso cálido de la noche).
     */
    public static synchronized boolean take(Context c, boolean nightOk) {
        try {
            Context app = c.getApplicationContext();
            if (!nightOk && AbsoluteRules.esNocheAbsoluta(LocalDateTime.now())) return false;
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL)
                return false;
            SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String today = RoutineStore.today().toString();
            int sent = today.equals(p.getString("dia", "")) ? p.getInt("n", 0) : 0;
            if (sent >= PER_DAY) return false;
            p.edit().putString("dia", today).putInt("n", sent + 1).apply();
            return true;
        } catch (RuntimeException e) {
            return true; // si no se puede saber, no se calla un aviso
        }
    }
}
