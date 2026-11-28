package personal.qqq.braintraining

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.delay
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/** Areas shown in progress views, in the order the user sees them. */
val trainedAreas =
    listOf(
        Area.ATTENTION,
        Area.LOGIC,
        Area.MATH,
        Area.REAL,
        Area.MEMORY,
        Area.SPEED,
        Area.NETSEC,
        Area.ARCADE,
    )

/**
 * Points per area: XP of every finished exercise. A plant grows one stage every 60 points (max 5).
 */
fun areaPoints(results: List<ResultRow>, area: Area) =
    results.filter { Games.get(it.game).area == area }.sumOf { it.xp }

fun plantStage(points: Int) = (points / 60).coerceAtMost(5)

@Composable
fun AreaChip(area: Area, modifier: Modifier = Modifier) {
    val color = areaColor(area)
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = .16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(areaIcon(area), null, Modifier.size(16.dp), tint = color)
        Text(
            area.title.uppercase(Locale("es")),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

// ---------- Result screen ----------

private fun phrase(accuracy: Float, delta: Int?): String =
    when {
        accuracy >= .95f -> "Impecable. Tu mente estaba en modo enfoque."
        delta != null && delta >= 2 ->
            "Mejoraste respecto a la última vez. Así se construye el hábito."
        accuracy >= .8f -> "Muy bien. Estás dominando este ejercicio."
        accuracy >= .6f -> "Buen trabajo. Cada ronda afina tu estrategia."
        else -> "Lo importante es practicar. Revisa las soluciones y vuelve mañana."
    }

@Composable
fun ResultScreen(
    r: ResultRow,
    previous: ResultRow?,
    planIndex: Int,
    planSize: Int,
    hasNext: Boolean,
    next: () -> Unit,
    home: () -> Unit,
    history: List<ResultRow> = emptyList(),
    mistakes: List<String> = emptyList(),
    best: Int? = null,
    againInRow: Int = 0,
    again: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    val game = Games.get(r.game)
    val color = areaColor(game.area)
    val motion = LocalMotionEnabled.current
    // Paso 2: each game's own measure (packets and points for Turno anti-DDoS, aciertos for the
    // rest).
    val head = remember(r.id) { headline(r) }
    val accuracy = head.value.toFloat() / head.total.coerceAtLeast(1)
    val delta =
        previous?.let(::headline)?.takeIf { it.noun == head.noun }?.let { head.score - it.score }
    // Score counts up and the ring fills.
    val shown = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(150)
            shown.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AreaChip(game.area)
        if (planSize > 1)
            Text(
                "Entrenamiento de hoy · ${planIndex} de $planSize",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceVariant
            Canvas(
                Modifier.fillMaxSize().semantics {
                    contentDescription = "${head.value} de ${head.total} ${head.noun}"
                }
            ) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    track,
                    -90f,
                    360f,
                    false,
                    Offset(inset, inset),
                    Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    Brush.sweepGradient(
                        listOf(color.copy(alpha = .6f), color, color.copy(alpha = .6f))
                    ),
                    -90f,
                    360f * accuracy * shown.value,
                    false,
                    Offset(inset, inset),
                    Size(size.width - stroke, size.height - stroke),
                    style =
                        androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(head.value * shown.value).roundToInt()}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "de ${head.total} ${head.noun}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(game.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            phrase(accuracy, delta),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (game.area == Area.NETSEC) NetsecResult(r, history)
        if (game.id == Suitcase.ID) SuitcaseResult(r, history)
        if (game.id == Vigil.ID) VigilResult(r, history)
        if (game.id == Ddos.ID) DdosResult(r, history)
        if (game.id == Route.ID) RouteResult(r, history)
        if (game.id == IncidentPairs.ID) PairsResult(r, history)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultStat(
                Icons.Rounded.Timer,
                "${r.activeMs / 60000}:${"%02d".format(r.activeMs / 1000 % 60)}",
                "tiempo",
                Modifier.weight(1f),
            )
            ResultStat(
                if (delta == null) Icons.Rounded.FiberNew
                else if (delta >= 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                when {
                    delta == null -> "Primera"
                    delta > 0 -> "+$delta"
                    else -> "$delta"
                },
                if (delta == null) "vez" else "vs. anterior",
                Modifier.weight(1f),
                if (delta != null && delta > 0) Color(0xFF2FCB7E)
                else if (delta != null && delta < 0) Color(0xFFFF6B6B) else null,
            )
            ResultStat(
                Icons.Rounded.Stairs,
                "N${r.nextLevel}",
                when {
                    r.nextLevel > r.level -> "¡sube!"
                    r.nextLevel < r.level -> "ajustado"
                    else -> "nivel"
                },
                Modifier.weight(1f),
            )
        }
        Text(
            "+${r.xp} puntos para tu jardín de ${game.area.title.lowercase(Locale("es"))}",
            color = color,
            style = MaterialTheme.typography.titleMedium,
        )
        // Lote C: near miss ("a 1 de tu récord") and what this practice earned (missions, chest,
        // cards).
        if (again != null)
            AgainOffer(
                head.score,
                best,
                againInRow,
                if (best != null && best - head.score == 1) head.unit else head.units,
                again,
            )
        extra()
        if (hasNext)
            PrimaryButton(
                "Siguiente ejercicio",
                next,
                true,
                ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                Icons.Rounded.ArrowForward,
            )
        if (hasNext) TextButton(home) { Text("Terminar por hoy") }
        else PrimaryButton("Volver al inicio", home, true, icon = Icons.Rounded.Home)
        if (game.area == Area.NETSEC && mistakes.isNotEmpty()) ReviewPanel(mistakes)
    }
}

@Composable
private fun ResultStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier,
    tint: Color? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.primary)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Home: daily training, streak, garden, weekly chart ----------

