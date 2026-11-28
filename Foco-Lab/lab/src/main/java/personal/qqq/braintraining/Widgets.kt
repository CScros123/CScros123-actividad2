package personal.qqq.braintraining

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import personal.qqq.braintraining.core.Games
import personal.qqq.braintraining.design.Mint

/** The same geometric C and equals mark as the adaptive launcher icon. */
@Composable
fun CognitiveMark(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF3667FF))) {
        val d = size.minDimension
        val c = center
        drawArc(
            Color(0xFFBAF9DC),
            48f,
            264f,
            false,
            topLeft = c - Offset(d * .29f, d * .29f),
            size = Size(d * .58f, d * .58f),
            style = Stroke(d * .105f, cap = StrokeCap.Round),
        )
        for (y in listOf(-.065f, .065f)) drawLine(
            Color.White,
            c + Offset(d * .055f, d * y),
            c + Offset(d * .29f, d * y),
            d * .055f,
            StrokeCap.Round,
        )
    }
}

@Composable
fun RoutineHero(today: LocalDate, start: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFF11151E))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("TU PRÁCTICA DE HOY", Color(0xFFB8C2D8))
            Spacer(Modifier.weight(1f))
            Text(
                today
                    .format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))
                    .uppercase(Locale("es")),
                Modifier.clip(CircleShape)
                    .background(Color(0xFF2D3546))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Piensa.\nPrueba.\nDescubre.",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
            }
            Box(Modifier.width(128.dp).height(152.dp)) {
                GameArt(
                    Games.get("math"),
                    Modifier.align(Alignment.TopStart)
                        .rotate(-9f)
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp)),
                )
                GameArt(
                    Games.get("orbit"),
                    Modifier.align(Alignment.BottomEnd)
                        .rotate(9f)
                        .size(86.dp)
                        .clip(RoundedCornerShape(25.dp)),
                )
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = 6.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = Color(0xFF073B29),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Text(
            "4 juegos para practicar y una idea para llevar a tu día.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC5CEDF),
        )
        PrimaryButton(
            "Empezar rutina diaria",
            start,
            true,
            ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF062D1E)),
            Icons.Rounded.PlayArrow,
        )
    }
}

@Composable
fun Metric(title: String, subtitle: String, icon: ImageVector, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val secondary = icon == Icons.Rounded.WorkspacePremium
    val accent = if (secondary) colors.secondary else colors.primary
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (secondary) colors.secondaryContainer else colors.primaryContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                Modifier.size(23.dp),
                tint = if (secondary) colors.onSecondary else colors.onPrimary,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = if (secondary) colors.onSecondaryContainer else colors.onPrimaryContainer,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (secondary) colors.onSecondaryContainer else colors.onPrimaryContainer,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    icon: ImageVector? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    val feedback = LocalFeedback.current
    val scale by
        animateFloatAsState(
            if (pressed && motion) .97f else 1f,
            if (motion) spring(stiffness = 650f) else snap(),
            label = "Botón",
        )
    Button(
        {
            feedback(FeedbackCue.TAP)
            onClick()
        },
        Modifier.fillMaxWidth().heightIn(min = 56.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        interactionSource = source,
        colors = colors,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CompletionMark() {
    val motion = LocalMotionEnabled.current
    var appeared by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by
        animateFloatAsState(
            if (appeared) 1f else .7f,
            if (motion) spring(dampingRatio = .65f, stiffness = 300f) else snap(),
            label = "Completado",
        )
    Box(
        Modifier.size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            null,
            Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}
