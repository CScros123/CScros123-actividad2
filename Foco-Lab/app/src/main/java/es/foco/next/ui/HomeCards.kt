@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package es.foco.next.ui

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.AbsoluteRules
import java.time.format.TextStyle
import java.util.Locale

val CardShape = RoundedCornerShape(22.dp)

/**
 * Micro-interacción: al pulsar, el elemento se hunde un 3 % con un muelle (se lee en la fase de
 * dibujo: no recompone). Se usa en botones y en el dock.
 */
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.InteractionSource): Modifier =
    composed {
        val pressed by interaction.collectIsPressedAsState()
        val k by animateFloatAsState(if (pressed) .97f else 1f, spring(dampingRatio = .6f, stiffness = 700f), label = "pulsar")
        graphicsLayer {
            scaleX = k
            scaleY = k
        }
    }

/** Tarjeta base del inicio: superficie de la paleta, borde de luz y sombra suave. */
@Composable
fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalHome.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            // Temas fotográficos: la tarjeta respeta su translucidez para que se vea la foto.
            .background(if (c.card.alpha > .99f) c.card.copy(alpha = .96f) else c.card)
            .border(1.dp, Color.White.copy(alpha = if (c.moon || c.card.red + c.card.green + c.card.blue < 1.2f) .07f else .55f), CardShape)
            .padding(16.dp)
    ) { content() }
}

@Composable
fun HomeText(text: String, size: TextUnit = 13.sp, color: Color = LocalHome.current.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, lineHeight = size * 1.3f)
}

@Composable
fun IconBadge(icon: ImageVector, size: Int = 32) {
    val c = LocalHome.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * .36f).dp)).background(lerp(c.card, c.accent, .12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size((size / 2).dp)) }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(press)
            .clip(RoundedCornerShape(16.dp))
            .background(c.accent)
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = c.card, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

// ===================== Hoy (sin banco desde la v26) =====================

@Composable
fun TodayCard(
    investedToday: Long,
    retosToday: Int,
    week: List<DayBar>,
    showNumbers: Boolean,
    goodToday: String,
) {
    val c = LocalHome.current
    var open by rememberSaveable { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        if (open) 180f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = .7f, stiffness = 400f),
        label = "flecha",
    )
    HomeCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.HourglassBottom)
                // Lo bueno primero: lo invertido y los retos. En CUIDADO, solo frases.
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    HomeText("Hoy", 15.sp, weight = FontWeight.SemiBold)
                    HomeText(
                        if (showNumbers) "Invertido: ${minutes(investedToday)}" + (if (retosToday > 0) " · retos: $retosToday" else "")
                        else goodToday,
                        12.5.sp, c.muted,
                    )
                    if (showNumbers && goodToday.isNotEmpty()) HomeText(goodToday, 11.5.sp, c.muted)
                }
            }
            // Resumen semanal desplegable (frente a tu propia media, sin metas ideales).
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeText("Esta semana", 12.sp, c.muted, FontWeight.Medium, Modifier.weight(1f))
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                    if (open) "Cerrar resumen semanal" else "Ver resumen semanal",
                    tint = c.muted,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrow },
                )
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                WeekSummary(week, showNumbers)
            }
        }
    }
}

