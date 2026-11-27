package es.foco.next.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.care.CareMode
import es.foco.next.core.care.Mood

/*
 * De control a cuidado (launcher). Principios: el usuario elige y Foco acompaña; nada de castigo,
 * rojo ni contadores de segundos; lo bueno primero; si el usuario está mal, Foco baja la presión.
 * Colores: solo los de la paleta del inicio (acento, tarjeta, texto apagado).
 */

/** Píldora con el modo actual. Tocar = elegir modo. */
@Composable
fun ModeChip(mode: CareMode.Modo, focusLeft: Long, onClick: () -> Unit, careLeft: Long = 0) {
    val c = LocalHome.current
    val text =
        when (mode) {
            CareMode.Modo.ENFOQUE -> "Enfoque · quedan ${minutes(focusLeft)}"
            CareMode.Modo.CUIDADO -> if (careLeft > 0) "Cuidado · quedan ${minutes(careLeft)}" else "Modo Cuidado · vamos suave"
            else -> "Modo Normal"
        }
    Row(
        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(999.dp))
            .background(lerp(c.card, c.accent, if (mode == CareMode.Modo.ENFOQUE) .18f else .08f))
            .border(1.dp, lerp(c.card, c.accent, .22f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "$text. Toca para cambiar de modo." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        HomeText("  $text", 11.5.sp, c.text, FontWeight.SemiBold)
        HomeText("  ›", 11.5.sp, c.muted)
    }
}

/** Minutos redondeados, sin segundos: "45 min", "1 h 20 min". */
fun minutes(m: Long): String = if (m >= 60) "${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}" else "$m min"

/** Hoja para elegir modo. NORMAL no está disponible durante un ENFOQUE; CUIDADO, siempre. */
@Composable
fun ModeSheet(
    current: CareMode.Modo,
    focusLeft: Long,
    untilNightAvailable: Boolean,
    nightStart: Int,
    onCare: (Int) -> Unit,
    onNormal: () -> Unit,
    onFocus: (CareMode.Duracion) -> Unit,
    onDismiss: () -> Unit,
    careBudgetMin: Long = Long.MAX_VALUE,
    careLeft: Long = 0,
) {
    val c = LocalHome.current
    var focusOpen by remember { mutableStateOf(false) }
    var careOpen by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        HomeText("¿Cómo quieres el día?", 17.sp, weight = FontWeight.Bold)
        HomeText("Tú eliges. La noche y Telegram siguen igual en todos los modos.", 12.sp, c.muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        ModeOption(CareMode.Modo.CUIDADO, current, CareMode.description(CareMode.Modo.CUIDADO), true) { careOpen = !careOpen; focusOpen = false }
        if (careOpen) {
            // Cuidado con tiempo: tú eliges cuánto; al acabar vuelves a tu modo de antes.
            HomeText(
                when {
                    careBudgetMin <= 0 -> "Hoy ya usaste tus 2 h de Cuidado. Si es un día difícil, cuéntamelo en ¿Cómo estás? y se abre."
                    careBudgetMin == Long.MAX_VALUE -> if (current == CareMode.Modo.CUIDADO) "Ampliar desde ahora (quedaban ${minutes(careLeft)}):" else "¿Cuánto rato? Hoy, sin tope: lo necesitas."
                    else -> "¿Cuánto rato? Te quedan ${minutes(careBudgetMin)} de Cuidado hoy."
                },
                12.sp, c.muted, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
            if (careBudgetMin > 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in CareMode.CARE_CHOICES_MIN)
                        FocusChoice(if (m < 60) "$m min" else "${m / 60} h", true, Modifier.weight(1f)) { onCare(m) }
                }
        }
        val normalOk = current != CareMode.Modo.ENFOQUE
        ModeOption(
            CareMode.Modo.NORMAL, current,
            if (normalOk) CareMode.description(CareMode.Modo.NORMAL)
            else "Disponible cuando termine tu Enfoque (en ${minutes(focusLeft)}). A Cuidado sí puedes pasar ya.",
            normalOk, onNormal,
        )
        ModeOption(CareMode.Modo.ENFOQUE, current, CareMode.description(CareMode.Modo.ENFOQUE), true) { focusOpen = !focusOpen; careOpen = false }
        if (focusOpen) {
            HomeText(
                if (current == CareMode.Modo.ENFOQUE) "Ampliar el Enfoque (gana el final más tardío):" else "¿Cuánto rato? No se puede terminar antes; a Cuidado sí puedes pasar.",
                12.sp, c.muted, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChoice("1 h", true, Modifier.weight(1f)) { onFocus(CareMode.Duracion.UNA_HORA) }
                FocusChoice("2 h", true, Modifier.weight(1f)) { onFocus(CareMode.Duracion.DOS_HORAS) }
                FocusChoice("Hasta las $nightStart:00", untilNightAvailable, Modifier.weight(1.4f)) { onFocus(CareMode.Duracion.HASTA_LA_NOCHE) }
            }
        }
    }
}

@Composable
private fun ModeOption(m: CareMode.Modo, current: CareMode.Modo, detail: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    val selected = m == current
    Column(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) lerp(c.card, c.accent, .12f) else lerp(c.card, c.muted, .05f))
            .border(1.dp, lerp(c.card, c.accent, if (selected) .35f else .1f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        HomeText(CareMode.label(m) + if (selected) " · ahora" else "", 14.5.sp, if (enabled) c.text else c.muted, FontWeight.SemiBold)
        HomeText(detail, 12.sp, c.muted)
    }
}

@Composable
private fun FocusChoice(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalHome.current
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent else lerp(c.card, c.muted, .12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) c.card else c.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/**
 * "¿Cómo estás?" del 1 al 5 y una palabra opcional. Solo en el teléfono. [onAnswer] devuelve si
 * conviene ofrecer CUIDADO (1 o 2): entonces se muestra un botón, nunca se cambia solo.
 */
@Composable
fun MoodCard(
    title: String,
    skipLabel: String,
    showCareOffer: Boolean,
    onAnswer: (Int, String) -> Boolean,
    onCare: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    var score by remember { mutableIntStateOf(0) }
    var word by remember { mutableStateOf("") }
    var offer by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    HomeCard {
        Column {
            HomeText(title, 15.sp, weight = FontWeight.SemiBold)
            if (!done) {
                HomeText("Del 1 al 5. Queda solo en tu teléfono.", 11.5.sp, c.muted)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (n in Mood.MIN..Mood.MAX) {
                        val sel = n == score
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (sel) lerp(c.card, c.accent, .18f) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    score = n
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Ánimo $n de 5" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(Mood.face(n), fontSize = 24.sp)
                            HomeText("$n", 11.sp, c.muted)
                        }
                    }
                }
                Box(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(lerp(c.card, c.muted, .07f)).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (word.isEmpty()) HomeText("Una palabra, si quieres (opcional)", 12.5.sp, c.muted)
                    BasicTextField(
                        word, { word = Mood.word(it) }, singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomeText(skipLabel, 12.5.sp, c.muted, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSkip).padding(8.dp))
                    PrimaryButton(if (score == 0) "Elige un número" else "Guardar", Modifier.padding(start = 8.dp).weight(1f)) {
                        if (score == 0) return@PrimaryButton
                        offer = onAnswer(score, word) && showCareOffer
                        done = true
                    }
                }
            } else {
                HomeText(Mood.reply(score), 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (offer) {
                    HomeText("¿Pasamos a modo Cuidado? Es tu decisión.", 12.5.sp, c.muted, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameButton("Sí, modo Cuidado", Act.CARE, Modifier.weight(1f)) { onCare(); offer = false }
                        HomeText("Ahora no", 12.5.sp, c.muted, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(10.dp)).clickable { offer = false; onSkip() }.padding(10.dp))
                    }
                } else {
                    HomeText("Cerrar", 12.5.sp, c.accent, FontWeight.SemiBold, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSkip).padding(8.dp))
                }
            }
        }
    }
}

