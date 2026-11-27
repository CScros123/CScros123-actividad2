package es.foco.next.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.home.OwlBrain.Mode
import es.foco.next.core.home.OwlState.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Cómo se ve el búho en cada modo (colores sin rojo ni negro puro). */
private data class OwlLook(
    val body: Color,
    val belly: Color,
    val iris: Color,
    val brow: Float, // rad: negativo = ceño
    val lidTop: Float, // 0 abierto · 1 cerrado
    val lidBottom: Float,
    val ear: Float,
    val tilt: Float,
    val halo: Boolean,
)

private fun look(m: Mode): OwlLook =
    when (m) {
        // Mucho ocio: preocupado y tierno. Cejas arriba, ojos grandes, cabeza ladeada; tonos cálidos
        // suaves (sin rojo ni ceño).
        Mode.OCIO -> OwlLook(Color(0xFFB08A62), Color(0xFFFBF1E4), Color(0xFFFFD98A), .34f, -.06f, .06f, .08f, .22f, false)
        Mode.LIBRO -> OwlLook(Color(0xFF3B6998), Color(0xFFEAF2FA), Color(0xFFFFD166), .24f, -.05f, 0f, .34f, .22f, false)
        Mode.JUEGO -> OwlLook(Color(0xFF5A54A4), Color(0xFFEFEFFB), Color(0xFF64DFDF), .28f, -.08f, 0f, .45f, -.14f, false)
        Mode.BIBLIA -> OwlLook(Color(0xFFC9963B), Color(0xFFFDF8EB), Color(0xFFFFF3B0), .14f, .42f, .24f, .10f, .06f, true)
        Mode.LEVANTATE -> OwlLook(Color(0xFF2A9D8F), Color(0xFFE6F7F5), Color(0xFFFFE66D), .30f, -.04f, 0f, .48f, 0f, false)
        Mode.NOCHE -> OwlLook(Color(0xFF35504E), Color(0xFFD9D6C3), Color(0xFFE8E2C8), .05f, 1f, .15f, .05f, .10f, false)
        Mode.DESPERTAR -> OwlLook(Color(0xFFA8774A), Color(0xFFFDF7EC), Color(0xFFEDB57A), .20f, .30f, .05f, .30f, .12f, false)
        Mode.CALMA -> OwlLook(Color(0xFF4E6E58), Color(0xFFEEF3EE), Color(0xFFF5B041), .05f, .12f, .05f, .18f, .04f, false)
    }

fun owlAccent(m: Mode): Color = look(m).body

private val BARK = Color(0xFF6B4F2A)
private val MOSS = Color(0xFF4E7A52)
private val LEAF = Color(0xFF6AA86E)
private val FEET = Color(0xFFDFA048)
private val BEAK = Color(0xFFE69A38)
private val INK = Color(0xFF1D2820) // cejas: casi negro, nunca negro puro
private val PUPIL = Color(0xFF111613)
private val GOLD = Color(0xFFFFD166)

/**
 * Búho de Foco en "2,5D": volumen pintado con degradados radiales y giro simulado (la cara se
 * desplaza y se estrecha al rotar). Arrástralo para girarlo; sus ojos siguen tu dedo; tócalo y te da
 * otra idea. Parpadea, habla al cambiar de mensaje y mueve las alas según el modo. Todos los valores
 * animados se LEEN en el dibujo: a 120 Hz solo se redibuja este Canvas. Sin movimiento permitido,
 * queda quieto (pero sigue respondiendo al dedo).
 */