@Composable
fun StreakRow(vm: BrainViewModel) {
    val results by vm.results.collectAsStateWithLifecycle()
    val c = remember(results) { vm.consistency(results) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    // Antes: "Nivel 1 + puntos/200", que solo medía cuánto jugabas. Ahora: lo aprendido.
    val learned = remember(prefs) { vm.wallet.cards.size }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            Icons.Rounded.Spa,
            Color(0xFF7CC6A4),
            "${c.practicedDays} ${if (c.practicedDays == 1) "día" else "días"}",
            "practicados · ${c.recentDays} esta semana",
            Modifier.weight(1f),
        )
        StatTile(
            Icons.Rounded.MenuBook,
            Color(0xFF8FB8FF),
            "$learned ${if (learned == 1) "concepto" else "conceptos"}",
            "aprendidos de ${Album.cards.size}",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    modifier: Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Mental garden": one plant per area that grows as you earn points there. */
@Composable
fun GardenCard(vm: BrainViewModel) {
    val results by vm.results.collectAsStateWithLifecycle()
    val motion = LocalMotionEnabled.current
    val sway =
        if (motion) {
            val t = rememberInfiniteTransition(label = "Viento")
            t.animateFloat(
                    -1f,
                    1f,
                    infiniteRepeatable(
                        tween(2600, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "Balanceo",
                )
                .value
        } else 0f
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("TU JARDÍN MENTAL")
                Text("Cada área es una planta", style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Rounded.Yard, null, tint = Color(0xFF2FCB7E))
        }
        Row(
            Modifier.fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f),
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            trainedAreas.forEach { area ->
                val points = areaPoints(results, area)
                val stage = plantStage(points)
                val grow by
                    animateFloatAsState(
                        stage / 5f,
                        if (motion) tween(1200, easing = FastOutSlowInEasing) else snap(),
                        label = "Crecer",
                    )
                Canvas(
                    Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${area.title}: etapa $stage de 5, $points puntos"
                    }
                ) {
                    drawPlant(areaColor(area), grow, stage, sway)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            trainedAreas.forEach { a ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(areaIcon(a), null, Modifier.size(18.dp), tint = areaColor(a))
                    Text(
                        "${areaPoints(results, a)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "60 puntos hacen crecer una planta un nivel. Con 300 florece.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawPlant(color: Color, grow: Float, stage: Int, sway: Float) {
    val base = Offset(size.width / 2, size.height - 10f)
    // Pot
    drawRoundRect(
        Color(0xFF8A5A3C),
        Offset(base.x - size.width * .22f, base.y - 22f),
        Size(size.width * .44f, 24f),
        androidx.compose.ui.geometry.CornerRadius(6f),
    )
    val height = (size.height - 50f) * (.12f + .88f * grow)
    val top = Offset(base.x + sway * 6f * grow, base.y - 22f - height)
    val stem =
        Path().apply {
            moveTo(base.x, base.y - 22f)
            quadraticTo(base.x - 8f, base.y - 22f - height / 2, top.x, top.y)
        }
    drawPath(
        stem,
        Color(0xFF3FAF6A),
        style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = StrokeCap.Round),
    )
    // Leaves appear along the stem as it grows.
    val leaves = (1 + grow * 6).toInt()
    for (i in 0 until leaves) {
        val t = (i + 1f) / (leaves + 1f)
        val p = Offset(base.x + (top.x - base.x) * t, base.y - 22f - height * t)
        val side = if (i % 2 == 0) 1f else -1f
        rotate(side * (40f + sway * 6f), p) {
            drawOval(Color(0xFF4CC97A), Offset(p.x, p.y - 6f), Size(20f + 6f * grow, 11f))
        }
    }
    // Bud, then flower in the area's color.
    if (stage >= 5) {
        for (k in 0 until 6) {
            val a = k * 60f * PI.toFloat() / 180f
            drawCircle(color, 9f, top + Offset(cos(a) * 10f, sin(a) * 10f))
        }
        drawCircle(Color(0xFFFFE08A), 7f, top)
    } else drawCircle(color.copy(alpha = .4f + .6f * grow), 5f + 6f * grow, top)
}

/** Active minutes per day for the last 7 days. */
@Composable
fun WeeklyChart(vm: BrainViewModel) {
    val totals by vm.dailyTotals.collectAsStateWithLifecycle()
    val today = LocalDate.now(vm.zone)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val minutes = days.map { (totals[it] ?: 0L) / 60000f }
    val max = max(5f, minutes.maxOrNull() ?: 0f)
    val motion = LocalMotionEnabled.current
    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("ESTA SEMANA")
                Text(
                    "${minutes.sum().roundToInt()} minutos activos",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Icon(Icons.Rounded.BarChart, null, tint = MaterialTheme.colorScheme.primary)
        }
        Row(
            Modifier.fillMaxWidth().height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (minutes[i] > 0)
                        Text(
                            "${minutes[i].roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    Box(
                        Modifier.fillMaxWidth()
                            .height((90f * (minutes[i] / max) * reveal.value).coerceAtLeast(4f).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isToday)
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1EDB90), Color(0xFF14A86C))
                                    )
                                else
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = .6f),
                                        )
                                    )
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es")).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Check-in de energía: un toque, opcional. Con energía baja el plan del día queda en un reto corto
 * y se ocultan las misiones; todos los juegos siguen disponibles.
 */
@Composable
fun EnergyCheckIn(vm: BrainViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "¿Cómo vas de energía hoy?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Energy.values().forEach { e ->
                OutlinedButton(
                    { vm.updateEnergy(e) },
                    Modifier.weight(1f),
                    colors =
                        if (vm.energy == e)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                            )
                        else ButtonDefaults.outlinedButtonColors(),
                ) {
                    Text(e.label)
                }
            }
        }
        Text(
            vm.energy.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
