package personal.qqq.braintraining.core

import java.time.*
import java.time.temporal.TemporalAdjusters

/** Rewards are derived from immutable completed evidence, never minted on refresh. */
data class EventSession(val id: String, val game: String, val ended: Long)

data class AppliedPractice(val date: LocalDate, val note: String, val ended: Long)

enum class EventPeriod {
    DAILY,
    WEEKLY,
}

data class Mission(
    val id: String,
    val period: EventPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val title: String,
    val description: String,
    val target: Int,
    val progress: Int,
    val unit: String,
    val reward: Int,
    val game: String? = null,
    val practical: Boolean = false,
    val badge: String? = null,
    val completedAt: Long? = null,
) {
    val complete
        get() = completedAt != null

    val fraction
        get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class EventSnapshot(
    val today: LocalDate,
    val daily: List<Mission>,
    val weekly: List<Mission>,
    val journal: List<Mission>,
) {
    val bonusXp
        get() = journal.sumOf { it.reward }

    val badges
        get() = journal.mapNotNull { it.badge }.distinct()
}

object EventEngine {
    fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun build(
        today: LocalDate,
        start: LocalDate,
        sessions: List<EventSession>,
        applied: List<AppliedPractice>,
        zone: ZoneId,
    ): EventSnapshot {
        val first = minOf(start, today)
        val valid =
            sessions
                .distinctBy { it.id }
                .filter {
                    Instant.ofEpochMilli(it.ended).atZone(zone).toLocalDate() in first..today
                }
                .sortedWith(compareBy<EventSession> { it.ended }.thenBy { it.id })
        val byDay = valid.groupBy { Instant.ofEpochMilli(it.ended).atZone(zone).toLocalDate() }
        val notes =
            applied
                .filter { it.date in first..today }
                .distinctBy { it.date }
                .associateBy { it.date }
        val dates = (byDay.keys + notes.keys + today).sorted()
        val byWeek =
            valid.groupBy { monday(Instant.ofEpochMilli(it.ended).atZone(zone).toLocalDate()) }
        val notesByWeek = notes.values.groupBy { monday(it.date) }
        val math = listOf("math", "percent", "fraction", "equation", "ratio").map { Games.get(it) }
        val real = listOf("budget", "unitprice", "planning").map { Games.get(it) }
        fun daily(d: LocalDate): List<Mission> {
            val a = math[Math.floorMod(d.toEpochDay(), math.size.toLong()).toInt()]
            val b = real[Math.floorMod(d.toEpochDay() + 1, real.size.toLong()).toInt()]
            val rows = byDay[d].orEmpty()
            fun game(g: Game, label: String, xp: Int): Mission {
                val matches = rows.filter { it.game == g.id }
                return Mission(
                    "d:$d:${g.id}",
                    EventPeriod.DAILY,
                    d,
                    d,
                    label,
                    "Completa una sesión de ${g.title}. Revisa las soluciones para aprender de tus respuestas.",
                    1,
                    minOf(1, matches.size),
                    "sesión",
                    xp,
                    g.id,
                    completedAt = matches.firstOrNull()?.ended,
                )
            }
            val task = RealLifePractice.forDay(d.toEpochDay())
            return listOf(
                game(a, "Matemáticas con método", 25),
                game(b, "Resuelve una situación", 20),
                Mission(
                    "d:$d:apply",
                    EventPeriod.DAILY,
                    d,
                    d,
                    task.first,
                    task.second,
                    1,
                    if (notes[d] != null) 1 else 0,
                    "aplicación",
                    35,
                    practical = true,
                    completedAt = notes[d]?.ended,
                ),
            )
        }
        fun weekly(w: LocalDate): List<Mission> {
            val end = w.plusDays(6)
            val rows = byWeek[w].orEmpty()
            val mathRows = rows.filter { Games.get(it.game).area == Area.MATH }
            val noteRows = notesByWeek[w].orEmpty().sortedBy { it.ended }
            val practiceDates =
                (rows.map { Instant.ofEpochMilli(it.ended).atZone(zone).toLocalDate() } +
                        noteRows.map { it.date })
                    .distinct()
            val dayTarget =
                minOf(
                    3,
                    (end.toEpochDay() - maxOf(first, w).toEpochDay() + 1).toInt().coerceAtLeast(1),
                )
            val stamps =
                (rows.map {
                        Instant.ofEpochMilli(it.ended).atZone(zone).toLocalDate() to it.ended
                    } + noteRows.map { it.date to it.ended })
                    .sortedBy { it.second }
                    .distinctBy { it.first }
            val taskTarget =
                minOf(
                    2,
                    (end.toEpochDay() - maxOf(first, w).toEpochDay() + 1).toInt().coerceAtLeast(1),
                )
            val weekNumber = w.toEpochDay() / 7
            val selected = math[Math.floorMod(weekNumber, math.size.toLong()).toInt()]
            return listOf(
                Mission(
                    "w:$w:math",
                    EventPeriod.WEEKLY,
                    w,
                    end,
                    "Exploración matemática",
                    "Completa 4 sesiones de matemáticas durante la semana, a tu ritmo.",
                    4,
                    minOf(4, mathRows.size),
                    "sesiones",
                    80,
                    selected.id,
                    badge = "Exploración matemática",
                    completedAt = mathRows.getOrNull(3)?.ended,
                ),
                Mission(
                    "w:$w:days",
                    EventPeriod.WEEKLY,
                    w,
                    end,
                    "Vuelve cuando puedas",
                    "Practica o aplica una idea en $dayTarget ${if(dayTarget==1)"día" else "días"} de esta semana. No tienen que ser seguidos.",
                    dayTarget,
                    minOf(dayTarget, practiceDates.size),
                    "días",
                    60,
                    selected.id,
                    badge = "Constancia flexible",
                    completedAt = stamps.getOrNull(dayTarget - 1)?.second,
                ),
                Mission(
                    "w:$w:apply",
                    EventPeriod.WEEKLY,
                    w,
                    end,
                    "Del aprendizaje a tu vida",
                    "Registra una aplicación concreta en $taskTarget ${if(taskTarget==1)"día" else "días distintos"}. Puede ser una compra, un presupuesto o una tarea.",
                    taskTarget,
                    minOf(taskTarget, noteRows.size),
                    "aplicaciones",
                    90,
                    practical = true,
                    badge = "Aplicación real",
                    completedAt = noteRows.getOrNull(taskTarget - 1)?.ended,
                ),
            )
        }
        val dayMissions = dates.flatMap { daily(it) }
        val weekMissions = dates.map { monday(it) }.distinct().flatMap { weekly(it) }
        val journal =
            (dayMissions + weekMissions)
                .filter { it.complete }
                .sortedWith(compareByDescending<Mission> { it.completedAt }.thenBy { it.id })
        return EventSnapshot(today, daily(today), weekly(monday(today)), journal)
    }
}
