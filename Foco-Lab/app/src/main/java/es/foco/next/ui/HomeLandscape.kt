package es.foco.next.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import es.foco.next.core.home.HomePalette

/** true solo con el inicio visible y animaciones permitidas (ahorro de Foco, reducir movimiento). */
val LocalMotion = compositionLocalOf { false }

/**
 * Paleta de Compose. Fluidez: es un data class inmutable (con equals). Antes era una clase normal
 * recreada en cada recomposición, así que todo lo que leía LocalHome (toda la pantalla) se
 * recomponía siempre; y 9 colores animados durante 1,5 s recomponían el inicio entero en cada
 * fotograma. Ahora el cambio de paleta es directo y solo recompone si los colores cambian.
 */
@androidx.compose.runtime.Immutable
data class HomeColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val sun: Color,
    val cloud: Color,
    val hill: Color,
    val onHill: Color,
    val moon: Boolean,
)

val LocalHome = compositionLocalOf {
    HomePalette.NEUTRO.let {
        HomeColors(Color(it.background), Color(it.card), Color(it.text), Color(it.muted), Color(it.accent),
            Color(it.sun), Color(it.cloud), Color(it.hill), Color(it.onHill), it.moon)
    }
}

const val HOME_TRANSITION_MS = 1500

/** Tarjeta oscura (temas fotográficos de noche o de paisaje oscuro). */
val HomeColors.darkCard: Boolean
    get() = card.red + card.green + card.blue < 1.2f

/**
 * Superficie elevada (filas del cajón, buscador) legible con cualquier tema: en temas claros se
 * aclara hacia blanco; en oscuros apenas, y siempre opaca.
 */
fun HomeColors.raised(k: Float): Color =
    if (darkCard) lerp(card.copy(alpha = 1f), Color.White, .07f) else lerp(card.copy(alpha = 1f), Color.White, k)

@Composable
fun rememberHomeColors(p: HomePalette): HomeColors =
    remember(p) {
        HomeColors(Color(p.background), Color(p.card), Color(p.text), Color(p.muted), Color(p.accent),
            Color(p.sun), Color(p.cloud), Color(p.hill), Color(p.onHill), p.moon)
    }

private val STILL: State<Float> = androidx.compose.runtime.mutableFloatStateOf(0f)

/**
 * Lento vaivén 0..1 como State: se LEE dentro del dibujo, así solo se redibuja el Canvas y no se
 * recompone nada en cada fotograma. Quieto si no hay movimiento.
 */
@Composable
fun slowDrift(periodMs: Int = 60_000): State<Float> {
    if (!LocalMotion.current) return STILL
    val t = rememberInfiniteTransition(label = "deriva")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse), label = "deriva")
}

/**
 * Cielo, nube detrás del reloj, sol (o luna y estrellas) y dos colinas. [hillTop] es la y (px) donde
 * empieza la colina cercana: la fija el pie (dock + buscador) para quedar siempre sobre ella.
 */
@Composable
fun HomeLandscape(hillTop: Float, modifier: Modifier = Modifier, sunAlpha: () -> Float = { 1f }) {
    val c = LocalHome.current
    // Estático a propósito: una deriva infinita obligaba a redibujar la pantalla entera 120 veces
    // por segundo para un movimiento casi invisible. Solo cambia al cambiar de estado (1,5 s).
    val path = remember { Path() }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val d = density
        val top = if (hillTop > 0) hillTop else h * .65f
        drawRect(c.background)
        val off = 0f
        drawOval(c.cloud, Offset(44 * d + off, 52 * d), Size(140 * d, 64 * d))
        drawOval(c.cloud, Offset(76 * d + off, 42 * d), Size(76 * d, 54 * d))
        val r = 31 * d
        val sun = Offset(w - 38 * d - r, 104 * d + r)
        // En Ajustes y Hacer el astro baja al 20 %: no compite con los interruptores del mismo color.
        // Se lee aquí (fase de dibujo): deslizar no recompone nada.
        val sunK = sunAlpha().coerceIn(0f, 1f)
        drawCircle(c.sun.copy(alpha = c.sun.alpha * sunK), r, sun)
        if (c.moon) {
            drawCircle(c.background, r * .9f, Offset(sun.x + r * .45f, sun.y - r * .25f))
            for (s in STARS) drawCircle(c.text.copy(alpha = .65f), 2.2f * d, Offset(w * s[0], h * s[1] * .8f))
        } else drawCircle(c.sun.copy(alpha = .25f * sunK), r * 1.45f, sun)
        // Colina lejana suave.
        path.reset()
        val far = top - 26 * d
        path.moveTo(0f, far)
        val n = 24
        for (i in 0..n) {
            val x = w * i / n
            path.lineTo(x, far - kotlin.math.sin(i / n.toFloat() * Math.PI * 1.1).toFloat() * 18 * d)
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(path, lerp(c.hill, c.background, .7f))
        // Colina cercana: curva del prototipo (M0,38 Q125,0 265,28 T400,22).
        val k = w / 400f
        path.reset()
        path.moveTo(0f, top + 38 * k * .9f)
        path.quadraticBezierTo(125 * k, top, 265 * k, top + 28 * k * .9f)
        path.quadraticBezierTo(405 * k, top + 56 * k * .9f, w, top + 22 * k * .9f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(path, c.hill)
    }
}

private val STARS = arrayOf(
    floatArrayOf(.12f, .08f), floatArrayOf(.3f, .14f), floatArrayOf(.5f, .06f),
    floatArrayOf(.62f, .2f), floatArrayOf(.2f, .25f), floatArrayOf(.9f, .3f),
)