@Composable
fun Owl(
    mode: Mode,
    talkKey: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    pose: Pose = Pose.CALM,
    scarf: Boolean = false,
    /** v38: la luna creciente solo con cielo de noche (antes salía con cualquier tema oscuro, también a mediodía). */
    nightSky: Boolean = false,
) {
    val music = pose == Pose.LISTENING_MUSIC
    val away = pose == Pose.ANGRY_BACK
    val sleeping = pose == Pose.SLEEPING
    val curious = pose == Pose.CURIOUS
    val l = look(mode)
    val motion = LocalMotion.current
    val haptic = LocalHapticFeedback.current
    val spec = tween<Color>(600)
    val body = animateColorAsState(l.body, spec, label = "plumaje")
    val belly = animateColorAsState(l.belly, spec, label = "pecho")
    val iris = animateColorAsState(l.iris, spec, label = "iris")
    val f = tween<Float>(450)
    val brow = animateFloatAsState(l.brow, f, label = "ceja")
    val lidTop = animateFloatAsState(l.lidTop, f, label = "párpado")
    val lidBottom = animateFloatAsState(l.lidBottom, f, label = "párpado2")
    val ear = animateFloatAsState(l.ear, f, label = "oreja")
    val tilt = animateFloatAsState(l.tilt, f, label = "inclinar")
    val halo = animateFloatAsState(if (l.halo) 1f else 0f, tween(700), label = "aureola")
    val time = remember { mutableFloatStateOf(0f) }
    val yaw = remember { mutableFloatStateOf(-.16f) }
    val pitch = remember { mutableFloatStateOf(.03f) }
    val lookX = remember { mutableFloatStateOf(0f) }
    val lookY = remember { mutableFloatStateOf(0f) }
    val dragging = remember { mutableFloatStateOf(0f) }
    val talkUntil = remember { mutableFloatStateOf(0f) }
    // De espaldas: tocarlo hace que se gire a mirarte un momento.
    val peekUntil = remember { mutableFloatStateOf(0f) }
    val awayNow = androidx.compose.runtime.rememberUpdatedState(away)
    val poseNow = androidx.compose.runtime.rememberUpdatedState(pose)
    val blink = remember { mutableFloatStateOf(0f) }
    // Reloj real del búho (segundos desde que apareció).
    val origin = remember { System.nanoTime() }
    fun now() = (System.nanoTime() - origin) / 1e9f
    // Despierto = anima a 120 fps. Se despierta al tocarlo, arrastrarlo, hablar o cambiar de modo.
    val awakeUntil = remember { mutableFloatStateOf(4f) }
    fun wake(seconds: Float) {
        awakeUntil.floatValue = maxOf(awakeUntil.floatValue, now() + seconds)
    }
    LaunchedEffect(talkKey) {
        if (talkKey > 0) {
            talkUntil.floatValue = now() + 1.3f
            wake(4f)
        }
    }
    // Al cambiar de postura se anima unos segundos (el estiramiento dura lo que un estiramiento).
    LaunchedEffect(mode, pose) { wake(if (away || pose == Pose.STRETCH) 7f else 4f) }
    // Ahorro: despierto, fotograma a fotograma; dormido, no se redibuja nada salvo un parpadeo cada
    // pocos segundos (y comprueba 10 veces por segundo si alguien lo despertó, sin dibujar).
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var last = -1L
        var nextBlink = now() + 3f
        while (true) {
            if (poseNow.value == Pose.SLEEPING && dragging.floatValue == 0f) {
                // Dormido: respiración y z Z z a 15 fps, sin parpadeos ni mirar alrededor.
                blink.floatValue = 0f
                last = -1L
                time.floatValue = now()
                kotlinx.coroutines.delay(66)
                continue
            }
            if (now() < awakeUntil.floatValue || dragging.floatValue != 0f) {
                withFrameNanos { n ->
                    val dt = if (last < 0) 0f else min((n - last) / 1e9f, .05f)
                    last = n
                    val t = now()
                    time.floatValue = t
                    val bp = (t % 3.7f)
                    blink.floatValue = if (bp < .16f) sin(bp / .16f * PI.toFloat()) else 0f
                    if (dragging.floatValue == 0f) {
                        // Vuelve solo a su pose y mira alrededor con calma.
                        val rest = if (awayNow.value && t > peekUntil.floatValue) AWAY_YAW else -.16f
                        yaw.floatValue += (rest - yaw.floatValue) * dt * 1.4f
                        pitch.floatValue += (.03f - pitch.floatValue) * dt * 1.8f
                        val up = poseNow.value == Pose.CURIOUS
                        val reads = poseNow.value == Pose.READING || poseNow.value == Pose.READING_IDLE
                        val tx = if (up) .25f else if (reads) .1f * sin(t * .9f) else .35f * sin(t * .7f)
                        val ty = if (up) -.85f else if (reads) .55f else .15f * sin(t * .53f + 1f)
                        lookX.floatValue += (tx - lookX.floatValue) * dt * 2f
                        lookY.floatValue += (ty - lookY.floatValue) * dt * 2f
                    }
                }
            } else {
                last = -1L
                blink.floatValue = 0f
                if (now() >= nextBlink) {
                    // Un parpadeo (~0,2 s) y vuelta a dormir.
                    val b0 = now()
                    while (now() - b0 < .2f) withFrameNanos { blink.floatValue = sin(((now() - b0) / .2f).coerceIn(0f, 1f) * PI.toFloat()) }
                    blink.floatValue = 0f
                    nextBlink = now() + 3.2f + (System.nanoTime() % 1500) / 1000f
                } else kotlinx.coroutines.delay(100)
            }
        }
    }
    Canvas(
        modifier
            .semantics { contentDescription = "Búho de Foco. Tócalo para otra idea; arrástralo para girarlo." }
            .pointerInput(Unit) {
                detectTapGestures {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (awayNow.value) peekUntil.floatValue = now() + 5f
                    wake(if (awayNow.value) 9f else 6f)
                    onTap()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging.floatValue = 1f },
                    onDragEnd = { dragging.floatValue = 0f; wake(3f) },
                    onDragCancel = { dragging.floatValue = 0f; wake(3f) },
                ) { change, d ->
                    change.consume()
                    yaw.floatValue = (yaw.floatValue + d.x / size.width * 2.4f).coerceIn(-1.1f, if (awayNow.value) AWAY_YAW else 1.1f)
                    pitch.floatValue = (pitch.floatValue + d.y / size.height * 1.2f).coerceIn(-.45f, .45f)
                    lookX.floatValue = (change.position.x / size.width * 2 - 1).coerceIn(-1f, 1f)
                    lookY.floatValue = (change.position.y / size.height * 2 - 1).coerceIn(-1f, 1f)
                }
            }
    ) {
        // Temas oscuros: resplandor muy sutil detrás y una luna creciente tenue en el cielo, para que
        // el búho tenga profundidad sin romper el negro.
        if (dark) {
            val m = min(size.width, size.height)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent), Offset(size.width * .56f, size.height * .5f), m * .6f),
                m * .6f, Offset(size.width * .56f, size.height * .5f),
            )
        }
        if (dark && (nightSky || sleeping)) {
            val m = min(size.width, size.height)
            val mr = m * .075f
            val mc = Offset(size.width * .13f, size.height * .16f)
            val crescent = Path().apply {
                op(
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(mc, mr)) },
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(mc.x + mr * .45f, mc.y - mr * .3f), mr * .9f)) },
                    androidx.compose.ui.graphics.PathOperation.Difference,
                )
            }
            drawPath(crescent, Color(0xFFE8E2C8).copy(alpha = .5f))
        }
        drawOwl(
            mode, time.floatValue, yaw.floatValue, pitch.floatValue, lookX.floatValue, lookY.floatValue,
            body.value, belly.value, iris.value, brow.value, lidTop.value, lidBottom.value, ear.value, tilt.value,
            halo.value, talkUntil.floatValue, blink.floatValue, music, pose, scarf,
        )
    }
}

