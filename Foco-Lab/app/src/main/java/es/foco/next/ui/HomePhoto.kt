package es.foco.next.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fondo de los temas fotográficos: la foto (ya a tamaño de pantalla) y dos velos para leer bien el
 * reloj arriba y el dock abajo. Todo estático y cacheado: no se redibuja al deslizar ni al hablar el
 * búho.
 */
@Composable
fun PhotoBackground(photo: ImageBitmap, hillTop: Float) {
    val c = LocalHome.current
    Box(Modifier.fillMaxSize()) {
        Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val top = Brush.verticalGradient(listOf(c.background.copy(alpha = .55f), Color.Transparent), 0f, size.height * .38f)
                val start = if (hillTop > 0) hillTop - 40.dp.toPx() else size.height * .62f
                val bottom = Brush.verticalGradient(listOf(Color.Transparent, c.hill), start, start + 120.dp.toPx())
                onDrawBehind {
                    drawRect(top, size = Size(size.width, size.height * .38f))
                    drawRect(bottom, topLeft = Offset(0f, start), size = Size(size.width, size.height - start))
                }
            }
        )
    }
}

/**
 * El búho como voz de Foco: su último mensaje (de los últimos 30 min) arriba del inicio, como en
 * el prototipo. "Ahora no" lo cierra; nunca insiste.
 */
@Composable
fun OwlBanner(title: String, text: String, target: String, onOpen: () -> Unit, onClose: () -> Unit) {
    val c = LocalHome.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
            .border(1.dp, lerp(c.card, c.accent, .25f), RoundedCornerShape(20.dp))
            .padding(12.dp)
            .semantics { contentDescription = "El búho dice: $text" },
        verticalAlignment = Alignment.Top,
    ) {
        Image(painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(40.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            HomeText("🦉 Búho · ${title.ifBlank { "ahora" }}", 11.5.sp, c.muted, FontWeight.SemiBold)
            HomeText(text, 13.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeText("Ahora no", 12.5.sp, c.muted, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(8.dp))
                if (target != "cuidado")
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent).clickable(onClick = onOpen)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.material3.Text(
                            when (target) { "reto" -> "Hacer el reto"; "respirar" -> "Respirar 30 s"; else -> "Vamos" },
                            color = if (c.text.lum() > .5f) c.background else c.card.copy(alpha = 1f),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
            }
        }
    }
}

private fun Color.lum(): Float = .2126f * red + .7152f * green + .0722f * blue
