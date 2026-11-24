package personal.qqq.braintraining

import android.content.Context
import kotlinx.coroutines.runBlocking
import personal.qqq.braintraining.core.Games
import personal.qqq.braintraining.core.NetSec
import personal.qqq.braintraining.data.ProgressRow
import personal.qqq.braintraining.data.ResultRow
import personal.qqq.braintraining.data.Store

/**
 * Foco Home, etapa 4.3: "Reto rápido". El juego de Cognitive Lab que lleva más días sin jugarse
 * (nunca jugado = primero), entre los de Mente y los de Redes y seguridad ya desbloqueados, con su
 * nivel guardado. Solo lectura de `brain-training.db`; no la crea. Llamar SIEMPRE fuera del hilo
 * principal (como [PracticeSummary]).
 */
class QuickChallenge(
    @JvmField val game: String,
    @JvmField val title: String,
    @JvmField val level: Int,
    /** Días desde la última partida; -1 si nunca se jugó. */
    @JvmField val daysSince: Int,
) {
    companion object {
        @JvmStatic
        fun load(context: Context): QuickChallenge {
            val app = context.applicationContext
            if (!app.getDatabasePath(PracticeSummary.DATABASE).exists())
                return pick(emptyList(), emptyList(), System.currentTimeMillis())
            val store = Store(app)
            try {
                val (rows, progress) =
                    runBlocking { store.dao.allResults() to store.dao.allProgress() }
                return pick(rows, progress, System.currentTimeMillis())
            } finally {
                runCatching { store.close() }
            }
        }

        /** Cálculo puro. */
        internal fun pick(
            rows: List<ResultRow>,
            progress: List<ProgressRow>,
            now: Long,
        ): QuickChallenge {
            val candidates =
                MIND_GAMES.map { it.id } +
                    NetSec.ids.filter { netsecUnlocked(rows, it) && it !in Games.retired }
            val last = candidates.associateWith { id -> rows.filter { it.game == id }.maxOfOrNull { it.ended } }
            val id =
                candidates.minWithOrNull(compareBy<String> { last[it] ?: Long.MIN_VALUE }) ?: NetSec.ids.first()
            val ended = last[id]
            val days = if (ended == null) -1 else ((now - ended) / 86_400_000L).toInt()
            val level = (progress.firstOrNull { it.game == id }?.level ?: 1).coerceIn(1, 10)
            return QuickChallenge(id, Games.get(id).title, level, days)
        }
    }
}