/** Hoja inferior genérica (mismo estilo que la Aduana). */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalHome.current
    // v31: con el teclado abierto la hoja sube encima de él (antes lo tapaba: no se veía lo que
    // escribías en el monto antes de entrar al banco).
    Box(
        Modifier.fillMaxSize().background(Color(0xFF1C2920).copy(alpha = .55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(26.dp)).background(c.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) { content() }
    }
}

// ===================== Los cuatro cuidados del búho (registro del 21/11) =====================

/**
 * Inquietud: "Respirar 30 s" con el búho. Un círculo que respira (4 s dentro, 4 s fuera), sin
 * contador de segundos. Al acabar, un gracias; nada más.
 */
@Composable
fun BreatheSheet(onDone: () -> Unit) {
    val c = LocalHome.current
    val motion = LocalMotion.current
    var finished by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(30_000)
        finished = true
    }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "respirar")
    val breath = t.animateFloat(
        .7f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "respirar",
    )
    Sheet(onDone) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HomeText(if (finished) "Gracias por parar un momento" else "Respira conmigo", 17.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                // El valor se lee en el dibujo: solo se redibuja este círculo.
                androidx.compose.foundation.Canvas(Modifier.size(150.dp)) {
                    val k = if (motion && !finished) breath.value else .85f
                    drawCircle(lerp(c.card, c.accent, .14f), size.minDimension / 2 * k)
                    drawCircle(lerp(c.card, c.accent, .3f), size.minDimension / 2 * k * .66f)
                }
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            HomeText(
                if (finished) "Ahora decides tú, con más calma." else "4 tiempos dentro, 4 tiempos fuera. Sin prisa.",
                13.sp, c.muted,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (finished) "Cerrar" else "Ya estoy mejor", onClick = onDone)
        }
    }
}

