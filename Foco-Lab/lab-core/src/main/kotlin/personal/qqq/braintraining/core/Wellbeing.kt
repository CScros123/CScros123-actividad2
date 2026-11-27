package personal.qqq.braintraining.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Check-in de energía (un toque, sin obligación). Con batería baja Lab no exige lo mismo: el plan
 * del día se reduce a un solo reto corto. Nunca bloquea nada: el resto de juegos sigue a mano, la
 * última palabra es tuya.
 */
enum class Energy(val label: String, val hint: String, val planSize: Int, val timed: Boolean) {
    LOW(
        "Baja",
        "Solo un reto corto; los juegos sin cronómetro siguen a mano. Descansar también cuenta.",
        1,
        false,
    ),
    NORMAL("Normal", "Tu plan de siempre.", Int.MAX_VALUE, true),
    HIGH("Alta", "Tu plan de siempre; si te apetece, prueba un nivel más.", Int.MAX_VALUE, true);

    /** Plan del día recortado a lo que pide esta energía. */
    fun <T> trim(plan: List<T>): List<T> = plan.take(planSize.coerceAtLeast(1))
}

/**
 * Cierre flexible del día: lo que haces antes de las [CLOSE_HOUR] cuenta para el día anterior, así
 * no hay carrera contra la medianoche.
 */
object LabDay {
    const val CLOSE_HOUR = 3L

    fun of(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(CLOSE_HOUR).toLocalDate()

    fun today(zone: ZoneId, nowMs: Long = System.currentTimeMillis()): LocalDate = of(nowMs, zone)
}

/**
 * Ritmo suave (Elo simplificado) para juegos de velocidad como Pulso: sube un paso cada
 * [HITS_TO_RISE] aciertos y BAJA un paso tras [MISSES_TO_EASE] fallos seguidos, para mantenerte en
 * flujo. Sustituye a una velocidad que solo crecía con los aciertos.
 */
data class SoftPace(val level: Int = 0, val hitsInRow: Int = 0, val missesInRow: Int = 0) {
    fun hit(): SoftPace =
        if (hitsInRow + 1 >= HITS_TO_RISE) SoftPace((level + 1).coerceAtMost(MAX_LEVEL), 0, 0)
        else copy(hitsInRow = hitsInRow + 1, missesInRow = 0)

    fun miss(): SoftPace =
        if (missesInRow + 1 >= MISSES_TO_EASE) SoftPace((level - 1).coerceAtLeast(0), 0, 0)
        else copy(missesInRow = missesInRow + 1, hitsInRow = 0)

    /** Milisegundos que la luz espera antes de cambiar. */
    val ms: Int
        get() = START_MS - level * STEP_MS

    companion object {
        const val HITS_TO_RISE = 4
        const val MISSES_TO_EASE = 2
        const val START_MS = 1100
        const val STEP_MS = 70
        /** Techo: 1100 - 9*70 = 470 ms (antes, 450 sin vuelta atrás). */
        const val MAX_LEVEL = 9
    }
}
