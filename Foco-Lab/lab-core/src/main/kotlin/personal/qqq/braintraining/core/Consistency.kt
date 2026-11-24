package personal.qqq.braintraining.core

import java.time.LocalDate

/**
 * Constancia sin castigo: sustituye a la "racha" de días seguidos, que caía a 0 tras un día de
 * descanso. Aquí nada resta: [practicedDays] solo crece, y la semana actual se compara con TU media
 * de las 4 semanas anteriores, no con un ideal. Lógica pura, probada en ConsistencyTest.
 */
object Consistency {
    /** Días de la semana actual (hoy y los 6 anteriores). */
    const val WEEK_DAYS = 7

    /** Semanas anteriores que forman la línea base (aprox. "hace un mes"). */
    const val BASELINE_WEEKS = 4

    /** Margen de tolerancia: quedarse hasta este número de días por debajo de la media sigue contando como ritmo mantenido. */
    const val STEADY_MARGIN = 1.0

    data class Summary(
        /** Días distintos con práctica, en total. Nunca baja. */
        val practicedDays: Int,
        /** Días con práctica en la semana actual. */
        val recentDays: Int,
        /** Media de días por semana en las [BASELINE_WEEKS] anteriores; null sin historia previa. */
        val baselineDays: Double?,
    ) {
        /** Ritmo mantenido respecto a la propia media (o primera semana: siempre en positivo). */
        val steady: Boolean
            get() = baselineDays == null || recentDays + STEADY_MARGIN >= baselineDays

        /** Frase amable para mostrar. Nunca habla de fallar ni de perder. */
        fun message(): String =
            when {
                practicedDays == 0 -> "Cuando quieras empezar, aquí estará."
                baselineDays == null -> "Empezando a construir tu ritmo."
                recentDays > baselineDays + STEADY_MARGIN -> "Esta semana vas por encima de tu media."
                steady -> "Mantienes tu ritmo habitual."
                else -> "Semana más tranquila que tu media: lo practicado sigue sumando."
            }
    }

    fun summarize(days: Collection<LocalDate>, today: LocalDate): Summary {
        val set = days.filter { !it.isAfter(today) }.toSet()
        val weekStart = today.minusDays(WEEK_DAYS - 1L)
        val recent = set.count { !it.isBefore(weekStart) }
        val baseStart = weekStart.minusDays(WEEK_DAYS.toLong() * BASELINE_WEEKS)
        val before = set.filter { it.isBefore(weekStart) }
        val baseline =
            if (before.isEmpty()) null
            else {
                // Solo las semanas en las que ya existía historia cuentan en la media.
                val first = maxOf(before.min(), baseStart)
                val span = java.time.temporal.ChronoUnit.DAYS.between(first, weekStart)
                val weeks = ((span + WEEK_DAYS - 1) / WEEK_DAYS).coerceIn(1, BASELINE_WEEKS.toLong())
                val from = weekStart.minusDays(weeks * WEEK_DAYS)
                before.count { !it.isBefore(from) }.toDouble() / weeks
            }
        return Summary(set.size, recent, baseline)
    }
}
