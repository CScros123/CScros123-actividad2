package personal.qqq.braintraining.core

/** A safety ceiling for a single foreground round; pauses discard an unfinished round. */
const val MAX_ROUND_ACTIVE_MS = 86_400_000L

data class TrialEvidence(val correct: Boolean, val reason: String, val responseMs: Long? = null)

data class ErrorProfile(val wrong: Int, val timeout: Int, val early: Int, val implausible: Int)

data class PracticeSample(
    val ended: Long,
    val level: Int,
    val mode: String,
    val correct: Int,
    val total: Int,
)

data class PracticeComparison(
    val level: Int,
    val mode: String,
    val beforePercent: Double,
    val afterPercent: Double,
    val sessions: Int,
) {
    val changePoints
        get() = afterPercent - beforePercent
}

object Evaluation {
    fun errors(trials: List<TrialEvidence>) =
        ErrorProfile(
            trials.count { !it.correct && it.reason == "wrong" },
            trials.count { !it.correct && it.reason == "timeout" },
            trials.count { !it.correct && it.reason == "early" },
            trials.count { !it.correct && it.reason == "implausible" },
        )

    /**
     * Six comparable sessions: three earlier vs three later. No inference across modes or levels.
     */
    fun compare(samples: List<PracticeSample>): PracticeComparison? {
        val ordered = samples.sortedBy { it.ended }
        val latest = ordered.lastOrNull() ?: return null
        if (latest.mode !in setOf("timed", "untimed")) return null
        val matching =
            ordered
                .filter {
                    it.level == latest.level &&
                        it.mode == latest.mode &&
                        it.total > 0 &&
                        it.correct in 0..it.total
                }
                .takeLast(6)
        if (matching.size < 6) return null
        fun accuracy(rows: List<PracticeSample>) =
            100.0 * rows.sumOf { it.correct.toLong() } / rows.sumOf { it.total.toLong() }
        return PracticeComparison(
            latest.level,
            latest.mode,
            accuracy(matching.take(3)),
            accuracy(matching.takeLast(3)),
            6,
        )
    }

    fun advice(game: String, errors: ErrorProfile): String {
        if (errors.early + errors.implausible > 0)
            return "Espera la señal y prioriza una respuesta deliberada antes de buscar velocidad."
        if (errors.timeout > errors.wrong)
            return "Practica la regla paso a paso. Si este juego permite aprender sin cronómetro, puedes activarlo en Ajustes."
        if (errors.wrong > 0)
            return when (Games.get(game).area) {
                Area.MATH,
                Area.REAL ->
                    "Revisa la solución, identifica el paso que falló y comprueba el resultado antes de responder."
                Area.MEMORY ->
                    "Agrupa los elementos y repite mentalmente la secuencia antes de contestar."
                else -> "Explica la regla con tus palabras y comprueba qué opción la cumple."
            }
        return "Mantén la estrategia que te ayudó y observa si se sostiene en próximas sesiones."
    }
}