@Composable
private fun WeekSummary(week: List<DayBar>, showNumbers: Boolean = true) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    if (week.isEmpty()) {
        HomeText("Aún sin datos de la semana.", 12.sp, c.muted)
        return
    }
    val invested = week.sumOf { it.investedMin }
    val ocio = week.sumOf { it.ocioMin }
    val first = week.take(3).sumOf { it.investedMin } / 3.0
    val last = week.takeLast(3).sumOf { it.investedMin } / 3.0
    // Día tocado (índice) o null: el texto de abajo muestra su detalle.
    var picked by remember(week) { mutableStateOf<Int?>(null) }
    Column(Modifier.padding(top = 6.dp)) {
        val max = (week.maxOf { maxOf(it.investedMin, it.ocioMin) }).coerceAtLeast(1)
        // Escala suavizada: altura = √(valor / máximo). Un pico de ocio ya no aplasta las barras
        // de lo invertido; los datos y los textos siguen siendo los reales.
        fun h(v: Long) = kotlin.math.sqrt(v.toFloat() / max)
        val sel = picked
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val slot = size.width / week.size
            val bw = slot * .28f
            week.forEachIndexed { i, d ->
                val x = slot * i + slot / 2
                val dim = if (sel == null || sel == i) 1f else .35f
                val hi = size.height * h(d.investedMin)
                val ho = size.height * h(d.ocioMin)
                drawRoundRect(c.accent.copy(alpha = dim), Offset(x - bw - 1.dp.toPx(), size.height - hi), Size(bw, hi), CornerRadius(3.dp.toPx()))
                drawRoundRect(c.muted.copy(alpha = .45f * dim), Offset(x + 1.dp.toPx(), size.height - ho), Size(bw, ho), CornerRadius(3.dp.toPx()))
            }
        }
        // Letras de los días: tocables (tocar otra vez el mismo día vuelve al resumen).
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, d ->
                val on = sel == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (on) lerp(c.card, c.accent, .16f) else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            picked = if (on) null else i
                        }
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "Ver " + d.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es")) },
                    contentAlignment = Alignment.Center,
                ) {
                    HomeText(
                        d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es")).uppercase(),
                        10.5.sp, if (on) c.accent else c.muted, if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.animation.AnimatedContent(sel, label = "detalle") { i ->
            Column {
                if (i != null) {
                    val d = week[i]
                    val name = d.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es")).replaceFirstChar { it.uppercase() }
                    HomeText(
                        if (showNumbers) "$name: ${minutes(d.investedMin)} invertidos · ${minutes(d.ocioMin)} de ocio"
                        else "$name: " + if (d.investedMin >= d.ocioMin) "más tiempo para lo tuyo que para el ocio" else "un día más de descanso",
                        12.sp, c.text, FontWeight.Medium,
                    )
                } else {
                    if (showNumbers) HomeText("Invertido: ${minutes(invested)} · ocio: ${minutes(ocio)}", 12.sp, c.text, FontWeight.Medium)
                    HomeText(
                        when {
                            invested == 0L -> "La semana empieza cuando tú quieras."
                            last >= first -> "Mantienes o mejoras tu ritmo de inicio de semana."
                            else -> "Días más tranquilos que al principio: lo invertido sigue sumando."
                        },
                        11.5.sp, c.muted,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            HomeText("  invertido   ", 10.5.sp, c.muted)
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.muted.copy(alpha = .45f)))
            HomeText("  ocio · toca un día para verlo", 10.5.sp, c.muted)
        }
    }
}

// ===================== Reto rápido =====================

@Composable
fun QuickCard(q: QuickInfo, night: Boolean, onPlay: () -> Unit) {
    val c = LocalHome.current
    // v26: entrenar la mente no da minutos; el búho solo lo recuerda.
    HomeCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Luggage)
                HomeText(q.title, 14.5.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(lerp(c.card, c.accent, .12f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    HomeText("Nv. ${q.level}", 10.5.sp, c.accent, FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(9.dp))
            HomeText(
                (if (night) "Un reto suave antes de dormir, sin prisa." else "Dos minutos para agilizar la mente.") +
                    when {
                        q.daysSince < 0 -> " Aún no lo has jugado."
                        q.daysSince > 0 -> " Lleva ${q.daysSince} ${if (q.daysSince == 1) "día" else "días"} sin jugarse."
                        else -> ""
                    },
                13.sp, lerp(c.text, c.muted, .5f),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Jugar ahora", onClick = onPlay)
        }
    }
}

// ===================== Despertar, noche y rol =====================

@Composable
fun NightCard(e: es.foco.next.runtime.HomeHub.EstadoActual) {
    val c = LocalHome.current
    HomeCard {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            HomeText("Resumen de hoy", 16.sp, weight = FontWeight.SemiBold)
            HomeText("Invertido: ${e.minutosInvertidosHoy} min · ocio: ${e.ocioHoyMin} min", 13.sp)
            HomeText("Días cumplidos en total: ${e.rachaDias}", 13.sp, c.muted)
            if (e.extraHoyMin > 0)
                HomeText("Ocio por encima del límite hoy: ${e.extraHoyMin} min", 13.sp, c.muted)
            HomeText(e.motivo, 14.sp, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RoleCard(onRequest: () -> Unit) {
    val c = LocalHome.current
    HomeCard {
        Column {
            HomeText("Usa Foco como pantalla de inicio", 15.sp, weight = FontWeight.SemiBold)
            HomeText("Android te pedirá confirmarlo. Puedes volver a tu inicio de siempre en Ajustes › Aplicaciones › Aplicación de inicio.", 12.5.sp, c.muted)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Usar Foco como inicio", onClick = onRequest)
        }
    }
}
