package personal.qqq.braintraining

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Foco 1.0, etapa 1: cola de retos y logros para el log del Inspector de Foco (misma app y mismo
 * proceso, sin dependencia de :app). CONTRATO: preferencias "foco-inspector-lab", clave "queue" con
 * una lista JSON de {ts, type ("reto_completado" | "logro_desbloqueado"), game, value, detail, id}.
 * Foco la lee y la vacía (es.foco.next.log.InspectorLog#drainLab); [id] evita que un mismo hecho se
 * registre dos veces. Nunca lanza: si la cola falla, la práctica se guarda igual.
 */
internal object InspectorQueue {
    const val PREFS = "foco-inspector-lab"
    const val KEY = "queue"
    const val RETO = "reto_completado"
    const val LOGRO = "logro_desbloqueado"
    /** Si Foco no la vacía (no debería pasar), la cola no crece sin límite. */
    private const val MAX = 300

    @Synchronized
    fun add(context: Context, type: String, game: String, value: Int, detail: String, id: String) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val queue =
                try {
                    JSONArray(prefs.getString(KEY, null) ?: "[]")
                } catch (e: Exception) {
                    JSONArray()
                }
            if (queue.length() < MAX) {
                queue.put(
                    JSONObject()
                        .put("ts", System.currentTimeMillis())
                        .put("type", type)
                        .put("game", game)
                        .put("value", value)
                        .put("detail", detail)
                        .put("id", id)
                )
                prefs.edit().putString(KEY, queue.toString()).apply()
            }
        } catch (e: Exception) {
            /* best effort */
        }
    }
}
