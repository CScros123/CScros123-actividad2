package personal.qqq.braintraining

import org.json.JSONArray
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

fun ResultRow.evidence(): List<TrialEvidence> {
    val array = JSONArray(trials)
    return (0 until array.length()).map { i ->
        val trial = array.getJSONObject(i)
        TrialEvidence(
            trial.getBoolean("correct"),
            trial.getString("reason"),
            if (trial.isNull("responseMs")) null else trial.getLong("responseMs"),
        )
    }
}

fun ResultRow.practiceMode(): String {
    val array = JSONArray(trials)
    val modes =
        (0 until array.length())
            .map { array.getJSONObject(it).optString("mode", "unknown") }
            .distinct()
    return modes.singleOrNull() ?: "unknown"
}

fun ResultRow.sample() = PracticeSample(ended, level, practiceMode(), correct, total)

fun ResultRow.evaluationText(): String {
    val trials = evidence()
    val errors = Evaluation.errors(trials)
    val label =
        when (practiceMode()) {
            "timed" -> "Con cronómetro"
            "untimed" -> "Sin cronómetro"
            else -> "Modo no registrado (sesión anterior o mixta)"
        }
    val response = median(trials.mapNotNull { it.responseMs })
    return "$label\nRespuestas incorrectas: ${errors.wrong}\nTiempo agotado: ${errors.timeout}\nSalidas anticipadas: ${errors.early}\nRespuestas demasiado rápidas: ${errors.implausible}" +
        (response?.let {
            "\nTiempo habitual de respuesta: ${it/1000.0} s (mediana de respuestas registradas)"
        } ?: "") +
        "\n\n" +
        Evaluation.advice(game, errors)
}
