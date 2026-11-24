package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Último reto rápido elegido por el inicio (juego de Cognitive Lab que lleva más días sin jugarse).
 * HomeActivity lo calcula fuera del hilo principal y lo guarda aquí para los mensajes ({juego}).
 */
public final class QuickGame {
    private QuickGame() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("foco-quick-game", Context.MODE_PRIVATE);
    }

    public static void save(Context c, String id, String title, int level) {
        p(c).edit().putString("id", id).putString("title", title).putInt("level", level).apply();
    }

    public static String id(Context c) {
        return p(c).getString("id", "");
    }

    public static String title(Context c) {
        return p(c).getString("title", "");
    }

    public static int level(Context c) {
        return p(c).getInt("level", 1);
    }
}
