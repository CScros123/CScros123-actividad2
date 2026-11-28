package personal.qqq.braintraining

import android.content.Context

/**
 * Lote C: lectura mínima para la pausa de Foco (Java). Lab copia aquí el número de cofres ganados
 * sin abrir cada vez que cambia (la fuente de verdad es la pref "rewards" de Room). Leerlo es una
 * lectura de SharedPreferences, sin abrir la base de datos: se puede llamar al dibujar la pausa.
 * Foco solo lee: un cofre nunca cambia minutos, pausas ni bloqueos, y Lab no toca RuleStore ni
 * Repository de Foco.
 */
object ChestSummary {
    private const val FILE = "lab-chest"
    private const val PENDING = "pending"

    /** Cofres ganados practicando y aún sin abrir (0 si Lab nunca se abrió o si algo falla). */
    @JvmStatic
    fun pending(context: Context): Int =
        runCatching {
                context.applicationContext
                    .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getInt(PENDING, 0)
                    .coerceAtLeast(0)
            }
            .getOrDefault(0)

    @JvmStatic fun available(context: Context): Boolean = pending(context) > 0

    internal fun write(context: Context, pending: Int) {
        context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(PENDING, pending.coerceAtLeast(0))
            .apply()
    }
}
