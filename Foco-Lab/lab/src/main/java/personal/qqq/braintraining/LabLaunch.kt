package personal.qqq.braintraining

import android.content.Context
import android.content.Intent

/** Fase 2: cómo abre Foco (Java) la práctica corta de Lab. LabActivity sigue sin exportar. */
object LabLaunch {
    const val EXTRA_SHORT_SESSION = "personal.qqq.braintraining.extra.SHORT_SESSION"
    const val EXTRA_FROM_PAUSE = "personal.qqq.braintraining.extra.FROM_PAUSE"
    const val EXTRA_CHEST = "personal.qqq.braintraining.extra.CHEST"
    const val EXTRA_GAME = "personal.qqq.braintraining.extra.GAME"

    /**
     * Foco Home, etapa 4.3: abre Lab directamente en ESE juego (12 rondas, nivel guardado). Desde
     * el inicio: tarea propia; al terminar, "atrás" vuelve al inicio.
     */
    @JvmStatic
    fun game(context: Context, id: String): Intent =
        Intent(context, LabActivity::class.java)
            .putExtra(EXTRA_GAME, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    @JvmStatic fun gameOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_GAME)

    /**
     * Desde la pausa (servicio de Accesibilidad, sin actividad) se abre en una tarea propia y
     * oculta de Recientes: al pulsar "Volver" se cierra esa tarea y Android muestra la app de
     * debajo, donde la pausa vuelve si sigue bloqueada. Desde "Hoy" se abre en la tarea de Foco y
     * "Volver" regresa a "Hoy".
     */
    @JvmStatic
    fun shortSession(context: Context, fromPause: Boolean): Intent {
        val i =
            Intent(context, LabActivity::class.java)
                .putExtra(EXTRA_SHORT_SESSION, true)
                .putExtra(EXTRA_FROM_PAUSE, fromPause)
        if (fromPause)
            i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        return i
    }

    /**
     * Lab completo: inicio, catálogo, arcade y Redes y seguridad. Se abre en la tarea de Foco;
     * "atrás" vuelve a "Hoy".
     */
    @JvmStatic fun full(context: Context): Intent = Intent(context, LabActivity::class.java)

    /**
     * Lab completo desde la pausa (contexto de servicio): tarea propia, fuera de Recientes; al
     * salir vuelve la app de debajo y su pausa.
     */
    @JvmStatic
    fun fullFromPause(context: Context): Intent =
        full(context)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )

    /**
     * Lote C: abre Lab directamente en el cofre ganado (desde la pausa: tarea propia, fuera de
     * Recientes, como [fullFromPause]). Después del cofre se ofrece la práctica de 3 min, no el
     * catálogo.
     */
    @JvmStatic
    fun chest(context: Context, fromPause: Boolean): Intent {
        val i =
            Intent(context, LabActivity::class.java)
                .putExtra(EXTRA_CHEST, true)
                .putExtra(EXTRA_FROM_PAUSE, fromPause)
        if (fromPause)
            i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        return i
    }

    @JvmStatic fun isChest(intent: Intent?) = intent?.getBooleanExtra(EXTRA_CHEST, false) == true

    @JvmStatic
    fun isShort(intent: Intent?) = intent?.getBooleanExtra(EXTRA_SHORT_SESSION, false) == true

    @JvmStatic
    fun fromPause(intent: Intent?) = intent?.getBooleanExtra(EXTRA_FROM_PAUSE, false) == true
}
