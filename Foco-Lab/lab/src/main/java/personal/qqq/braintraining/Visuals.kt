package personal.qqq.braintraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.*
import personal.qqq.braintraining.core.*

data class ArtColors(val base: Color, val glow: Color)

fun artColors(area: Area) =
    when (area) {
        Area.MEMORY -> ArtColors(Color(0xFF722FE0), Color(0xFFCCFFEF))
        Area.ATTENTION -> ArtColors(Color(0xFFFF563A), Color(0xFFFFE6BF))
        Area.SPEED -> ArtColors(Color(0xFF142B29), Color(0xFF2BE89D))
        Area.LOGIC -> ArtColors(Color(0xFF21264B), Color(0xFFACACFF))
        Area.MATH -> ArtColors(Color(0xFF3265F4), Color(0xFFE1EDFF))
        Area.REAL -> ArtColors(Color(0xFF087C8D), Color(0xFFD6FFFF))
        Area.NETSEC -> ArtColors(Color(0xFF161B26), Color(0xFFFFB300))
        Area.ARCADE -> ArtColors(Color(0xFF140F1F), Color(0xFFFF6B9A))
    }

fun artColors(game: Game) =
    when (game.id) {
        "math" -> ArtColors(Color(0xFF151C2B), Color(0xFF99C5FF))
        "suitcase" -> ArtColors(Color(0xFF2A1E14), Color(0xFFFFB300))
        "vigil" -> ArtColors(Color(0xFF0E1420), Color(0xFF7CF5FF))
        "route" -> ArtColors(Color(0xFF12161F), Color(0xFF7CF5FF))
        "ddos" -> ArtColors(Color(0xFF140F1F), Color(0xFFFF6B9A))
        "pairs" -> ArtColors(Color(0xFF161B26), Color(0xFFFFB300))
        "orbit" -> ArtColors(Color(0xFF6234D9), Color(0xFFB4FFD9))
        "compass" -> ArtColors(Color(0xFF141924), Color(0xFFFF526D))
        "balance" -> ArtColors(Color(0xFFFF7D38), Color(0xFFFFF5C8))
        "percent" -> ArtColors(Color(0xFF123B32), Color(0xFF37EE9E))
        "fraction" -> ArtColors(Color(0xFF1296A8), Color(0xFFD8FFEC))
        "equation" -> ArtColors(Color(0xFF913DE3), Color(0xFFFFE2FB))
        "ratio" -> ArtColors(Color(0xFF2554C5), Color(0xFFA8F4FF))
        "unitprice" -> ArtColors(Color(0xFF137949), Color(0xFFE2FFD0))
        "planning" -> ArtColors(Color(0xFF303574), Color(0xFFFFC0E7))
        "working" -> ArtColors(Color(0xFFAB2472), Color(0xFFFFDCEE))
        "pattern" -> ArtColors(Color(0xFFC82C52), Color(0xFFFFD5B9))
        "series" -> ArtColors(Color(0xFF434CC4), Color(0xFFC5F2FF))
        "choice" -> ArtColors(Color(0xFF008486), Color(0xFFBCFFF0))
        else -> artColors(game.area)
    }

private fun DrawScope.arrow(at: Offset, length: Float, direction: Int, color: Color) {
    withTransform({ rotate(direction * 90f, at) }) {
        val w = length * .19f
        val path =
            Path().apply {
                moveTo(at.x, at.y - length * .5f)
                lineTo(at.x + length * .38f, at.y - length * .08f)
                lineTo(at.x + w, at.y - length * .08f)
                lineTo(at.x + w, at.y + length * .5f)
                lineTo(at.x - w, at.y + length * .5f)
                lineTo(at.x - w, at.y - length * .08f)
                lineTo(at.x - length * .38f, at.y - length * .08f)
                close()
            }
        drawPath(path, color)
    }
}

private fun DrawScope.ornaments(glow: Color) {
    val d = size.minDimension
    drawCircle(
        glow.copy(alpha = .07f),
        d * .58f,
        Offset(size.width * .95f, size.height * .05f),
        style = Stroke(d * .15f),
    )
    drawCircle(
        Color.White.copy(alpha = .06f),
        d * .36f,
        Offset(size.width * .06f, size.height * .96f),
    )
}

