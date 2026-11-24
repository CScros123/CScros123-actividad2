package personal.qqq.braintraining

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*

/* Fase 7, lote piloto: tableros propios para Subredes, Phishing y Puertos (docs/FASE-7-PILOTO.md).
 * Each board only reports the final answer through its callback; scoring, sounds for right/wrong and saving stay in BrainViewModel. */

private val NetAmber = Color(0xFFFFB300)
private val NetInk = Color(0xFF2B1D00)

// ---------------------------------------------------------------- Subredes

private val SubnetGap = 6.dp
private val SubnetCellGap = 3.dp

/**
 * Vertical bar + knob at the boundary of prefix [p] over the 4×8 bit grid. No allocations: only
 * value-class geometry.
 */
private fun DrawScope.boundary(p: Int, color: Color) {
    val g = SubnetGap.toPx()
    val cg = SubnetCellGap.toPx()
    val h = (size.height - 3 * g) / 4f
    val cw = (size.width - 7 * cg) / 8f
    val row = if (p <= 0) 0 else (p - 1) / 8
    val col = p - row * 8
    val edge = 2.dp.toPx()
    val x =
        (when (col) {
                0 -> 0f
                8 -> size.width
                else -> col * (cw + cg) - cg / 2f
            })
            .coerceIn(edge, size.width - edge)
    val top = row * (h + g)
    drawRoundRect(
        color,
        Offset(x - edge, top - 3.dp.toPx()),
        Size(edge * 2, h + 6.dp.toPx()),
        CornerRadius(edge),
    )
    drawCircle(color, 6.dp.toPx(), Offset(x, top + h / 2f))
}

