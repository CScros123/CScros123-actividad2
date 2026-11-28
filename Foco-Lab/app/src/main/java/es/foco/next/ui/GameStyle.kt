package es.foco.next.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * v39: Foco en «modo juego». Un solo sitio para la letra, los colores por acción, los tres
 * redondeos y los botones con relieve. Las pantallas no eligen colores sueltos: piden una acción.
 * El fondo (foto o paisaje del tema) no se toca.
 */

/** Rubik: títulos, números y botones (redonda y fuerte). */
val Rubik =
    FontFamily(
        Font(es.foco.next.R.font.rubik_medium, FontWeight.Medium),
        Font(es.foco.next.R.font.rubik_bold, FontWeight.Bold),
        Font(es.foco.next.R.font.rubik_extrabold, FontWeight.ExtraBold),
        Font(es.foco.next.R.font.rubik_black, FontWeight.Black),
    )

/** Lexend: texto corrido y tareas (pensada para leerse con menos esfuerzo). */
val Lexend =
    FontFamily(
        Font(es.foco.next.R.font.lexend_regular, FontWeight.Normal),
        Font(es.foco.next.R.font.lexend_medium, FontWeight.Medium),
        Font(es.foco.next.R.font.lexend_semibold, FontWeight.SemiBold),
    )

/** Bold o más → Rubik; lo demás → Lexend. Así HomeText cambia toda la app de una vez. */
fun familyFor(weight: FontWeight): FontFamily =
    if (weight.weight >= FontWeight.Bold.weight) Rubik else Lexend

/** Los tres redondeos: chips y casillas · botones · tarjetas y hojas. */
val GameSmall = RoundedCornerShape(10.dp)
val GameMid = RoundedCornerShape(16.dp)
val GameLarge = RoundedCornerShape(26.dp)

/** Altura del relieve: lo que baja el botón al pulsarlo. */
val GameDepth = 4.dp

/**
 * Un color por tipo de acción, siempre el mismo (cara, borde inferior y tinta). Viven dentro del
 * juego: no dependen del tema, así se aprenden sin leer.
 */
enum class Act(val face: Color, val edge: Color, val ink: Color) {
    /** Hacer: empezar, nueva tarea, activar la hora libre, abrir algo productivo. */
    GO(Color(0xFF7BD84F), Color(0xFF4F9E2C), Color(0xFF12280A)),
    /** Completar: marcar hecha, guardar. */
    DONE(Color(0xFF4AA8FF), Color(0xFF2A72C4), Color(0xFF04192E)),
    /** Evidencia: cámara, fotos, la nota «¿cómo?». */
    PHOTO(Color(0xFFFFC93C), Color(0xFFC4911A), Color(0xFF352400)),
    /** Premio: cupones, racha, semana perfecta. */
    PRIZE(Color(0xFFA77BFF), Color(0xFF6F45CC), Color(0xFF1B0B3A)),
    /** Tiempo: horas límite, cuenta atrás, cronómetro. */
    TIME(Color(0xFFFF9F43), Color(0xFFC27A1E), Color(0xFF331800)),
    /** Parar: salir del ocio, quitar, bloqueos. */
    STOP(Color(0xFFFF8170), Color(0xFFC2725F), Color(0xFF3A0703)),
    /** Cuidarte: modo Cuidado, ánimo, respirar. */
    CARE(Color(0xFF4FD6C8), Color(0xFF239488), Color(0xFF04241F)),
    /** Secundario neutro (sobre tarjeta oscura). */
    GHOST(Color(0xFF2C4136), Color(0xFF14211A), Color(0xFFF1F6F2)),
}

/** Texto del juego: elige la letra por el peso. */
@Composable
fun GameText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = LocalHome.current.text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = familyFor(weight),
        lineHeight = size * 1.25f,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Botón con relieve: se hunde al pulsarlo y vibra un poco. [text] null = solo icono (cuadrado si se
 * le da ancho). Apagado: mismo color, más transparente, sin relieve.
 */
@Composable
fun GameButton(
    text: String?,
    act: Act,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val drop by
        animateDpAsState(if (pressed && enabled) GameDepth else 0.dp, tween(60), label = "hundir")
    val face = if (enabled) act.face else act.face.copy(alpha = .35f)
    val ink = if (enabled) act.ink else act.ink.copy(alpha = .6f)
    // El relieve va en el margen de abajo; el ancho lo pone quien lo usa (weight, width) o el
    // contenido, nunca toda la pantalla.
    Box(modifier.padding(bottom = GameDepth), propagateMinConstraints = true) {
        if (enabled)
            Box(Modifier.matchParentSize().offset(y = GameDepth).clip(GameMid).background(act.edge))
        Row(
            Modifier.height(height)
                .offset(y = drop)
                .clip(GameMid)
                .background(face)
                .clickable(interactionSource = src, indication = null, enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(horizontal = if (text == null) 0.dp else 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, text, tint = ink, modifier = Modifier.size(20.dp))
            if (icon != null && text != null) Spacer(Modifier.width(7.dp))
            if (text != null) GameText(text, 15.sp, ink, FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/** Casilla cuadrada que salta al marcarla. */
@Composable
fun GameCheck(
    done: Boolean,
    color: Color = Act.DONE.face,
    ink: Color = Act.DONE.ink,
    idle: Color = LocalHome.current.muted,
) {
    val fill by
        animateColorAsState(if (done) color else Color.Transparent, tween(180), label = "casilla")
    val pop by
        animateFloatAsState(
            if (done) 1f else .9f,
            spring(dampingRatio = .35f, stiffness = 600f),
            label = "salto",
        )
    Box(
        Modifier.size(28.dp)
            .scale(if (done) pop else 1f)
            .clip(RoundedCornerShape(9.dp))
            .background(fill)
            .border(3.dp, if (done) color else idle.copy(alpha = .7f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** Barra del día: se llena con lo hecho (verde con brillo). */
@Composable
fun GameBar(value: Float, modifier: Modifier = Modifier, color: Color = Act.GO.face) {
    val v by
        animateFloatAsState(
            value.coerceIn(0f, 1f),
            spring(dampingRatio = .6f, stiffness = 120f),
            label = "barra",
        )
    Box(
        modifier
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = .32f))
    ) {
        if (v > 0f)
            Box(
                Modifier.fillMaxWidth(v)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(4.dp)
                        .padding(horizontal = 4.dp)
                        .offset(y = 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = .35f))
                )
            }
    }
}

/** Contador del HUD (racha, cupones): icono y número en relieve. */
@Composable
fun GameStat(icon: ImageVector, value: String, tint: Color, modifier: Modifier = Modifier) {
    val c = LocalHome.current
    Box(modifier.padding(bottom = 3.dp)) {
        Box(
            Modifier.matchParentSize()
                .offset(y = 3.dp)
                .clip(GameSmall)
                .background(Color.Black.copy(alpha = .35f))
        )
        Row(
            Modifier.height(34.dp)
                .clip(GameSmall)
                .background(c.card.copy(alpha = .96f))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            GameText(value, 14.sp, c.text, FontWeight.ExtraBold)
        }
    }
}

/** Chip pequeño con color de acción (hora límite, minutos, estado). */
@Composable
fun GameChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(GameSmall)
            .background(color.copy(alpha = .18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        GameText(text, 12.sp, color, FontWeight.ExtraBold, maxLines = 1)
    }
}
