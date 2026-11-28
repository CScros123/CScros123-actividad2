package es.foco.next.ui;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Toast;
import personal.qqq.braintraining.LabLaunch;

/**
 * Fase 2 (puente): abre la práctica corta de Lab (hasta 6 retos o 3 minutos de Redes y seguridad).
 * Practicar no da minutos de ocio: no se toca Repository, FocusEngine, presupuesto ni ventanas. Lab
 * corre dentro de es.foco.next, que es esencial, así que la pausa no se dibuja encima.
 */
public final class PracticeEntry {
    private PracticeEntry() {}

    /** Tarjeta de "Hoy". */
    static void today(Activity activity, LinearLayout body) {
        LinearLayout card = Design.card(body, "Practica 3 minutos");
        Design.note(
                card,
                "Redes y seguridad en tu nivel: hasta 6 retos o 3 minutos, lo primero que ocurra.");
        card.addView(
                Design.button(activity, "Practicar 3 min", false, () -> open(activity, false)));
        card.addView(Design.button(activity, "Todos los juegos", false, () -> openFull(activity)));
        Design.note(card, "Practicar no suma minutos de ocio. Lo que ganas es práctica.");
    }

    /** Abre Lab completo (catálogo, arcade, Redes y seguridad, jardín y progreso). */
    static void openFull(Activity activity) {
        try {
            activity.startActivity(LabLaunch.full(activity));
        } catch (RuntimeException e) {
            try {
                Toast.makeText(activity, "No se pudieron abrir los juegos", Toast.LENGTH_SHORT)
                        .show();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Menú completo de juegos desde la pausa (contexto del servicio de Accesibilidad). */
    public static void openGames(Context context) {
        try {
            context.startActivity(LabLaunch.fullFromPause(context));
        } catch (RuntimeException e) {
            try {
                Toast.makeText(context, "No se pudieron abrir los juegos", Toast.LENGTH_SHORT)
                        .show();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Lote C: cofre ganado practicando, desde la pausa. Abre Lab directamente en el cofre y después
     * ofrece la práctica de 3 min. No toca Repository, RuleStore, presupuesto ni bloqueos: el cofre
     * nunca da minutos.
     */
    public static void openChest(Context context) {
        try {
            context.startActivity(LabLaunch.chest(context, !(context instanceof Activity)));
        } catch (RuntimeException e) {
            try {
                Toast.makeText(context, "No se pudo abrir el cofre", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Lectura barata (SharedPreferences de Lab): true solo si hay un cofre ganado practicando y sin
     * abrir.
     */
    public static boolean chestAvailable(Context context) {
        try {
            return personal.qqq.braintraining.ChestSummary.available(context);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param fromPause true desde la pausa (servicio de Accesibilidad): tarea propia y "Volver"
     *     regresa a la app de debajo.
     */
    public static void open(Context context, boolean fromPause) {
        try {
            context.startActivity(
                    LabLaunch.shortSession(context, fromPause || !(context instanceof Activity)));
        } catch (RuntimeException e) {
            try {
                Toast.makeText(context, "No se pudo abrir la práctica", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