/** De espaldas: unos 165°, mirando el paisaje. Solo en Enfoque con el saldo gastado (lo eligió la persona). */
private const val AWAY_YAW = 2.88f

private val PHONES = Color(0xFF3E4A52)
private val SCARF = Color(0xFFC0634A)
private val TORTOISE = Color(0xFF8A5A33)
private val GLASS_GOLD = Color(0xFFE2C27A)
private val COVER = Color(0xFF3B6998)
private val PAGE = Color(0xFFFBF7EE)

/** Libro abierto sostenido delante del pecho: tapas, dos páginas en V y unas líneas de texto. */
private fun DrawScope.book(c: Offset, rx: Float) {
    val w = rx * .72f
    val h = rx * .62f
    for (side in listOf(-1f, 1f)) {
        val cover = Path().apply {
            moveTo(c.x, c.y + h * .08f)
            lineTo(c.x + side * w * 1.06f, c.y - h * .02f)
            lineTo(c.x + side * w * 1.06f, c.y + h * 1.04f)
            lineTo(c.x, c.y + h * 1.1f)
            close()
        }
        drawPath(cover, COVER)
        val page = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + side * w, c.y - h * .08f)
            lineTo(c.x + side * w, c.y + h * .94f)
            lineTo(c.x, c.y + h)
            close()
        }
        drawPath(page, PAGE)
        for (k in 1..3) {
            val y = c.y + h * (.18f + k * .18f)
            drawLine(Color(0xFFC9C2B0), Offset(c.x + side * w * .18f, y), Offset(c.x + side * w * .82f, y - h * .05f), rx * .025f, StrokeCap.Round)
        }
    }
    drawLine(darken(COVER, .3f), Offset(c.x, c.y), Offset(c.x, c.y + h), rx * .035f)
}