/** Aviso del búho en el inicio (Foco apagado o el hueco que estuvo apagado). */
@Composable
fun OwlNotice(text: String, action: String, onAction: () -> Unit, onClose: (() -> Unit)? = null) {
    val c = LocalHome.current
    HomeCard {
        Row(verticalAlignment = Alignment.Top) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(44.dp),
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                HomeText(text, 13.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onClose != null)
                        HomeText("Cerrar", 12.5.sp, c.muted, FontWeight.SemiBold, Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(8.dp))
                    PrimaryButton(action, Modifier.weight(1f), onAction)
                }
            }
        }
    }
}


// ===================== Rutinas obligatorias =====================

/** Lista para marcar: cada tarea con sus minutos; abajo, lo que queda. */
@Composable
fun RoutineSheet(
    kind: es.foco.next.core.care.Routine.Kind,
    tasks: List<es.foco.next.core.care.Routine.Task>,
    done: Set<Int>,
    onCheck: (Int, Boolean) -> Unit,
    money: String? = null,
    onClose: () -> Unit,
) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    Sheet(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(56.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                HomeText(if (kind == es.foco.next.core.care.Routine.Kind.MORNING) "Buenos días" else "Cerrar bien el día", 19.sp, weight = FontWeight.Bold)
                HomeText("Rutina de la ${kind.word} · ~${es.foco.next.core.care.Routine.totalMinutes(tasks)} min", 12.5.sp, c.muted)
            }
        }
        // Al despertar, también el dinero de hoy.
        if (money != null) HomeText("💵 " + money, 12.5.sp, c.text, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        tasks.forEachIndexed { i, t ->
            val on = i in done
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) lerp(c.card, c.accent, .16f) else lerp(c.card, c.muted, .06f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheck(i, !on)
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeText(t.label, 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                HomeText(if (on) "✓" else "${t.minutes} min", if (on) 18.sp else 12.sp, if (on) c.accent else c.muted, FontWeight.Bold)
            }
        }
        val left = es.foco.next.core.care.Routine.remainingCount(tasks, done)
        Spacer(Modifier.height(10.dp))
        HomeText(
            if (left == 0) "Hecho. El ocio se abre cuando pase el tiempo mínimo de la rutina."
            else "Quedan ~${es.foco.next.core.care.Routine.remainingMinutes(tasks, done)} min. El ocio espera.",
            12.5.sp, c.muted,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(if (left == 0) "Listo" else "Voy a ello", onClick = onClose)
    }
}

/** Domingo: invitación a planificar la semana. */
@Composable
fun PlanCard(onPlan: () -> Unit) {
    val c = LocalHome.current
    HomeCard(Modifier.clickable(onClick = onPlan)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(40.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                HomeText("Planifica tu semana", 15.sp, weight = FontWeight.Bold)
                HomeText("Qué toca cada día y cuánto te lleva.", 12.5.sp, c.muted)
            }
            HomeText("›", 20.sp, c.muted)
        }
    }
}

/**
 * Antes de entrar al banco: cuánto vas a sacar o pagar y para qué. Se puede saltar; no es para
 * mortificarse, es para que el búho te diga cuánto queda hoy.
 */
@Composable
fun BankSheet(
    bank: String,
    owl: String?,
    onSave: (Long, String) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalHome.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cents = es.foco.next.core.care.Money.parse(amount)
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(48.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                HomeText("Antes de entrar a $bank", 17.sp, weight = FontWeight.Bold)
                HomeText(owl ?: "¿Cuánto vas a sacar o pagar?", 12.5.sp, c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        MoneyField(amount, "Monto, p. ej. 12.50", true) { amount = it }
        Spacer(Modifier.height(8.dp))
        MoneyField(note, "Para qué (opcional)", false) { note = it }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (cents > 0) "Anotar " + es.foco.next.core.care.Money.fmt(cents) + " y abrir" else "Escribe el monto") {
            if (cents > 0) onSave(cents, note)
        }
        HomeText(
            "Solo abrir", 13.sp, c.muted, FontWeight.SemiBold,
            Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onOpen).padding(8.dp),
        )
    }
}

@Composable
private fun MoneyField(value: String, hint: String, number: Boolean, onChange: (String) -> Unit) {
    val c = LocalHome.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(lerp(c.card, c.muted, .08f)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) HomeText(hint, 14.sp, c.muted)
        androidx.compose.foundation.text.BasicTextField(
            value, onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