private fun DrawScope.shield(at: Offset, h: Float, color: Color) {
    val w = h * .8f
    val p =
        Path().apply {
            moveTo(at.x, at.y - h / 2)
            lineTo(at.x + w / 2, at.y - h * .35f)
            quadraticTo(at.x + w / 2, at.y + h * .2f, at.x, at.y + h / 2)
            quadraticTo(at.x - w / 2, at.y + h * .2f, at.x - w / 2, at.y - h * .35f)
            close()
        }
    drawPath(p, color)
}

/** Original vector scenes: no downloaded game assets, no bitmap allocation per frame. */
@Composable
fun GameArt(game: Game, modifier: Modifier = Modifier) {
    val palette = artColors(game)
    Canvas(
        modifier.clipToBounds().semantics { contentDescription = "Ilustración de ${game.title}" }
    ) {
        drawRect(
            Brush.linearGradient(
                listOf(
                    palette.base,
                    palette.base.copy(
                        red = (palette.base.red * .72f),
                        green = (palette.base.green * .84f),
                        blue = (palette.base.blue * .88f),
                    ),
                )
            )
        )
        ornaments(palette.glow)
        val cx = size.width / 2
        val cy = size.height / 2
        val d = size.minDimension
        when (game.id) {
            "orbit",
            "sequence" -> {
                val radius = d * .32f
                drawCircle(
                    Color.White.copy(alpha = .17f),
                    radius,
                    Offset(cx, cy),
                    style = Stroke(d * .013f),
                )
                for (i in 0..7) {
                    val a = (i * PI / 4 - PI / 2)
                    val at = Offset(cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius)
                    drawCircle(
                        if (i in listOf(0, 2, 5)) palette.glow else Color.White.copy(alpha = .45f),
                        d * .06f,
                        at,
                    )
                }
                drawCircle(palette.glow, d * .065f, Offset(cx, cy))
                drawCircle(Color.White, d * .026f, Offset(cx, cy))
            }
            "grid",
            "working" -> {
                val unit = d * .18f
                for (i in 0..8) {
                    val at =
                        Offset(
                            cx + (i % 3 - 1) * unit * 1.2f - unit * .5f,
                            cy + (i / 3 - 1) * unit * 1.2f - unit * .5f,
                        )
                    drawRoundRect(
                        if (i in listOf(0, 4, 5, 6)) palette.glow
                        else Color.White.copy(alpha = .2f),
                        at,
                        Size(unit, unit),
                        CornerRadius(unit * .2f),
                    )
                    if (i in listOf(0, 4, 5, 6))
                        drawCircle(Color.White, unit * .17f, at + Offset(unit * .5f, unit * .5f))
                }
            }
            "compass",
            "flanker" -> {
                drawCircle(Color.White.copy(alpha = .12f), d * .35f, Offset(cx, cy))
                arrow(Offset(cx, cy), d * .48f, if (game.id == "compass") 1 else 0, palette.glow)
                arrow(Offset(cx - d * .35f, cy), d * .2f, 3, Color.White.copy(alpha = .65f))
                arrow(Offset(cx + d * .35f, cy), d * .2f, 3, Color.White.copy(alpha = .65f))
            }
            "balance" -> {
                drawLine(
                    palette.glow,
                    Offset(cx, cy - d * .18f),
                    Offset(cx, cy + d * .3f),
                    d * .035f,
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(cx - d * .33f, cy - d * .14f),
                    Offset(cx + d * .33f, cy - d * .14f),
                    d * .025f,
                    StrokeCap.Round,
                )
                drawLine(
                    palette.glow,
                    Offset(cx - d * .18f, cy + d * .3f),
                    Offset(cx + d * .18f, cy + d * .3f),
                    d * .035f,
                    StrokeCap.Round,
                )
                for (side in listOf(-1, 1)) {
                    val x = cx + side * d * .28f
                    drawLine(
                        Color.White.copy(alpha = .65f),
                        Offset(x, cy - d * .14f),
                        Offset(x, cy + d * .09f),
                        d * .012f,
                    )
                    drawArc(
                        palette.glow,
                        0f,
                        180f,
                        false,
                        Offset(x - d * .14f, cy - d * .05f),
                        Size(d * .28f, d * .25f),
                        style = Stroke(d * .023f),
                    )
                    drawRoundRect(
                        if (side == -1) Color.White else palette.glow,
                        Offset(x - d * .075f, cy - d * .03f),
                        Size(d * .15f, d * .12f),
                        CornerRadius(d * .02f),
                    )
                }
            }
            "search",
            "pattern" -> {
                for (i in 0..8) {
                    val at = Offset(cx + (i % 3 - 1) * d * .23f, cy + (i / 3 - 1) * d * .23f)
                    drawCircle(
                        if (i == 4) palette.glow else Color.White.copy(alpha = .32f),
                        d * .068f,
                        at,
                        style = Stroke(d * .022f),
                    )
                    if (i == 4) drawCircle(Color.White, d * .025f, at)
                }
            }
            "matrix",
            "series" -> {
                for (i in 0..5) {
                    val at = Offset(cx + (i % 3 - 1) * d * .23f, cy + (i / 3 - .5f) * d * .28f)
                    if (i % 3 == 0) drawCircle(palette.glow, d * .068f, at)
                    else if (i % 3 == 1)
                        drawRoundRect(
                            Color.White.copy(alpha = .65f),
                            at - Offset(d * .065f, d * .065f),
                            Size(d * .13f, d * .13f),
                            CornerRadius(d * .015f),
                        )
                    else {
                        val p =
                            Path().apply {
                                moveTo(at.x, at.y - d * .08f)
                                lineTo(at.x + d * .075f, at.y + d * .06f)
                                lineTo(at.x - d * .075f, at.y + d * .06f)
                                close()
                            }
                        drawPath(p, palette.glow)
                    }
                }
            }
            "budget" -> {
                drawRoundRect(
                    Color.White.copy(alpha = .86f),
                    Offset(cx - d * .24f, cy - d * .27f),
                    Size(d * .48f, d * .56f),
                    CornerRadius(d * .05f),
                )
                for (i in 0..2) drawLine(
                    palette.base.copy(alpha = .5f),
                    Offset(cx - d * .14f, cy - d * .13f + i * d * .11f),
                    Offset(cx + d * .1f, cy - d * .13f + i * d * .11f),
                    d * .025f,
                    StrokeCap.Round,
                )
                drawCircle(palette.glow, d * .12f, Offset(cx + d * .2f, cy + d * .17f))
                drawCircle(
                    palette.base,
                    d * .05f,
                    Offset(cx + d * .2f, cy + d * .17f),
                    style = Stroke(d * .017f),
                )
            }
            "percent" -> {
                drawCircle(
                    Color.White.copy(alpha = .15f),
                    d * .32f,
                    Offset(cx, cy),
                    style = Stroke(d * .065f),
                )
                drawArc(
                    palette.glow,
                    -90f,
                    270f,
                    false,
                    Offset(cx - d * .32f, cy - d * .32f),
                    Size(d * .64f, d * .64f),
                    style = Stroke(d * .065f, cap = StrokeCap.Round),
                )
                drawLine(
                    Color.White,
                    Offset(cx - d * .09f, cy + d * .13f),
                    Offset(cx + d * .09f, cy - d * .13f),
                    d * .027f,
                    StrokeCap.Round,
                )
                drawCircle(Color.White, d * .035f, Offset(cx - d * .1f, cy - d * .095f))
                drawCircle(Color.White, d * .035f, Offset(cx + d * .1f, cy + d * .095f))
            }
            "fraction" -> {
                for (i in 0..5) drawArc(
                    if (i < 4) palette.glow else Color.White.copy(alpha = .25f),
                    i * 60f - 90f,
                    55f,
                    true,
                    Offset(cx - d * .32f, cy - d * .32f),
                    Size(d * .64f, d * .64f),
                )
                drawCircle(palette.base, d * .105f, Offset(cx, cy))
                drawCircle(Color.White, d * .035f, Offset(cx, cy))
            }
            "equation" -> {
                val at = Offset(cx - d * .33f, cy - d * .14f)
                drawRoundRect(palette.glow, at, Size(d * .28f, d * .28f), CornerRadius(d * .04f))
                val middle = at + Offset(d * .14f, d * .14f)
                drawLine(
                    palette.base,
                    middle - Offset(d * .06f, d * .06f),
                    middle + Offset(d * .06f, d * .06f),
                    d * .024f,
                    StrokeCap.Round,
                )
                drawLine(
                    palette.base,
                    middle - Offset(d * .06f, -d * .06f),
                    middle + Offset(d * .06f, -d * .06f),
                    d * .024f,
                    StrokeCap.Round,
                )
                for (y in listOf(-.045f, .045f)) drawLine(
                    Color.White,
                    Offset(cx + d * .02f, cy + d * y),
                    Offset(cx + d * .14f, cy + d * y),
                    d * .023f,
                    StrokeCap.Round,
                )
                drawCircle(palette.glow, d * .12f, Offset(cx + d * .32f, cy))
            }
            "ratio" -> {
                for (side in listOf(-1, 1)) {
                    val at = Offset(cx + side * d * .23f, cy + d * .14f)
                    val radius = if (side == -1) d * .13f else d * .19f
                    drawArc(
                        palette.glow,
                        0f,
                        180f,
                        true,
                        at - Offset(radius, radius * .5f),
                        Size(radius * 2, radius),
                        style = Fill,
                    )
                    for (i in 0 until (if (side == -1) 3 else 6)) {
                        val grain =
                            Offset(
                                at.x + (i % 3 - 1) * d * .055f,
                                at.y - d * .1f - (i / 3) * d * .05f,
                            )
                        drawOval(Color.White, grain, Size(d * .037f, d * .02f))
                    }
                }
                arrow(Offset(cx, cy - d * .12f), d * .16f, 1, Color.White.copy(alpha = .7f))
            }
            "unitprice" -> {
                for (side in listOf(-1, 1)) {
                    val at = Offset(cx + side * d * .22f - d * .12f, cy - d * .12f)
                    drawRoundRect(
                        if (side == -1) Color.White.copy(alpha = .85f) else palette.glow,
                        at,
                        Size(d * .24f, d * .35f),
                        CornerRadius(d * .035f),
                    )
                    drawArc(
                        Color.White,
                        -180f,
                        180f,
                        false,
                        at + Offset(d * .055f, -d * .105f),
                        Size(d * .13f, d * .18f),
                        style = Stroke(d * .023f),
                    )
                    for (i in 0 until (if (side == -1) 2 else 4)) drawCircle(
                        palette.base.copy(alpha = .65f),
                        d * .02f,
                        at + Offset(d * (.065f + (i % 2) * .11f), d * (.11f + (i / 2) * .09f)),
                    )
                }
            }
            "planning" -> {
                drawCircle(Color.White.copy(alpha = .2f), d * .33f, Offset(cx, cy))
                drawCircle(palette.glow, d * .31f, Offset(cx, cy), style = Stroke(d * .025f))
                for (i in 0..11) {
                    val a = i * PI / 6
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(
                        Color.White.copy(alpha = .7f),
                        Offset(cx, cy) + dir * d * .255f,
                        Offset(cx, cy) + dir * d * .275f,
                        d * .014f,
                        StrokeCap.Round,
                    )
                }
                drawLine(
                    Color.White,
                    Offset(cx, cy),
                    Offset(cx, cy - d * .19f),
                    d * .025f,
                    StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(cx, cy),
                    Offset(cx + d * .14f, cy + d * .06f),
                    d * .025f,
                    StrokeCap.Round,
                )
                drawCircle(palette.glow, d * .032f, Offset(cx, cy))
            }
            "suitcase" -> {
                val w = d * .62f
                val h = d * .42f
                val top = cy - h * .35f
                drawRoundRect(
                    Color.White.copy(alpha = .18f),
                    Offset(cx - d * .1f, top - d * .13f),
                    Size(d * .2f, d * .17f),
                    CornerRadius(d * .04f),
                    style = Stroke(d * .035f),
                )
                drawRoundRect(
                    Color(0xFF4A3524),
                    Offset(cx - w / 2, top),
                    Size(w, h),
                    CornerRadius(d * .06f),
                )
                drawRoundRect(
                    Color(0xFF5C4330),
                    Offset(cx - w / 2, top),
                    Size(w, h * .3f),
                    CornerRadius(d * .06f),
                )
                drawRoundRect(
                    palette.glow,
                    Offset(cx - d * .05f, top + h * .3f - d * .03f),
                    Size(d * .1f, d * .06f),
                    CornerRadius(d * .015f),
                )
                for (i in 0..5) drawCircle(
                    if (i < 4) palette.glow else Color.White.copy(alpha = .3f),
                    d * .022f,
                    Offset(cx - w * .36f + i * w * .145f, top + h * .78f),
                )
            }
            "vigil" -> {
                val at = Offset(cx - d * .4f, cy - d * .3f)
                drawRoundRect(
                    Color.Black.copy(alpha = .45f),
                    at,
                    Size(d * .8f, d * .6f),
                    CornerRadius(d * .05f),
                )
                listOf(.42f, .3f, .5f, .36f, .46f).forEachIndexed { i, w ->
                    val y = at.y + d * (.1f + i * .1f)
                    val alert = i == 2
                    if (alert)
                        drawRoundRect(
                            Color(0xFFFF5470).copy(alpha = .35f),
                            Offset(at.x + d * .03f, y - d * .035f),
                            Size(d * .74f, d * .07f),
                            CornerRadius(d * .02f),
                        )
                    drawLine(
                        if (alert) Color(0xFFFF5470) else palette.glow.copy(alpha = .55f),
                        Offset(at.x + d * .07f, y),
                        Offset(at.x + d * (.07f + w), y),
                        d * .022f,
                        StrokeCap.Round,
                    )
                }
                shield(Offset(cx + d * .3f, cy + d * .26f), d * .22f, palette.glow)
            }
            // Fase 3 scenes: metro map of routers, the belt in front of the server, and two
            // incident cards (one flipped).
            "route" -> {
                val pts =
                    listOf(
                        Offset(cx - d * .32f, cy + d * .25f),
                        Offset(cx - d * .3f, cy - d * .2f),
                        Offset(cx + d * .02f, cy - d * .3f),
                        Offset(cx + d * .05f, cy + d * .05f),
                        Offset(cx + d * .32f, cy + d * .24f),
                        Offset(cx + d * .3f, cy - d * .12f),
                    )
                val lines = listOf(0 to 1, 1 to 2, 2 to 5, 1 to 3, 3 to 4, 0 to 3, 3 to 5)
                val colors =
                    listOf(
                        Color(0xFFFFB300),
                        Color(0xFF7CF5FF),
                        Color(0xFFFF6B9A),
                        Color(0xFFB69CFF),
                    )
                lines.forEachIndexed { i, (a, b) ->
                    drawLine(
                        colors[i % 4].copy(alpha = if (i == 5) .35f else .9f),
                        pts[a],
                        pts[b],
                        d * .028f,
                        StrokeCap.Round,
                    )
                }
                pts.forEachIndexed { i, p ->
                    drawCircle(Color.White, d * .055f, p)
                    drawCircle(palette.base, d * .03f, p)
                    if (i == 3) drawCircle(palette.glow, d * .03f, p)
                }
            }
            "ddos" -> {
                drawRoundRect(
                    Color.White.copy(alpha = .85f),
                    Offset(cx - d * .2f, cy - d * .42f),
                    Size(d * .4f, d * .13f),
                    CornerRadius(d * .03f),
                )
                for (i in 0..2) {
                    val y = cy - d * .18f + i * d * .2f
                    val bad = i == 1
                    drawRoundRect(
                        if (bad) palette.glow else Color.White.copy(alpha = .22f),
                        Offset(cx - d * .36f, y),
                        Size(d * .72f, d * .13f),
                        CornerRadius(d * .03f),
                    )
                    drawLine(
                        if (bad) palette.base else Color.White.copy(alpha = .6f),
                        Offset(cx - d * .3f, y + d * .065f),
                        Offset(cx + d * .05f, y + d * .065f),
                        d * .025f,
                        StrokeCap.Round,
                    )
                }
                shield(Offset(cx + d * .3f, cy + d * .32f), d * .2f, Color(0xFFFFB300))
            }
            "pairs" -> {
                for (side in listOf(-1, 1)) {
                    val at = Offset(cx + side * d * .18f - d * .14f, cy - d * .2f)
                    drawRoundRect(
                        if (side < 0) palette.glow else Color(0xFF1F2636),
                        at,
                        Size(d * .28f, d * .4f),
                        CornerRadius(d * .04f),
                    )
                    if (side < 0)
                        for (k in 0..2) drawLine(
                            palette.base.copy(alpha = .6f),
                            at + Offset(d * .05f, d * (.09f + k * .11f)),
                            at + Offset(d * .23f, d * (.09f + k * .11f)),
                            d * .018f,
                            StrokeCap.Round,
                        )
                    else {
                        drawRoundRect(
                            Color.White.copy(alpha = .8f),
                            at,
                            Size(d * .28f, d * .4f),
                            CornerRadius(d * .04f),
                            style = Stroke(d * .012f),
                        )
                        drawLine(
                            palette.glow,
                            at + Offset(d * .06f, d * .2f),
                            at + Offset(d * .22f, d * .2f),
                            d * .03f,
                            StrokeCap.Round,
                        )
                    }
                }
                drawArc(
                    palette.glow.copy(alpha = .7f),
                    200f,
                    140f,
                    false,
                    Offset(cx - d * .2f, cy - d * .42f),
                    Size(d * .4f, d * .3f),
                    style = Stroke(d * .018f, cap = StrokeCap.Round),
                )
            }
            "bits" -> {
                shield(Offset(cx, cy), d * .62f, Color.White.copy(alpha = .08f))
                for (i in 0..7) {
                    val at = Offset(cx + (i % 4 - 1.5f) * d * .17f, cy + (i / 4 - .5f) * d * .26f)
                    val on = i in listOf(0, 2, 3, 6)
                    if (on)
                        drawLine(
                            palette.glow,
                            at - Offset(0f, d * .08f),
                            at + Offset(0f, d * .08f),
                            d * .035f,
                            StrokeCap.Round,
                        )
                    else
                        drawOval(
                            Color.White.copy(alpha = .6f),
                            at - Offset(d * .045f, d * .08f),
                            Size(d * .09f, d * .16f),
                            style = Stroke(d * .026f),
                        )
                }
            }
            "subnet" -> {
                val nodes =
                    List(6) {
                        val a = it * PI / 3 - PI / 2
                        Offset(cx + cos(a).toFloat() * d * .32f, cy + sin(a).toFloat() * d * .32f)
                    }
                nodes.forEachIndexed { i, n ->
                    drawLine(Color.White.copy(alpha = .3f), Offset(cx, cy), n, d * .012f)
                    drawLine(Color.White.copy(alpha = .15f), n, nodes[(i + 1) % 6], d * .008f)
                }
                nodes.forEachIndexed { i, n ->
                    drawCircle(
                        if (i % 3 == 0) palette.glow else Color.White.copy(alpha = .7f),
                        d * .05f,
                        n,
                    )
                }
                drawRoundRect(
                    palette.glow,
                    Offset(cx - d * .09f, cy - d * .06f),
                    Size(d * .18f, d * .12f),
                    CornerRadius(d * .025f),
                )
                for (k in 0..2) drawCircle(
                    palette.base,
                    d * .012f,
                    Offset(cx - d * .045f + k * d * .045f, cy),
                )
            }
            "ports" -> {
                drawLine(
                    Color.White.copy(alpha = .5f),
                    Offset(cx - d * .45f, cy + d * .12f),
                    Offset(cx - d * .16f, cy + d * .02f),
                    d * .04f,
                    StrokeCap.Round,
                )
                drawRoundRect(
                    palette.glow,
                    Offset(cx - d * .18f, cy - d * .13f),
                    Size(d * .26f, d * .26f),
                    CornerRadius(d * .05f),
                )
                for (y in listOf(-.06f, .06f)) drawLine(
                    Color.White,
                    Offset(cx + d * .08f, cy + d * y),
                    Offset(cx + d * .2f, cy + d * y),
                    d * .035f,
                    StrokeCap.Round,
                )
                drawRoundRect(
                    Color.White.copy(alpha = .18f),
                    Offset(cx + d * .24f, cy - d * .17f),
                    Size(d * .14f, d * .34f),
                    CornerRadius(d * .03f),
                )
                drawCircle(
                    palette.glow.copy(alpha = .8f),
                    d * .018f,
                    Offset(cx + d * .31f, cy - d * .22f),
                )
            }
            "phishing" -> {
                drawLine(
                    Color.White.copy(alpha = .6f),
                    Offset(cx + d * .05f, 0f),
                    Offset(cx + d * .05f, cy - d * .05f),
                    d * .014f,
                )
                val hook =
                    Path().apply {
                        moveTo(cx + d * .05f, cy - d * .05f)
                        lineTo(cx + d * .05f, cy + d * .12f)
                        quadraticTo(cx + d * .05f, cy + d * .26f, cx - d * .07f, cy + d * .24f)
                        quadraticTo(cx - d * .16f, cy + d * .2f, cx - d * .14f, cy + d * .08f)
                    }
                drawPath(hook, palette.glow, style = Stroke(d * .035f, cap = StrokeCap.Round))
                val env = Offset(cx - d * .42f, cy - d * .3f)
                drawRoundRect(
                    Color.White.copy(alpha = .85f),
                    env,
                    Size(d * .3f, d * .2f),
                    CornerRadius(d * .02f),
                )
                val flap =
                    Path().apply {
                        moveTo(env.x, env.y)
                        lineTo(env.x + d * .15f, env.y + d * .11f)
                        lineTo(env.x + d * .3f, env.y)
                    }
                drawPath(flap, palette.base, style = Stroke(d * .014f))
            }
            "sqlsafe" -> {
                for (i in 2 downTo 0) {
                    val top = cy - d * .22f + i * d * .13f
                    drawRect(
                        if (i == 0) palette.glow else Color.White.copy(alpha = .25f + .15f * i),
                        Offset(cx - d * .2f, top),
                        Size(d * .4f, d * .13f),
                    )
                    drawOval(
                        if (i == 0) palette.glow else Color.White.copy(alpha = .35f + .15f * i),
                        Offset(cx - d * .2f, top + d * .13f - d * .05f),
                        Size(d * .4f, d * .1f),
                    )
                }
                drawOval(
                    Color.White.copy(alpha = .9f),
                    Offset(cx - d * .2f, cy - d * .27f),
                    Size(d * .4f, d * .1f),
                )
                shield(Offset(cx + d * .26f, cy + d * .16f), d * .24f, palette.glow)
                drawLine(
                    palette.base,
                    Offset(cx + d * .21f, cy + d * .16f),
                    Offset(cx + d * .25f, cy + d * .2f),
                    d * .02f,
                    StrokeCap.Round,
                )
                drawLine(
                    palette.base,
                    Offset(cx + d * .25f, cy + d * .2f),
                    Offset(cx + d * .32f, cy + d * .11f),
                    d * .02f,
                    StrokeCap.Round,
                )
            }
            "logs" -> {
                val at = Offset(cx - d * .38f, cy - d * .28f)
                drawRoundRect(
                    Color.Black.copy(alpha = .35f),
                    at,
                    Size(d * .76f, d * .56f),
                    CornerRadius(d * .05f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = .1f),
                    at,
                    Size(d * .76f, d * .1f),
                    CornerRadius(d * .05f),
                )
                for (k in 0..2) drawCircle(
                    Color.White.copy(alpha = .5f),
                    d * .017f,
                    at + Offset(d * (.06f + k * .05f), d * .05f),
                )
                listOf(.5f, .38f, .56f, .3f).forEachIndexed { i, w ->
                    val y = at.y + d * (.18f + i * .09f)
                    drawLine(
                        if (i == 2) palette.glow else Color.White.copy(alpha = .45f),
                        Offset(at.x + d * .06f, y),
                        Offset(at.x + d * (.06f + w), y),
                        d * .028f,
                        StrokeCap.Round,
                    )
                }
            }
            "reaction",
            "choice" -> {
                drawCircle(Color.White.copy(alpha = .13f), d * .34f, Offset(cx, cy))
                val p =
                    Path().apply {
                        moveTo(cx + d * .1f, cy - d * .3f)
                        lineTo(cx - d * .15f, cy + d * .025f)
                        lineTo(cx + d * .015f, cy + d * .025f)
                        lineTo(cx - d * .07f, cy + d * .3f)
                        lineTo(cx + d * .18f, cy - d * .07f)
                        lineTo(cx + d * .015f, cy - d * .07f)
                        close()
                    }
                drawPath(p, palette.glow)
            }
            else -> {
                for (i in 0..3) {
                    val at =
                        Offset(
                            cx + (if (i % 2 == 0) -1 else 1) * d * .14f,
                            cy + (if (i < 2) -1 else 1) * d * .14f,
                        )
                    drawRoundRect(
                        listOf(
                            Color(0xFF386EFF),
                            Color(0xFF764DDE),
                            Color(0xFF1CA16D),
                            Color(0xFFDA6035),
                        )[i],
                        at - Offset(d * .12f, d * .12f),
                        Size(d * .24f, d * .24f),
                        CornerRadius(d * .045f),
                    )
                    when (i) {
                        0 -> {
                            drawLine(
                                palette.glow,
                                at - Offset(d * .065f, 0f),
                                at + Offset(d * .065f, 0f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                            drawLine(
                                palette.glow,
                                at - Offset(0f, d * .065f),
                                at + Offset(0f, d * .065f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                        }
                        1 ->
                            drawLine(
                                Color.White,
                                at - Offset(d * .065f, 0f),
                                at + Offset(d * .065f, 0f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                        2 -> {
                            drawLine(
                                Color.White,
                                at - Offset(d * .047f, d * .047f),
                                at + Offset(d * .047f, d * .047f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                            drawLine(
                                Color.White,
                                at - Offset(d * .047f, -d * .047f),
                                at + Offset(d * .047f, -d * .047f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                        }
                        else -> {
                            drawLine(
                                palette.glow,
                                at - Offset(d * .065f, 0f),
                                at + Offset(d * .065f, 0f),
                                d * .025f,
                                StrokeCap.Round,
                            )
                            drawCircle(palette.glow, d * .017f, at - Offset(0f, d * .052f))
                            drawCircle(palette.glow, d * .017f, at + Offset(0f, d * .052f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameTile(
    game: Game,
    level: Int?,
    open: () -> Unit,
    modifier: Modifier = Modifier,
    endless: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressClick(onClick = open)
    ) {
        GameArt(game, Modifier.fillMaxWidth().height(116.dp))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AreaChip(game.area)
            Text(
                game.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                game.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (endless) "Sin límite · explorar" else "${Formats.of(game.id).label} · explorar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CompassBoard(c: Challenge) {
    val opposite = c.board[1].toBoolean()
    val base = if (opposite) Color(0xFFB7274F) else Color(0xFF2E5BDE)
    val direction = c.board[0].toInt()
    Canvas(
        Modifier.fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(base)
            .semantics {
                contentDescription =
                    "Flecha hacia ${listOf("arriba","derecha","abajo","izquierda")[direction]}"
            }
    ) {
        drawCircle(Color.White.copy(alpha = .12f), size.height * .37f, center)
        arrow(center, size.height * .48f, direction, Color.White)
        ornaments(Color.White)
    }
    Text(
        if (opposite) "Responde al contrario de la flecha" else "Responde hacia donde apunta",
        Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun BalanceBoard(c: Challenge) {
    val boxes = c.board[0].toInt()
    val extra = c.board[1]
    val total = c.board[2]
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(surface, Color(0xFFFF9447).copy(alpha = .10f).compositeOver(surface))
                )
            )
            .border(1.dp, Color(0xFFFF9447).copy(alpha = .25f), RoundedCornerShape(28.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val color = Color(0xFFFFB067).copy(alpha = .85f)
            drawLine(
                color,
                Offset(w * .5f, h * .34f),
                Offset(w * .5f, h * .9f),
                5.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * .15f, h * .34f),
                Offset(w * .85f, h * .34f),
                4.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * .36f, h * .91f),
                Offset(w * .64f, h * .91f),
                5.dp.toPx(),
                StrokeCap.Round,
            )
            for (x in listOf(.25f, .75f)) {
                drawLine(
                    color,
                    Offset(w * x, h * .34f),
                    Offset(w * (x - .13f), h * .71f),
                    2.dp.toPx(),
                )
                drawLine(
                    color,
                    Offset(w * x, h * .34f),
                    Offset(w * (x + .13f), h * .71f),
                    2.dp.toPx(),
                )
                drawArc(
                    color,
                    0f,
                    180f,
                    false,
                    Offset(w * (x - .13f), h * .65f),
                    Size(w * .26f, h * .12f),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(boxes) {
                        Box(
                            Modifier.size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFA248)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("?", color = Color(0xFF3A1E00), fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("+ $extra kg", color = ink, style = MaterialTheme.typography.titleMedium)
            }
            Text("=", color = muted, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF4C8DFF), Color(0xFF2E5BDE)))
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$total kg",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}