private fun DrawScope.zigzag(c: Offset, s: Float, col: Color, stroke: Float) {
    val a = Offset(c.x - s / 2, c.y - s / 2)
    val b = Offset(c.x + s / 2, c.y - s / 2)
    val d = Offset(c.x - s / 2, c.y + s / 2)
    val e = Offset(c.x + s / 2, c.y + s / 2)
    drawLine(col, a, b, stroke, StrokeCap.Round)
    drawLine(col, b, d, stroke, StrokeCap.Round)
    drawLine(col, d, e, stroke, StrokeCap.Round)
}

private fun lighten(c: Color, k: Float) = lerp(c, Color.White, k)

private fun darken(c: Color, k: Float) = lerp(c, Color(0xFF1C2920), k)

private fun DrawScope.drawOwl(
    mode: Mode, t: Float, yaw: Float, pitch: Float, lx: Float, ly: Float,
    body: Color, belly: Color, iris: Color, brow: Float, lidTop: Float, lidBottom: Float, ear: Float, tilt: Float,
    halo: Float, talkUntil: Float, blink: Float, music: Boolean = false, pose: Pose = Pose.CALM,
    scarf: Boolean = false,
) {
    val reading = pose == Pose.READING || pose == Pose.READING_IDLE
    val sleeping = pose == Pose.SLEEPING
    // De espaldas cuando la cara quedaría detrás (cos < 0): sin ojos ni pico ni pecho.
    val back = cos(yaw) < 0f
    val w = size.width
    val h = size.height
    // Escala: la parte más alta (penachos) queda a ~2,8 u sobre la rama; con 4,1 el búho entero,
    // orejas incluidas, cabe en el lienzo (con 3,4 se cortaban arriba).
    val u = min(w, h * 1.2f) / 4.1f
    val cx = w * .56f
    val branchY = h * .86f
    // Rebote según el modo.
    val bounce = if (sleeping) u * sin(t * 1.1f) * .02f else if (music && mode != Mode.NOCHE) u * abs(sin(t * 4.4f)) * .06f else u * when (mode) {
        Mode.LEVANTATE -> abs(sin(t * 5.2f)) * .16f
        Mode.JUEGO -> abs(sin(t * 4f)) * .09f
        Mode.OCIO -> sin(t * 1.6f) * .025f // se mece despacio, se acerca
        Mode.NOCHE -> sin(t * 1.2f) * .02f
        else -> sin(t * 2.4f) * .035f
    }
    val turn = sin(yaw)
    val bodyC = Offset(cx + turn * u * .12f, branchY - u * .95f - bounce)
    val rx = u * .78f
    val ry = u * .92f

    // Partículas suaves alrededor (detrás).
    for (i in 0 until 8) {
        val a = i / 8f * 2 * PI.toFloat() + t * .25f
        val p = Offset(cx + cos(a) * u * 1.55f, bodyC.y - u * .3f + sin(a * 2) * u * .85f)
        drawCircle(lighten(GOLD, .6f).copy(alpha = .35f + .25f * sin(t + i)), u * .03f, p)
    }
    // Rama con musgo y una hoja.
    drawLine(Brush.verticalGradient(listOf(lighten(BARK, .15f), darken(BARK, .25f)), branchY - u * .1f, branchY + u * .12f),
        Offset(w * .04f, branchY + u * .06f), Offset(w * .98f, branchY - u * .08f), u * .2f, StrokeCap.Round)
    drawCircle(MOSS, u * .2f, Offset(w * .2f, branchY - u * .02f))
    drawCircle(lighten(MOSS, .1f), u * .16f, Offset(w * .85f, branchY - u * .14f))
    drawLine(BARK, Offset(w * .9f, branchY - u * .1f), Offset(w * .96f, branchY - u * .38f), u * .05f, StrokeCap.Round)
    drawOval(LEAF, Offset(w * .93f, branchY - u * .52f), Size(u * .22f, u * .14f))
    // Sombra en la rama.
    drawOval(Color(0x33000000), Offset(bodyC.x - rx * .7f, branchY - u * .14f), Size(rx * 1.4f, u * .16f))
    // Cola.
    val tail = Path().apply {
        moveTo(bodyC.x + rx * .2f, bodyC.y + ry * .6f)
        lineTo(bodyC.x + rx * .75f, branchY + u * .28f)
        lineTo(bodyC.x - rx * .05f, branchY + u * .2f)
        close()
    }
    drawPath(tail, darken(body, .2f))
    // Cuerpo con volumen.
    drawOval(
        Brush.radialGradient(listOf(lighten(body, .28f), body, darken(body, .35f)), Offset(bodyC.x - rx * .35f, bodyC.y - ry * .4f), ry * 1.5f),
        Offset(bodyC.x - rx, bodyC.y - ry), Size(rx * 2, ry * 2),
    )
    // Pecho y plumas en V.
    val bx = bodyC.x + turn * rx * .25f
    if (back) {
        // Espalda: plumas en escamas, del color del plumaje.
        for (row in 0 until 3) for (col in -1..1) {
            val p = Offset(bodyC.x + col * rx * .3f + (row % 2) * rx * .15f, bodyC.y - ry * .3f + row * ry * .3f)
            drawArc(darken(body, .28f), 20f, 140f, false, Offset(p.x - u * .09f, p.y - u * .06f), Size(u * .18f, u * .12f), style = Stroke(u * .03f, cap = StrokeCap.Round))
        }
    } else {
    drawOval(
        Brush.radialGradient(listOf(Color.White, belly, darken(belly, .12f)), Offset(bx, bodyC.y), rx * .9f),
        Offset(bx - rx * .62f, bodyC.y - ry * .55f), Size(rx * 1.24f, ry * 1.4f),
    )
    for (row in 0 until 3) for (col in -1..1) {
        val p = Offset(bx + col * rx * .28f + (row % 2) * rx * .14f, bodyC.y - ry * .15f + row * ry * .28f)
        drawArc(darken(belly, .22f), 20f, 140f, false, Offset(p.x - u * .07f, p.y - u * .05f), Size(u * .14f, u * .1f), style = Stroke(u * .025f, cap = StrokeCap.Round))
    }
    }
    // Alas según el modo (grados; positivo = hacia fuera).
    val (wl, wr) = if (reading) 28f to -28f // las dos alas sostienen el libro
    else if (pose == Pose.STRETCH) (20f + 95f * (1f - cos(t * 1.1f)) / 2f) to -5f // estira un ala, despacio
    else when (mode) {
        Mode.OCIO -> 18f + sin(t * 1.6f) * 4f to -(18f + sin(t * 1.6f) * 4f) // alas abiertas, como un abrazo
        Mode.LIBRO -> 7f to -(34f + sin(t * 4f) * 5f)
        Mode.JUEGO -> abs(sin(t * 7f)) * 26f + 20f to -(abs(sin(t * 7f)) * 26f + 20f)
        Mode.BIBLIA -> 10f + sin(t * 1.5f) * 4f to -(10f + sin(t * 1.5f) * 4f)
        Mode.LEVANTATE -> 150f + sin(t * 5.2f) * 12f to -(150f + sin(t * 5.2f) * 12f)
        else -> 5f to -5f
    }
    wing(Offset(bodyC.x - rx * .82f, bodyC.y - ry * .35f), rx, ry, wl, body)
    wing(Offset(bodyC.x + rx * .82f, bodyC.y - ry * .35f), rx, ry, wr, body)
    // Patas sobre la rama.
    for (side in listOf(-1f, 1f)) for (k in -1..1)
        drawCircle(FEET, u * .065f, Offset(bodyC.x + side * rx * .35f + k * u * .09f, branchY - u * .08f))

    if (reading && !back) book(Offset(bodyC.x + turn * rx * .2f, bodyC.y + ry * .28f), rx)
    if (music) for (k in 0 until 2) {
        val ph = ((t * .35f + k * .5f) % 1f)
        val nc = Offset(bodyC.x - rx * (1.25f + ph * .3f) + k * rx * 2.6f, bodyC.y - ry * (.6f + ph * 1.1f))
        val col = GOLD.copy(alpha = (1f - ph) * .85f)
        drawOval(col, Offset(nc.x - u * .07f, nc.y - u * .05f), Size(u * .14f, u * .1f))
        drawLine(col, Offset(nc.x + u * .06f, nc.y), Offset(nc.x + u * .06f, nc.y - u * .26f), u * .025f, StrokeCap.Round)
        drawLine(col, Offset(nc.x + u * .06f, nc.y - u * .26f), Offset(nc.x + u * .16f, nc.y - u * .2f), u * .025f, StrokeCap.Round)
    }
    // v31: bufanda ganada con 3 días seguidos al 100 % (se queda para siempre).
    if (scarf && !back) {
        val sy = bodyC.y - ry * .78f
        drawOval(SCARF, Offset(bodyC.x - rx * .72f, sy - u * .1f), Size(rx * 1.44f, u * .26f))
        drawOval(darken(SCARF, .18f), Offset(bodyC.x - rx * .72f, sy + u * .02f), Size(rx * 1.44f, u * .08f))
        val tail = Path().apply {
            moveTo(bodyC.x + rx * .2f, sy + u * .05f)
            lineTo(bodyC.x + rx * .42f, sy + u * .62f)
            lineTo(bodyC.x + rx * .14f, sy + u * .58f)
            close()
        }
        drawPath(tail, SCARF)
        for (k in 0..2) drawLine(Color.White.copy(alpha = .55f), Offset(bodyC.x - rx * .5f + k * rx * .45f, sy - u * .08f), Offset(bodyC.x - rx * .5f + k * rx * .45f, sy + u * .12f), u * .03f)
    }
    // ---------- Cabeza ----------
    val r = u * .72f
    val head = Offset(cx + turn * u * .3f, bodyC.y - u * 1.02f + bounce * .08f + pitch * u * .15f)
    val headTilt = tilt + if (pose == Pose.CURIOUS) .28f else 0f
    rotate(headTilt * 57.3f + sin(t * 1.5f) * 1.7f, head) {
        // Penachos (orejas).
        for (side in listOf(-1f, 1f)) {
            val base = Offset(head.x + side * r * .55f, head.y - r * .72f)
            rotate(side * ear * 40f, base) {
                val p = Path().apply {
                    moveTo(base.x - r * .17f, base.y + r * .12f)
                    lineTo(base.x + side * r * .14f, base.y - r * .42f)
                    lineTo(base.x + r * .17f, base.y + r * .12f)
                    close()
                }
                drawPath(p, darken(body, .1f))
            }
        }
        drawCircle(
            Brush.radialGradient(listOf(lighten(body, .3f), body, darken(body, .35f)), Offset(head.x - r * .35f, head.y - r * .45f), r * 1.6f),
            r, head,
        )
        if (back) {
            // Nuca: dos arcos suaves en lugar de la cara.
            for (k in 0..1)
                drawArc(darken(body, .25f), 200f, 140f, false, Offset(head.x - r * (.5f - k * .12f), head.y - r * (.1f - k * .25f)), Size(r * (1f - k * .24f), r * .5f), style = Stroke(r * .06f, cap = StrokeCap.Round))
        } else {
        // Cara: se desplaza y se estrecha al girar (falso 3D).
        val fx = head.x + turn * r * .42f
        val sx = .78f + .22f * cos(yaw)
        val fy = head.y + r * .05f + pitch * r * .25f
        for (side in listOf(-1f, 1f)) {
            val dc = Offset(fx + side * r * .38f * sx, fy)
            drawOval(Brush.radialGradient(listOf(Color.White, belly), dc, r * .5f), Offset(dc.x - r * .44f * sx, dc.y - r * .46f), Size(r * .88f * sx, r * .92f))
        }
        // Ojos.
        val top = min(1f, lidTop + blink * .9f)
        val bottom = min(1f, lidBottom + blink * .8f)
        val er = r * .27f
        for (side in listOf(-1f, 1f)) {
            val e = Offset(fx + side * r * .36f * sx, fy - r * .02f)
            drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFE6ECE6)), e, er), Offset(e.x - er * sx, e.y - er), Size(er * 2 * sx, er * 2))
            val pupil = Offset(e.x + lx * er * .32f + turn * er * .2f, e.y + ly * er * .28f)
            drawCircle(Brush.radialGradient(listOf(lighten(iris, .35f), iris, darken(iris, .25f)), pupil, er * .62f), er * .62f, pupil)
            drawCircle(PUPIL, er * .34f, pupil)
            drawCircle(Color.White, er * .12f, Offset(pupil.x + er * .18f, pupil.y - er * .2f))
            // Párpados del color del plumaje.
            val eye = Path().apply { addOval(androidx.compose.ui.geometry.Rect(e.x - er * sx, e.y - er, e.x + er * sx, e.y + er)) }
            clipPath(eye) {
                if (top > 0f) drawRect(lighten(body, .1f), Offset(e.x - er * sx, e.y - er), Size(er * 2 * sx, er * 2 * top))
                if (bottom > 0f) drawRect(lighten(body, .05f), Offset(e.x - er * sx, e.y + er - er * 2 * bottom), Size(er * 2 * sx, er * 2 * bottom))
            }
            // Cejas: con ceño (negativo) bajan por dentro.
            val bc = Offset(e.x, e.y - er * 1.3f)
            rotate(-side * brow * 57.3f, bc) {
                drawLine(INK, Offset(bc.x - er * .75f * sx, bc.y), Offset(bc.x + er * .75f * sx, bc.y), er * .26f, StrokeCap.Round)
            }
        }
        // Pico: la parte de abajo se mueve al hablar.
        val talking = t < talkUntil
        val open = if (talking) abs(sin(t * 22f)) * r * .1f else 0f
        val beakTop = Offset(fx, fy + r * .12f)
        val lower = Path().apply {
            moveTo(beakTop.x - r * .09f, beakTop.y + r * .12f + open)
            lineTo(beakTop.x + r * .09f, beakTop.y + r * .12f + open)
            lineTo(beakTop.x, beakTop.y + r * .26f + open)
            close()
        }
        drawPath(lower, darken(BEAK, .15f))
        val upper = Path().apply {
            moveTo(beakTop.x - r * .13f, beakTop.y)
            lineTo(beakTop.x + r * .13f, beakTop.y)
            lineTo(beakTop.x, beakTop.y + r * .3f)
            close()
        }
        drawPath(upper, Brush.verticalGradient(listOf(lighten(BEAK, .2f), BEAK), beakTop.y, beakTop.y + r * .3f))
        }
        // Anteojos redondos de lectura: aros finos carey/dorado, puente sobre el pico y un destello.
        if (reading && !back) {
            val gx = head.x + turn * r * .42f
            val gsx = .78f + .22f * cos(yaw)
            val gy = head.y + r * .03f + pitch * r * .25f
            val gr = r * .31f
            for (side in listOf(-1f, 1f)) {
                val lens = Offset(gx + side * r * .36f * gsx, gy)
                drawOval(Color.White.copy(alpha = .10f), Offset(lens.x - gr * gsx, lens.y - gr), Size(gr * 2 * gsx, gr * 2))
                drawOval(TORTOISE, Offset(lens.x - gr * gsx, lens.y - gr), Size(gr * 2 * gsx, gr * 2), style = Stroke(r * .05f))
                drawOval(GLASS_GOLD.copy(alpha = .7f), Offset(lens.x - gr * gsx, lens.y - gr), Size(gr * 2 * gsx, gr * 2), style = Stroke(r * .018f))
                drawLine(Color.White.copy(alpha = .55f), Offset(lens.x - gr * .45f * gsx, lens.y - gr * .15f), Offset(lens.x - gr * .05f * gsx, lens.y - gr * .55f), r * .03f, StrokeCap.Round)
                // Patilla hacia la oreja.
                drawLine(TORTOISE, Offset(lens.x + side * gr * gsx, lens.y - gr * .2f), Offset(head.x + side * r * .92f, head.y - r * .12f), r * .035f, StrokeCap.Round)
            }
            drawArc(TORTOISE, 200f, 140f, false, Offset(gx - r * .13f * gsx, gy - gr * .55f), Size(r * .26f * gsx, r * .2f), style = Stroke(r * .045f, cap = StrokeCap.Round))
        }
        // z Z z al dormir: tres letras que suben y se desvanecen despacio.
        if (sleeping) for (k in 0 until 3) {
            val ph = ((t * .22f + k / 3f) % 1f)
            val zs = r * (.16f + .1f * k)
            val zc = Offset(head.x + r * (.9f + ph * .55f), head.y - r * (.5f + ph * .7f))
            zigzag(zc, zs, Color(0xFFE8E2C8).copy(alpha = (1f - ph) * .8f), r * .045f)
        }
        // Cascos cuando suena música: diadema sobre la cabeza y dos auriculares a los lados.
        if (music) {
            drawArc(PHONES, 200f, 140f, false, Offset(head.x - r * 1.02f, head.y - r * 1.02f), Size(r * 2.04f, r * 2.04f), style = Stroke(r * .13f, cap = StrokeCap.Round))
            for (side in listOf(-1f, 1f)) {
                val cup = Offset(head.x + side * r * .98f, head.y + r * .05f)
                drawOval(PHONES, Offset(cup.x - r * .17f, cup.y - r * .27f), Size(r * .34f, r * .54f))
                drawOval(lighten(PHONES, .35f), Offset(cup.x - r * .08f, cup.y - r * .16f), Size(r * .16f, r * .32f))
            }
        }
        // Aureola (palabra del día).
        if (halo > .01f) {
            val hc = Offset(head.x, head.y - r * 1.08f)
            val hw = r * 1.3f * halo
            val hh = r * .34f * halo
            drawOval(GOLD.copy(alpha = .25f), Offset(hc.x - hw / 2 - r * .06f, hc.y - hh / 2 - r * .04f), Size(hw + r * .12f, hh + r * .08f), style = Stroke(r * .14f))
            drawOval(GOLD, Offset(hc.x - hw / 2, hc.y - hh / 2), Size(hw, hh), style = Stroke(r * .07f))
        }
    }
}

