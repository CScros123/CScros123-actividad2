package personal.qqq.braintraining

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import personal.qqq.braintraining.core.Area
import personal.qqq.braintraining.core.Consistency
import personal.qqq.braintraining.core.Games
import personal.qqq.braintraining.core.LabDay
import personal.qqq.braintraining.data.ResultRow
import personal.qqq.braintraining.data.Store

/**
 * Fase 5: resumen de solo lectura de Lab para la tarjeta "Tu práctica" de Foco (Java). Nunca
 * escribe en `brain-training.db` y no la crea si no existe. Llamar SIEMPRE fuera del hilo
 * principal.
 */
class PracticeSummary(
    @JvmField val sessionsToday: Int,
    /** Días distintos con práctica en total: nunca baja (sustituye a la racha). */
    @JvmField val practicedDays: Int,
    /** Frase de [Consistency]: semana actual frente a la media propia. */
    @JvmField val rhythm: String,
    @JvmField val netsecRank: String,
    @JvmField val netsecPoints: Int,
    @JvmField val totalSessions: Int,
) {
    fun isEmpty(): Boolean = totalSessions == 0

    companion object {
        const val DATABASE = "brain-training.db"
        @JvmField val EMPTY = PracticeSummary(0, 0, "", netsecRank(0).first, 0, 0)

        /**
         * Lee Room en el hilo que llama (bloqueante). Si Lab nunca se abrió, devuelve [EMPTY] sin
         * crear la base.
         */
        @JvmStatic
        fun load(context: Context): PracticeSummary {
            val app = context.applicationContext
            if (!app.getDatabasePath(DATABASE).exists()) return EMPTY
            val store = Store(app)
            try {
                val (rows, zoneId) =
                    runBlocking {
                        store.dao.allResults() to
                            store.dao.allPreferences().firstOrNull { it.key == "zone" }?.value
                    }
                val zone =
                    runCatching { ZoneId.of(zoneId ?: ZoneId.systemDefault().id) }
                        .getOrDefault(ZoneId.systemDefault())
                return from(rows, LabDay.today(zone), zone)
            } finally {
                runCatching { store.close() }
            }
        }

        /**
         * Cálculo puro (probado en PracticeSummaryTest). `internal`: Foco (Java) no ve tipos de
         * :lab-data. Constancia acumulada: un día de descanso no borra nada.
         */
        internal fun from(rows: List<ResultRow>, today: LocalDate, zone: ZoneId): PracticeSummary {
            val days = rows.map { LabDay.of(it.ended, zone) }
            val c = Consistency.summarize(days, today)
            // Juegos desconocidos (p. ej. añadidos en otra versión) no rompen el resumen.
            val points =
                rows
                    .filter { row ->
                        runCatching { Games.get(row.game).area }.getOrNull() == Area.NETSEC
                    }
                    .sumOf { it.xp }
            return PracticeSummary(
                days.count { it == today },
                c.practicedDays,
                c.message(),
                netsecRank(points).first,
                points,
                rows.size,
            )
        }
    }
}
