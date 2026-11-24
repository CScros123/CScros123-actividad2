package es.foco.next.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.home.Aduana
import kotlinx.coroutines.delay

/**
 * Peaje de intención ("Aduana"): antes de abrir una app de ocio, 5 s de respiración y elegir 1, 3 o 5
 * min. El bloque se paga igual (Regla 4 intacta); elegir menos solo hace que Foco la cierre antes.
 * De noche o con el ocio apartado en esta red, no ofrece abrir: solo informa.
 */
@Composable
fun AduanaSheet(appLabel: String, outlook: LeisureOutlook, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    val motion = LocalMotion.current
    val closed = outlook.night || outlook.hiddenHere
    // Respiración guiada: inhalar (1,25 s) y exhalar (1,25 s). Hasta completar la exhalación los
    // botones están atenuados y no responden; al habilitarse, un toque háptico leve.
    val circle = remember { androidx.compose.animation.core.Animatable(.72f) }
    var phase by remember { mutableIntStateOf(0) } // 0 inhala · 1 exhala · 2 listo
    LaunchedEffect(Unit) {
        val half = (Aduana.UNLOCK_MS / 2).toInt()
        circle.animateTo(1f, tween(half, easing = FastOutSlowInEasing))
        phase = 1
        circle.animateTo(.72f, tween(half, easing = FastOutSlowInEasing))
        phase = 2
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // Después sigue respirando despacio (4 s), solo con movimiento permitido.
        while (motion) {
            circle.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            circle.animateTo(.72f, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    val ready = phase == 2
    val enabledAlpha by androidx.compose.animation.core.animateFloatAsState(if (ready) 1f else .38f, tween(300), label = "listo")
    Box(
        // Velo casi opaco: el cajón de debajo (y su "Filtrar por nombre…") no asoma.
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .84f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                .clip(RoundedCornerShape(28.dp)).background(c.card.copy(alpha = 1f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeText("Antes de abrir $appLabel", 17.sp, weight = FontWeight.Bold)
            // Arriba, solo el dato objetivo (modo y límite, o por qué no se puede).
            HomeText(outlook.text, 12.5.sp, c.muted, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val k = circle.value // se lee al dibujar: solo se redibuja el círculo
                    drawCircle(lerp(c.card, c.accent, .12f), size.minDimension / 2 * k)
                    drawCircle(lerp(c.card, c.accent, .3f), size.minDimension / 2 * k * .66f)
                }
                if (ready) Text("✓", color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(10.dp))
            HomeText(
                when {
                    phase == 0 -> "Inhala…"
                    phase == 1 -> "Exhala…"
                    closed -> "Ahora no se puede"
                    else -> "¿Cuánto tiempo quieres?"
                },
                15.sp, weight = FontWeight.SemiBold,
            )
            if (!closed) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().graphicsLayer { alpha = enabledAlpha }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in Aduana.CHOICES_MIN) {
                        val recommended = m == Aduana.RECOMMENDED_MIN
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (recommended) c.accent else lerp(c.card, c.accent, .1f))
                                .border(if (recommended) 2.dp else 1.dp, lerp(c.card, c.accent, if (recommended) .55f else .2f), RoundedCornerShape(16.dp))
                                .clickable(enabled = ready) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onChoose(m)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$m min", color = if (recommended) c.card.copy(alpha = 1f) else c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (recommended) Text("recomendado", color = c.card.copy(alpha = .85f), fontSize = 10.sp)
                            }
                        }
                    }
                }
                // Una sola frase, debajo de los botones.
                HomeText(
                    if (outlook.focus) "Al acabar el tiempo, Foco cierra la app." else "Es tu intención: al acabar te aviso y tú decides.",
                    11.5.sp, c.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Mejor no, volver",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(10.dp),
                color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
    }
}