private fun DrawScope.wing(pivot: Offset, rx: Float, ry: Float, degrees: Float, body: Color) {
    rotate(degrees, pivot) {
        drawOval(
            Brush.radialGradient(listOf(lighten(body, .12f), darken(body, .25f)), Offset(pivot.x, pivot.y + ry * .4f), ry),
            Offset(pivot.x - rx * .26f, pivot.y - ry * .05f), Size(rx * .52f, ry * 1.12f),
        )
    }
}

/** Burbuja del búho: modo, mensaje, "Otra idea ↻", dato real del día y acción sugerida. */
@Composable
fun OwlSpeech(mode: Mode, text: String, info: String, cta: String, onNext: () -> Unit, onCta: () -> Unit) {
    val c = LocalHome.current
    val accent by animateColorAsState(lerp(owlAccent(mode), c.text, if (c.moon) .0f else .1f), tween(600), label = "acento")
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.card.copy(alpha = .96f))
            .border(2.dp, accent.copy(alpha = .3f), shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                HomeText(mode.badge, 10.5.sp, if (c.moon) c.text else accent, FontWeight.Bold)
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(lerp(c.card, c.accent, .1f)).clickable(onClick = onNext)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) { HomeText("Otra idea ↻", 11.sp, c.text, FontWeight.SemiBold) }
        }
        AnimatedContent(text, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "mensaje") { msg ->
            Text(msg, Modifier.padding(top = 6.dp), color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HomeText(info, 11.sp, c.muted, modifier = Modifier.weight(1f))
            if (cta.isNotEmpty())
                Text("$cta →", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCta).padding(horizontal = 4.dp, vertical = 2.dp),
                    color = if (c.moon) c.text else accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
