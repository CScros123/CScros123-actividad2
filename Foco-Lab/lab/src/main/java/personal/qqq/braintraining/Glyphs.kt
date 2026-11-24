package personal.qqq.braintraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Symbols drawn as vectors instead of font characters, so they look the same on every device and
 * scale cleanly.
 */
private val glyphNames =
    mapOf(
        "↑" to "flecha arriba",
        "→" to "flecha derecha",
        "↓" to "flecha abajo",
        "←" to "flecha izquierda",
        "⊕" to "círculo con cruz",
        "⊗" to "círculo con aspa",
        "⊙" to "círculo con punto",
        "◎" to "doble círculo",
        "○" to "círculo",
        "△" to "triángulo",
        "□" to "cuadrado",
        "◇" to "rombo",
    )

fun isGlyph(s: String) = s in glyphNames

@Composable
fun Glyph(
    symbol: String,
    size: Dp,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size).semantics { contentDescription = glyphNames[symbol] ?: symbol }) {
        drawGlyph(symbol, color)
    }
}

fun DrawScope.drawGlyph(symbol: String, color: Color) {
    val d = size.minDimension
    val c = center
    val w = d * .12f
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val soft = color.copy(alpha = .16f)
    fun ring(r: Float = d * .4f) {
        drawCircle(soft, r, c)
        drawCircle(color, r, c, style = stroke)
    }
    when (symbol) {
        "↑",
        "→",
        "↓",
        "←" ->
            rotate(
                when (symbol) {
                    "↑" -> 0f
                    "→" -> 90f
                    "↓" -> 180f
                    else -> 270f
                },
                c,
            ) {
                drawLine(
                    color,
                    Offset(c.x, c.y + d * .38f),
                    Offset(c.x, c.y - d * .2f),
                    w * 1.15f,
                    StrokeCap.Round,
                )
                drawPath(
                    Path().apply {
                        moveTo(c.x, c.y - d * .44f)
                        lineTo(c.x + d * .27f, c.y - d * .1f)
                        lineTo(c.x - d * .27f, c.y - d * .1f)
                        close()
                    },
                    color,
                )
            }
        "⊕" -> {
            ring()
            drawLine(
                color,
                Offset(c.x, c.y - d * .4f),
                Offset(c.x, c.y + d * .4f),
                w,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(c.x - d * .4f, c.y),
                Offset(c.x + d * .4f, c.y),
                w,
                StrokeCap.Round,
            )
        }
        "⊗" -> {
            ring()
            val k = d * .4f * .7071f
            drawLine(color, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), w, StrokeCap.Round)
        }
        "⊙" -> {
            ring()
            drawCircle(color, d * .12f, c)
        }
        "◎" -> {
            ring()
            drawCircle(color, d * .2f, c, style = stroke)
        }
        "○" -> ring()
        "△" -> {
            val p =
                Path().apply {
                    moveTo(c.x, c.y - d * .4f)
                    lineTo(c.x + d * .42f, c.y + d * .34f)
                    lineTo(c.x - d * .42f, c.y + d * .34f)
                    close()
                }
            drawPath(p, soft)
            drawPath(p, color, style = stroke)
        }
        "□" -> {
            val s = d * .72f
            val tl = Offset(c.x - s / 2, c.y - s / 2)
            drawRoundRect(soft, tl, Size(s, s), CornerRadius(d * .1f))
            drawRoundRect(color, tl, Size(s, s), CornerRadius(d * .1f), style = stroke)
        }
        "◇" -> {
            val p =
                Path().apply {
                    moveTo(c.x, c.y - d * .44f)
                    lineTo(c.x + d * .36f, c.y)
                    lineTo(c.x, c.y + d * .44f)
                    lineTo(c.x - d * .36f, c.y)
                    close()
                }
            drawPath(p, soft)
            drawPath(p, color, style = stroke)
        }
    }
}

/**
 * Text where glyph characters are replaced by vector symbols. Falls back to plain text when there
 * are none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlyphText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (text.none { isGlyph(it.toString()) }) {
        Text(text, modifier, color = color, style = style, textAlign = TextAlign.Center)
        return
    }
    val tokens = mutableListOf<String>()
    val word = StringBuilder()
    for (ch in text) {
        val s = ch.toString()
        if (isGlyph(s) || ch == ' ') {
            if (word.isNotEmpty()) {
                tokens += word.toString()
                word.clear()
            }
            if (ch != ' ') tokens += s
        } else word.append(ch)
    }
    if (word.isNotEmpty()) tokens += word.toString()
    val glyphSize = (style.fontSize.value * 1.05f).dp
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tokens.forEach { t ->
            if (isGlyph(t))
                Glyph(
                    t,
                    glyphSize,
                    MaterialTheme.colorScheme.primary,
                    Modifier.align(Alignment.CenterVertically),
                )
            else Text(t, Modifier.align(Alignment.CenterVertically), color = color, style = style)
        }
    }
}