@Composable
private fun BitCell(bit: Int, other: Int?, network: Boolean, diff: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotionEnabled.current
    val fill by
        animateColorAsState(
            if (network) NetAmber else colors.surfaceVariant,
            if (motion) tween(140) else snap(),
            label = "Bit",
        )
    val ink = if (network) NetInk else colors.onSurfaceVariant
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .then(
                if (diff) Modifier.border(1.5.dp, colors.error, RoundedCornerShape(8.dp))
                else Modifier
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$bit",
            color = ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        if (other != null)
            Text(
                "$other",
                color = ink.copy(alpha = .7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
    }
}

@Composable
private fun Readout(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Subredes: 32 bit cells in 4 octets. Drag the boundary (or tap a cell, or use − / +) to set the
 * prefix; network, broadcast and hosts update live. [submit] receives the chosen prefix.
 */
@Composable
fun SubnetBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    picked: List<Int>,
    submit: (Int) -> Unit,
) {
    val t = c.subnet ?: return
    val colors = MaterialTheme.colorScheme
    val cue = LocalFeedback.current
    var prefix by remember(c) { mutableIntStateOf(t.start) }
    val shown = if (feedback) (picked.firstOrNull() ?: prefix) else prefix
    val pair = t.format == Pilot.PAIR
    // Dragging ticks only when the boundary crosses an octet (Lote B): one TAP per bit saturated
    // the vibration.
    fun move(p: Int, drag: Boolean = false) {
        val v = p.coerceIn(0, 32)
        if (answering && v != prefix) {
            val crossed = v / 8 != prefix / 8
            prefix = v
            if (!drag || crossed) cue(FeedbackCue.TAP)
        }
    }
    val rowHeight = if (pair) 54.dp else 40.dp
    val ink = colors.onSurface
    val good = colors.secondary
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Readout(if (pair) "A" else "Dirección", Ipv4.text(t.addr))
            if (pair) Readout("B", Ipv4.text(t.other))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f)
                    .clearAndSetSemantics {
                        contentDescription =
                            "Cuadrícula de 32 bits en 4 octetos. Frontera en /$shown: $shown bits de red y ${32-shown} de host. Arrastra o toca una casilla, o usa los botones menos y más."
                    }
                    .pointerInput(c, answering) {
                        if (answering)
                            detectTapGestures { o ->
                                val row = (o.y / size.height * 4).toInt().coerceIn(0, 3)
                                val col = (o.x / size.width * 8).toInt().coerceIn(0, 7)
                                move(row * 8 + col + 1)
                            }
                    }
                    .pointerInput(c, answering) {
                        if (answering) {
                            fun at(o: Offset): Int {
                                val row = (o.y / size.height * 4).toInt().coerceIn(0, 3)
                                val col = (o.x / size.width * 8).roundToInt().coerceIn(0, 8)
                                return row * 8 + col
                            }
                            detectDragGestures(onDragStart = { move(at(it), true) }) { change, _ ->
                                change.consume()
                                move(at(change.position), true)
                            }
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (feedback && shown != t.answer) boundary(t.answer, good)
                        boundary(shown, ink)
                    },
                verticalArrangement = Arrangement.spacedBy(SubnetGap),
            ) {
                for (row in 0 until 4) Row(
                    Modifier.fillMaxWidth().height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(SubnetCellGap),
                ) {
                    for (col in 0 until 8) {
                        val i = row * 8 + col
                        val a = Ipv4.bit(t.addr, i)
                        val b = if (pair) Ipv4.bit(t.other, i) else null
                        BitCell(
                            a,
                            b,
                            i < shown,
                            pair && (t.hints || feedback) && a != b,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            // Octet values of the address (and of B) next to each row.
            Column(Modifier.width(52.dp), verticalArrangement = Arrangement.spacedBy(SubnetGap)) {
                for (row in 0 until 4) Box(
                    Modifier.fillMaxWidth().height(rowHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        if (pair) "${Ipv4.octet(t.addr,row)}\n${Ipv4.octet(t.other,row)}"
                        else "${Ipv4.octet(t.addr,row)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        val maskHidden = t.format == Pilot.MASK && !feedback
        val hostsHidden = t.format == Pilot.HOSTS && !t.hints && !feedback
        Panel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "/$shown",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Readout("Máscara", if (maskHidden) "¿?" else Ipv4.text(Ipv4.mask(shown)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Readout(
                    if (pair) "Red de A" else "Red",
                    Ipv4.text(Ipv4.network(t.addr, shown)),
                    Modifier.weight(1f),
                )
                if (pair)
                    Readout(
                        "Red de B",
                        Ipv4.text(Ipv4.network(t.other, shown)),
                        Modifier.weight(1f),
                    )
                else
                    Readout(
                        "Broadcast",
                        Ipv4.text(Ipv4.broadcast(t.addr, shown)),
                        Modifier.weight(1f),
                    )
            }
            if (pair) {
                val same = Ipv4.network(t.addr, shown) == Ipv4.network(t.other, shown)
                Readout(
                    "¿Misma red?",
                    if (!t.hints && !feedback) "¿?" else if (same) "Sí" else "No",
                )
            } else Readout("Hosts usables", if (hostsHidden) "¿?" else "${Ipv4.hosts(shown)}")
            if (feedback && shown != t.answer)
                Text(
                    "Correcto: /${t.answer} (barra verde)",
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton({ move(prefix - 1) }, enabled = answering && prefix > 0) {
                Icon(Icons.Rounded.Remove, "Un bit menos de red")
            }
            FilledTonalIconButton({ move(prefix + 1) }, enabled = answering && prefix < 32) {
                Icon(Icons.Rounded.Add, "Un bit más de red")
            }
            Button(
                { submit(prefix) },
                Modifier.weight(1f).heightIn(min = 52.dp),
                enabled = answering,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Comprobar /$prefix", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- Phishing

private fun marked(
    text: String,
    offset: Int,
    start: Int,
    end: Int,
    style: SpanStyle?,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (style != null) {
        val s = (start - offset).coerceAtLeast(0)
        val e = (end - offset).coerceAtMost(text.length)
        if (s < e) addStyle(style, s, e)
    }
}

/**
 * One message rendered as mail, link bar or SMS, with the clue span highlighted when [clue] is not
 * null.
 */
@Composable
private fun MessageCard(t: InboxTask, clue: SpanStyle?) {
    val colors = MaterialTheme.colorScheme
    var offset = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (line in t.text.split("\n")) {
            val at = offset
            offset += line.length + 1
            fun part(prefix: String) =
                marked(line.removePrefix(prefix), at + prefix.length, t.clueStart, t.clueEnd, clue)
            when {
                line.startsWith("De: ") ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val name = line.removePrefix("De: ").substringBefore(" <").trim()
                        Box(
                            Modifier.size(42.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            part("De: "),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                line.startsWith("Asunto: ") ->
                    Text(part("Asunto: "), style = MaterialTheme.typography.titleMedium)
                line.startsWith("Adjunto: ") ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Attachment,
                            "Adjunto",
                            Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant,
                        )
                        Text(part("Adjunto: "), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                line.startsWith("Enlace: ") ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val https = line.contains("https://")
                        Icon(
                            if (https) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            if (https) "Conexión cifrada" else "Sin cifrar",
                            Modifier.size(18.dp),
                            tint = if (https) colors.onSurfaceVariant else colors.error,
                        )
                        Text(
                            part("Enlace: "),
                            Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                        )
                    }
                line.startsWith("SMS: ") ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Sms,
                            "SMS",
                            Modifier.size(20.dp),
                            tint = colors.onSurfaceVariant,
                        )
                        Text(
                            part("SMS: "),
                            Modifier.clip(
                                    RoundedCornerShape(
                                        topEnd = 16.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp,
                                    )
                                )
                                .background(colors.surfaceVariant)
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                else ->
                    Text(
                        part(""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
            }
        }
    }
}

/**
 * Phishing: the message arrives as a card in an inbox stack. Swipe right = Seguro (0), left =
 * Trampa (1); buttons and accessibility actions do the same. After answering, the exact clue of the
 * bank entry is highlighted.
 */
@Composable
fun InboxBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    picked: List<Int>,
    choose: (Int) -> Unit,
) {
    val t = c.inbox ?: return
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val drag = remember(c) { Animatable(0f) }
    val enter = remember(c) { Animatable(if (motion) 1f else 0f) }
    LaunchedEffect(c) {
        if (motion) enter.animateTo(0f, spring(dampingRatio = .82f, stiffness = 260f))
    }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val away =
        with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() * 1.3f }
    // After the card flies off it comes back in place (fade and scale, not a slide) to show the
    // clue.
    val back = remember(c) { Animatable(1f) }
    fun commit(i: Int) {
        if (!answering) return
        choose(i)
        scope.launch {
            if (motion) {
                drag.animateTo(
                    if (i == 0) away else -away,
                    tween(240, easing = FastOutLinearInEasing),
                )
                back.snapTo(0f)
                drag.snapTo(0f)
                back.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            } else drag.snapTo(0f)
        }
    }
    val wrong = feedback && picked.firstOrNull() != c.answer
    val clue =
        if (feedback)
            SpanStyle(
                background = if (t.phishing) colors.errorContainer else colors.secondaryContainer,
                color = if (t.phishing) colors.onErrorContainer else colors.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                textDecoration = if (wrong) TextDecoration.Underline else null,
            )
        else null
    val safeInk = colors.secondary
    val trapInk = colors.error
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Email, null, Modifier.size(20.dp), tint = colors.primary)
            Text(
                "Bandeja de entrada",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Box {
            // Two cards peeking behind: the rest of the inbox.
            for (k in 2 downTo 1) Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        translationY = k * 10.dp.toPx()
                        scaleX = 1f - k * .04f
                        alpha = .6f / k
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
            )
            Box(
                Modifier.fillMaxWidth()
                    .graphicsLayer {
                        translationX = drag.value + enter.value * 420.dp.toPx()
                        rotationZ =
                            if (motion) (drag.value / threshold * 4f).coerceIn(-10f, 10f) else 0f
                        alpha = back.value
                        val k = .92f + .08f * back.value
                        scaleX = k
                        scaleY = k
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(
                        if (feedback) 2.dp else 1.dp,
                        if (!feedback) colors.outlineVariant
                        else if (t.phishing) colors.error else colors.secondary,
                        RoundedCornerShape(24.dp),
                    )
                    .pointerInput(c, answering) {
                        if (answering) {
                            var armed = false
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val v = drag.value
                                    armed = false
                                    when {
                                        v > threshold -> commit(0)
                                        v < -threshold -> commit(1)
                                        else ->
                                            scope.launch {
                                                drag.animateTo(0f, if (motion) spring() else snap())
                                            }
                                    }
                                },
                                onDragCancel = {
                                    armed = false
                                    scope.launch { drag.snapTo(0f) }
                                },
                            ) { change, dx ->
                                change.consume()
                                val v = drag.value + dx
                                if (!armed && kotlin.math.abs(v) > threshold) {
                                    armed = true
                                    cue(FeedbackCue.TAP)
                                } else if (armed && kotlin.math.abs(v) <= threshold) armed = false
                                scope.launch { drag.snapTo(v) }
                            }
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Marcar como seguro") {
                                    commit(0)
                                    answering
                                },
                                CustomAccessibilityAction("Marcar como trampa") {
                                    commit(1)
                                    answering
                                },
                            )
                        if (feedback)
                            stateDescription =
                                if (t.phishing) "Era una trampa. Pista: ${t.clue}"
                                else "Era seguro. Pista: ${t.clue}"
                    }
                    .padding(18.dp)
            ) {
                MessageCard(t, clue)
                // Stamps that appear while swiping.
                Text(
                    "SEGURO",
                    Modifier.align(Alignment.TopEnd)
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            alpha = (drag.value / threshold).coerceIn(0f, 1f)
                            rotationZ = 12f
                        }
                        .border(2.dp, safeInk, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = safeInk,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "TRAMPA",
                    Modifier.align(Alignment.TopStart)
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            alpha = (-drag.value / threshold).coerceIn(0f, 1f)
                            rotationZ = -12f
                        }
                        .border(2.dp, trapInk, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = trapInk,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        if (feedback)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (t.phishing) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    null,
                    Modifier.size(20.dp),
                    tint = if (t.phishing) colors.error else colors.secondary,
                )
                Text(
                    (if (t.phishing) "Era una trampa · pista: " else "Era seguro · clave: ") +
                        t.clue,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (t.phishing) colors.error else colors.secondary,
                )
            }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                { commit(1) },
                Modifier.weight(1f).heightIn(min = 56.dp),
                enabled = answering,
                shape = RoundedCornerShape(18.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (answering) trapInk else colors.outlineVariant,
                    ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Trampa", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                { commit(0) },
                Modifier.weight(1f).heightIn(min = 56.dp),
                enabled = answering,
                shape = RoundedCornerShape(18.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (answering) safeInk else colors.outlineVariant,
                    ),
            ) {
                Text("Seguro", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Puertos

/**
 * Puertos: a firewall panel with one switch per port. Open rows show packets flowing (only with
 * motion enabled; drawn in the draw phase from one infinite transition, no recomposition per
 * frame). [submit] receives the indices left open. Paso 1: rows show only the port (the service
 * only at levels 1–2, both after answering); ORDER tasks list the rules on top. When the rules are
 * applied, one test packet crosses every row: it passes the open ones and is dropped at the closed
 * ones.
 */
@Composable
fun FirewallBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    submit: (List<Int>) -> Unit,
) {
    val t = c.firewall ?: return
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val open = remember(c) { t.start.toMutableStateList() }
    var hint by remember(c) { mutableStateOf(false) }
    val flow =
        if (motion && !feedback)
            rememberInfiniteTransition(label = "Tráfico")
                .animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(1600, easing = LinearEasing)),
                    label = "Paquetes",
                )
        else null
    val packet = colors.secondary
    // "Regla aplicada": an amber scan line sweeps the panel once when the rules are submitted (Lote
    // B).
    val sweep = remember(c) { Animatable(if (feedback) 1f else 0f) }
    // Test packets: 0 → 1 while they cross the rows (Juice.Reveal); without motion they are not
    // drawn and the readout says it all.
    val probe = remember(c) { Animatable(if (feedback) 1f else 0f) }
    LaunchedEffect(feedback) {
        if (feedback && motion && sweep.value == 0f)
            sweep.animateTo(1f, tween(Juice.Settle, easing = Juice.EaseOut))
        else if (feedback) sweep.snapTo(1f)
    }
    LaunchedEffect(feedback) {
        if (feedback && probe.value == 0f)
            probe.go(1f, Juice.tween(Juice.Reveal, motion, Juice.Linear), motion)
    }
    val good = t.rows.indices.count { open[it] == t.expected[it] }
    Column(
        Modifier.drawWithContent {
            drawContent()
            val v = sweep.value
            if (v > 0f && v < 1f) {
                val y = size.height * v
                val band = 28.dp.toPx()
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, NetAmber.copy(alpha = .35f), Color.Transparent),
                        y - band,
                        y + band,
                    ),
                    Offset(0f, y - band),
                    Size(size.width, band * 2),
                )
                drawRect(
                    NetAmber.copy(alpha = .9f),
                    Offset(0f, y - 1.dp.toPx()),
                    Size(size.width, 2.dp.toPx()),
                )
            }
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), tint = NetAmber)
            Text(
                "Firewall · ${open.count{it}} de ${open.size} abiertos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (answering)
                TextButton({ hint = !hint }) {
                    Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Método")
                }
        }
        if (hint && !feedback)
            Text(t.hint, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        if (t.rules.isNotEmpty())
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Juice.NetBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Reglas · primera coincidencia",
                    style = MaterialTheme.typography.labelLarge,
                    color = NetAmber,
                )
                t.rules.forEachIndexed { i, rule ->
                    Text(
                        "${i+1}  ${rule.label}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (rule.allow) Juice.Good else Juice.Bad,
                    )
                }
                Text(
                    "—  sin coincidencia: DENEGAR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = .7f),
                )
            }
        else
            Text(
                "Interruptor encendido = puerto abierto a Internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        t.rows.forEachIndexed { i, row ->
            val isOpen = open[i]
            val wrong = feedback && isOpen != t.expected[i]
            val fixed = feedback && !wrong && t.start[i] != t.expected[i]
            val fill by
                animateColorAsState(
                    if (isOpen) colors.secondaryContainer else colors.surface,
                    if (motion) tween(160) else snap(),
                    label = "Puerto",
                )
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(fill)
                    .border(
                        if (wrong || fixed) 2.dp else 1.dp,
                        if (wrong) colors.error
                        else if (fixed) colors.secondary else colors.outlineVariant,
                        RoundedCornerShape(18.dp),
                    )
                    .toggleable(value = isOpen, enabled = answering, role = Role.Switch) {
                        open[i] = it
                        cue(FeedbackCue.TAP)
                    }
                    .drawBehind {
                        val f = flow
                        val r = 3.dp.toPx()
                        val y = size.height - 6.dp.toPx()
                        if (isOpen && f != null) {
                            val p = f.value
                            for (k in 0 until 3) drawCircle(
                                packet,
                                r,
                                Offset(((p + k / 3f) % 1f) * size.width, y),
                            )
                        }
                        val q = probe.value
                        if (feedback && motion && q < 1f) {
                            val wall = size.width * .55f
                            val x = size.width * q
                            if (isOpen) drawCircle(Juice.Good, r * 1.6f, Offset(x, y))
                            else if (x < wall) drawCircle(Juice.Bad, r * 1.6f, Offset(x, y))
                            else {
                                val k = ((x - wall) / (size.width - wall)).coerceIn(0f, 1f)
                                drawCircle(
                                    Juice.Bad.copy(alpha = 1f - k),
                                    r * 1.6f * (1f + k),
                                    Offset(wall, y),
                                )
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    ":${row.port}",
                    Modifier.widthIn(min = 72.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = if (isOpen) colors.onSecondaryContainer else colors.onSurface,
                )
                Column(Modifier.weight(1f)) {
                    if (t.labels || feedback)
                        Text(
                            row.service,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOpen) colors.onSecondaryContainer else colors.onSurface,
                        )
                    if (feedback)
                        Text(
                            if (t.rules.isNotEmpty()) FirstMatch.decided(t.rules, row.port)
                            else row.use,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (isOpen) colors.onSecondaryContainer.copy(alpha = .8f)
                                else colors.onSurfaceVariant,
                        )
                    if (wrong)
                        Text(
                            if (t.expected[i]) "Debía quedar abierto" else "Debía quedar cerrado",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.error,
                        )
                }
                Switch(checked = isOpen, onCheckedChange = null, enabled = answering)
            }
        }
        if (feedback) {
            val ok = good == t.rows.size
            Text(
                if (ok) "Reglas correctas · ${t.rows.size} de ${t.rows.size} puertos bien"
                else "$good de ${t.rows.size} puertos bien",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (ok) colors.secondary else colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(t.why, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            { submit(open.indices.filter { open[it] }) },
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = answering,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Aplicar reglas", fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- Bits (Lote B)

/**
 * Bits: one switch per position with its place value. Tap to turn a bit on or off; the sum updates
 * live (hidden at level 8+). [submit] receives the value the switches add up to.
 */
@Composable
fun BitsBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    picked: List<Int>,
    submit: (Int) -> Unit,
) {
    val t = c.bits ?: return
    val colors = MaterialTheme.colorScheme
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    var value by remember(c) { mutableIntStateOf(t.start) }
    val shown = if (feedback) (picked.firstOrNull() ?: value) else value
    val on = t.weights.filter { shown and it != 0 }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            t.weights.forEachIndexed { i, w ->
                val bit = t.bit(shown, i)
                val wrong = feedback && bit != t.bit(t.target, i)
                val fill by
                    animateColorAsState(
                        if (bit == 1) NetAmber else colors.surfaceVariant,
                        if (motion) tween(150) else snap(),
                        label = "Bit",
                    )
                val lift by
                    animateFloatAsState(
                        if (bit == 1) 1f else 0f,
                        if (motion) spring(dampingRatio = .5f, stiffness = 500f) else snap(),
                        label = "Interruptor",
                    )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "$w",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(64.dp)
                            .graphicsLayer {
                                val k = 1f + .06f * lift
                                scaleX = k
                                scaleY = k
                                translationY = -4.dp.toPx() * lift
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(fill)
                            .border(
                                if (wrong) 2.dp else 1.dp,
                                if (wrong) colors.error else colors.outlineVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .toggleable(value = bit == 1, enabled = answering, role = Role.Switch) {
                                value = value xor w
                                cue(FeedbackCue.TAP)
                            }
                            .semantics {
                                contentDescription = "Bit de valor $w"
                                stateDescription = if (bit == 1) "encendido" else "apagado"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$bit",
                            color = if (bit == 1) NetInk else colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        Panel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (t.hints || feedback) "$shown" else "¿?",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    if (on.isEmpty()) "Todos apagados"
                    else if (t.hints || feedback) on.joinToString(" + ")
                    else "${on.size} ${if(on.size==1)"bit encendido" else "bits encendidos"}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariant,
                )
            }
            Readout("Binario", shown.toString(2).padStart(t.width, '0'))
            if (feedback && shown != t.target)
                Text(
                    "Correcto: ${t.target.toString(2).padStart(t.width,'0')} = ${t.target}",
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
        }
        Button(
            { submit(value) },
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = answering,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Comprobar", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Relámpago and the 3-minute practice (Lote B): the pilot games keep their board, the rest the
 * answer cards. [answer] gets what [Challenge.accepts] checks.
 */
@Composable
fun QuickChallenge(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    picked: List<Int>,
    answer: (List<Int>) -> Unit,
) {
    when (c.kind) {
        Kind.SUBNET -> SubnetBoard(c, answering, feedback, picked) { answer(listOf(it)) }
        Kind.INBOX -> InboxBoard(c, answering, feedback, picked) { answer(listOf(it)) }
        Kind.FIREWALL -> FirewallBoard(c, answering, feedback) { answer(it) }
        Kind.BITS -> BitsBoard(c, answering, feedback, picked) { answer(listOf(it)) }
        else -> ChoiceCards(c, answering, feedback, picked) { answer(listOf(it)) }
    }
}
