package personal.qqq.braintraining.core

/**
 * Fase 2: práctica corta abierta desde Foco (pausa u "Hoy"). Hasta 6 retos o 3 minutos activos, lo
 * primero que ocurra. Lógica pura e inmutable; no da minutos de ocio ni cambia el nivel adaptativo
 * (Adaptive pide 12 rondas).
 */
data class ShortSession(val answered: Int = 0, val correct: Int = 0, val activeMs: Long = 0L) {
    val done
        get() = answered >= MAX_CHALLENGES || activeMs >= MAX_ACTIVE_MS

    val timeUp
        get() = activeMs >= MAX_ACTIVE_MS

    val remainingMs
        get() = (MAX_ACTIVE_MS - activeMs).coerceAtLeast(0L)

    /** Cuenta un reto respondido; después del límite no cambia nada. */
    fun answer(ok: Boolean): ShortSession =
        if (done) this else copy(answered = answered + 1, correct = correct + if (ok) 1 else 0)

    /**
     * Suma tiempo activo. Un salto grande (proceso suspendido, reloj) cuenta como mucho
     * MAX_TICK_MS.
     */
    fun tick(deltaMs: Long): ShortSession =
        if (done || deltaMs <= 0) this
        else
            copy(
                activeMs =
                    (activeMs + deltaMs.coerceAtMost(MAX_TICK_MS)).coerceAtMost(MAX_ACTIVE_MS)
            )

    companion object {
        const val MAX_CHALLENGES = 6
        const val MAX_ACTIVE_MS = 180_000L
        const val MAX_TICK_MS = 1_000L

        /**
         * Misma regla que la "Misión de hoy": entre los juegos de redes desbloqueados, el de menor
         * último acierto (nunca jugado primero) y después el menos reciente. Sin datos, el primero
         * (Bits).
         */
        fun mission(
            unlocked: List<String>,
            lastCorrect: Map<String, Int>,
            lastEnded: Map<String, Long>,
        ): String {
            val ids = unlocked.filter { it in NetSec.ids }.ifEmpty { listOf(NetSec.ids.first()) }
            return ids.minWith(
                compareBy<String> { lastCorrect[it] ?: -1 }.thenBy { lastEnded[it] ?: 0L }
            )
        }

        /**
         * Orden de los retos: empieza por la misión y rota por el resto de juegos desbloqueados.
         */
        fun plan(first: String, unlocked: List<String>, count: Int = MAX_CHALLENGES): List<String> {
            val pool = (listOf(first) + unlocked.filter { it in NetSec.ids }).distinct()
            return List(count.coerceAtLeast(0)) { pool[it % pool.size] }
        }
    }
}
