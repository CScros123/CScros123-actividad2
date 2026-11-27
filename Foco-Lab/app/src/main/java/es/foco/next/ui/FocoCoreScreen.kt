@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package es.foco.next.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.care.FocoCore
import es.foco.next.core.home.HomeSections
import es.foco.next.core.care.Money
import es.foco.next.core.care.Routine
import es.foco.next.runtime.MoneyStore
import es.foco.next.runtime.RoutineStore
import java.time.LocalDate
import java.time.LocalTime


/** Rojo suave (terracota) para el dinero por encima del presupuesto: se nota sin alarmar. */
val Terracotta = Color(0xFFC0634A)

private val BLOCK_TITLES = mapOf(RoutineStore.MORNING to "Mañana", RoutineStore.NIGHT to "Tarde y noche", RoutineStore.EVENT to "Solo este día")

/** Lo que muestra el widget de Inicio (se recalcula con [HomeViewModel.refreshCore]). */
data class CoreSummary(
    val state: FocoCore.State,
    val total: Int,
    val done: Int,
    val next: String?,
    val nextWindow: String,
    val finish: String,
    val penaltyMin: Long,
    val penaltyTask: String,
    val freeLeftMin: Long,
    val freeUsed: Boolean,
    val money: String?,
    val moneyOver: Boolean,
)

fun coreSummary(ctx: android.content.Context): CoreSummary {
    val d = RoutineStore.today()
    val t = RoutineStore.totals(ctx, d)
    val now = System.currentTimeMillis()
    var next: String? = null
    var window = ""
    var best = Int.MAX_VALUE
    for (b in RoutineStore.BLOCKS) {
        val tasks = RoutineStore.blockTasks(ctx, b, d)
        val i = FocoCore.next(tasks, RoutineStore.blockDone(ctx, b, d))
        if (i < 0) continue
        val w = tasks[i].window
        // La que vence antes; sin hora, la primera del primer bloque con pendientes.
        val key = if (w >= 0) w else 10_000 + RoutineStore.BLOCKS.indexOf(b)
        if (key < best) {
            best = key
            next = tasks[i].label
            window = tasks[i].windowText()
        }
    }
    val left = RoutineStore.remainingMinutes(ctx, d)
    val budget = MoneyStore.budget(ctx)
    val entries = MoneyStore.entries(ctx)
    val today = RoutineStore.today() // el dinero también cambia de día a las 4:00
    val money: String?
    val over: Boolean
    if (budget > 0) {
        val l = Money.leftToday(budget, entries, today)
        over = l < 0
        money = if (over) "${Money.fmt(-l)} por encima hoy" else "${Money.fmt(l)} hoy"
    } else {
        val s = Money.spentToday(entries, today)
        over = false
        money = if (s > 0) "Hoy llevas ${Money.fmt(s)}" else null
    }
    val penalty = RoutineStore.activePenaltyUntil(ctx) - now
    val free = RoutineStore.freeUntil(ctx) - now
    return CoreSummary(
        RoutineStore.state(ctx), t[0], t[1], next, window,
        FocoCore.hm(FocoCore.projectedFinish(LocalTime.now(), left)),
        if (penalty > 0) (penalty + 59_999) / 60_000 else 0,
        ctx.getSharedPreferences("foco-rutina", 0).getString("castigoTarea", "") ?: "",
        if (free > 0) (free + 59_999) / 60_000 else 0,
        RoutineStore.freeUsedToday(ctx),
        money, over,
    )
}

/** Widget de Foco Core en Inicio: 4 estados, un toque abre la pantalla completa. */
@Composable
fun CoreWidget(s: CoreSummary, onOpen: () -> Unit, onFree: () -> Unit) {
    val c = LocalHome.current
    HomeCard(Modifier.clickable(onClick = onOpen)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    when (s.state) {
                        FocoCore.State.NO_PLAN -> {
                            HomeText("Anota tus pendientes de hoy", 15.sp, weight = FontWeight.Bold)
                            HomeText("Con 2 tareas o más, Foco Core se pone en marcha.", 12.5.sp, c.muted)
                        }
                        FocoCore.State.RUNNING -> {
                            HomeText("Siguiente: ${s.next ?: "—"}" + if (s.nextWindow.isNotEmpty()) " · antes de ${s.nextWindow}" else "", 15.sp, weight = FontWeight.Bold)
                            HomeText("${s.done}/${s.total} hechas · terminas ~${s.finish}", 12.5.sp, c.muted)
                        }
                        FocoCore.State.PENALTY -> {
                            HomeText("Ocio en pausa · ${s.penaltyMin} min", 15.sp, Terracotta, FontWeight.Bold)
                            HomeText("Se pasó la hora de ${s.penaltyTask}. Terminarla sigue contando.", 12.5.sp, c.muted)
                        }
                        FocoCore.State.COMPLETE -> {
                            HomeText("Todo hecho hoy ✓", 15.sp, weight = FontWeight.Bold)
                            HomeText(
                                when {
                                    s.freeLeftMin > 0 -> "Ocio libre · quedan ${s.freeLeftMin} min"
                                    s.freeUsed -> "Hora libre usada. Buen día."
                                    else -> "Te ganaste 1 h de ocio libre."
                                },
                                12.5.sp, c.muted,
                            )
                        }
                    }
                    if (s.money != null) HomeText("💵 " + s.money, 12.5.sp, if (s.moneyOver) Terracotta else c.text, if (s.moneyOver) FontWeight.SemiBold else FontWeight.Normal)
                }
                HomeText("›", 20.sp, c.muted)
            }
            if (s.state == FocoCore.State.COMPLETE && !s.freeUsed && s.freeLeftMin <= 0) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Activar 1 h de ocio libre", onClick = onFree)
            }
        }
    }
}

// =====================================================================================
// v30: Foco Core rediseñado. Colores adaptativos: todo sale de la paleta del tema del
// inicio (LocalHome), así que de día es claro y cálido y de noche azul y oscuro, y cambia
// solo cuando cambia el tema. Verde, ámbar y terracota se mezclan con el acento del tema.
// =====================================================================================

/** Colores de estado derivados del tema del inicio. */
@androidx.compose.runtime.Immutable
data class CoreColors(val ok: Color, val warn: Color, val bad: Color, val track: Color, val sheet: Color, val onAccent: Color)

@Composable
fun coreColors(): CoreColors {
    val c = LocalHome.current
    val dark = c.darkCard
    val anim = tween<Color>(700)
    val ok by animateColorAsState(lerp(if (dark) Color(0xFF6FCF97) else Color(0xFF2F8F5B), c.accent, .18f), anim, label = "ok")
    val warn by animateColorAsState(lerp(if (dark) Color(0xFFE8B45A) else Color(0xFFB7791F), c.accent, .10f), anim, label = "warn")
    val bad by animateColorAsState(if (dark) Color(0xFFE07A5F) else Terracotta, anim, label = "bad")
    val sheet by animateColorAsState(c.raised(.5f), anim, label = "sheet")
    val lum = .2126f * c.accent.red + .7152f * c.accent.green + .0722f * c.accent.blue
    return CoreColors(ok, warn, bad, c.text.copy(alpha = .12f), sheet, if (lum > .55f) Color(0xFF0B1624) else Color.White)
}

/** Lo que se edita en la hoja: nueva tarea o una existente (clave + posición, o hoy + bloque). */
private data class Editing(val task: Routine.Task?, val key: String? = null, val block: String, val index: Int = -1, val today: Boolean = false)

/** v36: la tarea que se está marcando (hoja «¿Cómo lo hiciste?»). */
private data class Doing(val block: String, val index: Int, val task: Routine.Task)

@Composable
fun FocoCoreScreen(vm: HomeViewModel, a: HomeActions, startTab: Int, onClose: () -> Unit) {
    val c = LocalHome.current
    val k = coreColors()
    var tab by remember { mutableIntStateOf(startTab.coerceIn(0, 2)) }
    var clock by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var money by remember { mutableStateOf(false) }
    var coupon by remember { mutableStateOf<Boolean?>(null) } // null: cerrada; true: cupón de semana
    var doing by remember { mutableStateOf<Doing?>(null) }
    val motion = !(vm.reduceMotion || vm.saving)
    // Cronómetros y cuenta atrás: se refresca cada 30 s mientras está abierta.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            clock++
            vm.refreshCore()
        }
    }
    val tick = vm.coreTick + clock
    Box(Modifier.fillMaxSize().background(c.background)) {
        vm.photo?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(c.background.copy(alpha = .62f)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeText("‹", 26.sp, c.text, FontWeight.Light, Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 2.dp))
                HomeText("Foco Core", 22.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            CoreTabs(tab, k) { tab = it }
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (tab) {
                        0 -> TodayTab(vm, a, tick, k, motion, onCoupon = { coupon = it }, onDo = { doing = it }) { editing = it }
                        1 -> WeekTab(tick, k, onMoney = { money = true }) { editing = it }
                        else -> ReportsTab(tick, k, motion)
                    }
                }
                if (tab != 2)
                    GameButton("Tarea", Act.GO, Modifier.align(Alignment.BottomEnd).padding(18.dp), icon = Icons.Rounded.Add, height = 54.dp) {
                        editing = Editing(null, block = RoutineStore.MORNING)
                    }
            }
        }
        editing?.let { e ->
            TaskSheet(e, k, onDismiss = { editing = null }) {
                editing = null
                vm.refreshRoutine()
                vm.refreshCore()
                clock++
            }
        }
        doing?.let { d ->
            DoneSheet(
                d, k,
                onDismiss = { doing = null },
                onPhoto = {
                    doing = null
                    a.photoTask(d.block, d.index) // al volver de la cámara queda hecha con su foto
                    clock++
                },
                onDone = {
                    doing = null
                    vm.refreshRoutine()
                    vm.refreshCore()
                    clock++
                },
            )
        }
        coupon?.let { week ->
            CouponSheet(vm, week, k, onDismiss = { coupon = null }) {
                coupon = null
                clock++
            }
        }
        if (money) MoneySheet(k, onDismiss = { money = false }) {
            money = false
            vm.refreshMoney()
            vm.refreshCore()
            clock++
        }
    }
}

@Composable
private fun CoreTabs(tab: Int, k: CoreColors, onTab: (Int) -> Unit) {
    val c = LocalHome.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(18.dp)).background(c.text.copy(alpha = .06f)).padding(4.dp)) {
        listOf("Hoy", "Semana", "Reportes").forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == tab) Act.GO.face else Color.Transparent, tween(250), label = "tab")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onTab(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { HomeText(label, 13.5.sp, if (i == tab) Act.GO.ink else c.muted, FontWeight.Bold) }
        }
    }
}

// ----------------------------------- Hoy -----------------------------------

@Composable
private fun TodayTab(
    vm: HomeViewModel,
    a: HomeActions,
    tick: Int,
    k: CoreColors,
    motion: Boolean,
    onCoupon: (Boolean) -> Unit,
    onDo: (Doing) -> Unit,
    onEdit: (Editing) -> Unit,
) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val d = RoutineStore.today()
    val s = vm.core ?: return
    val left = remember(tick) { RoutineStore.remainingMinutes(ctx, d) }
    // v39: HUD del día (modo juego): hechas, barra, racha y cupones, con textos cortos.
    val streak = remember(tick) { es.foco.next.runtime.RewardStore.streak(ctx) }
    val couponCount = remember(tick) { es.foco.next.runtime.RewardStore.coupons(ctx).size }
    CoreCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                GameText("${s.done}/${s.total}", 32.sp, c.text, FontWeight.Black)
                GameText(if (s.total > 0 && s.done == s.total) "¡todo!" else "hechas", 13.sp, c.muted, FontWeight.Bold, Modifier.padding(start = 8.dp, bottom = 5.dp))
            }
            GameStat(Icons.Rounded.LocalFireDepartment, "$streak", Act.TIME.face)
            Spacer(Modifier.width(8.dp))
            GameStat(Icons.Rounded.ConfirmationNumber, "$couponCount", Act.PRIZE.face)
        }
        Spacer(Modifier.height(12.dp))
        GameBar(if (s.total == 0) 0f else s.done / s.total.toFloat(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (pillText, pillColor) = when (s.state) {
                FocoCore.State.NO_PLAN -> "Anota 2 tareas" to Act.TIME.face
                FocoCore.State.PENALTY -> "Ocio en pausa · ${s.penaltyMin}′" to Act.STOP.face
                FocoCore.State.COMPLETE ->
                    (if (s.freeLeftMin > 0) "Ocio libre · ${s.freeLeftMin}′" else if (s.freeUsed) "Hora libre usada" else "1 h libre ganada") to Act.GO.face
                else -> "Hora libre a ${s.total - s.done}" to Act.GO.face
            }
            GameChip(pillText, pillColor)
            Spacer(Modifier.weight(1f))
            if (left > 0) GameText("~$left′ · fin ${s.finish}", 12.5.sp, c.muted, FontWeight.Medium)
        }
        if (s.money != null)
            GameText("Dinero: " + s.money, 12.5.sp, if (s.moneyOver) Act.STOP.face else c.muted, FontWeight.Medium, Modifier.padding(top = 8.dp))
        if (s.state == FocoCore.State.COMPLETE && !s.freeUsed && s.freeLeftMin <= 0) {
            Spacer(Modifier.height(12.dp))
            GameButton("Activar 1 h libre", Act.GO, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, onClick = a::startFreeHour)
        }
    }
    CouponsCard(tick, k, onCoupon)
    // Lo siguiente: una sola tarea, con sus tres acciones.
    val next = remember(tick) { nextTask(ctx, d) }
    if (next != null) {
        val (b, i, t) = next
        val running = remember(tick) { RoutineStore.running(ctx, b, i) }
        val ms = remember(tick) { RoutineStore.timerMs(ctx, b, i) }
        Column(
            Modifier.fillMaxWidth().clip(CardShape)
                .background(Brush.linearGradient(listOf(c.accent.copy(alpha = .28f), c.card.copy(alpha = .9f))))
                .border(1.dp, c.accent.copy(alpha = .4f), CardShape).padding(16.dp),
        ) {
            HomeText("SIGUIENTE", 11.sp, Act.GO.face, FontWeight.Bold)
            HomeText(t.label, 19.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            HomeText(meta(t), 12.5.sp, c.muted)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(
                    if (running) "${ms / 60_000}′" else "Empezar",
                    if (running) Act.TIME else Act.GO,
                    Modifier.weight(1f),
                    icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                ) { a.toggleTaskTimer(b, i) }
                GameButton(null, Act.DONE, Modifier.width(52.dp), icon = Icons.Rounded.Check) { onDo(Doing(b, i, t)) }
                GameButton(null, Act.PHOTO, Modifier.width(52.dp), icon = Icons.Rounded.PhotoCamera) { a.photoTask(b, i) }
            }
        }
    }
    // Las franjas: pendientes primero; las hechas se pliegan.
    for (b in RoutineStore.BLOCKS) {
        val tasks = remember(tick, b) { RoutineStore.blockTasks(ctx, b, d) }
        if (tasks.isEmpty() && b == RoutineStore.EVENT) continue
        val done = remember(tick, b) { RoutineStore.blockDone(ctx, b, d) }
        val missed = remember(tick, b) { RoutineStore.blockMissed(ctx, b, d) }
        val doneCount = tasks.indices.count { it in done }
        val pending = Routine.remainingMinutes(tasks, done)
        // v36: hora límite de la franja (tócala para cambiarla); 30 min antes, aviso cada 5 min.
        var limit by remember(tick, b) { mutableIntStateOf(RoutineStore.blockDeadline(ctx, b)) }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeText("${(BLOCK_TITLES[b] ?: b).uppercase()} · $doneCount/${tasks.size}", 11.sp, c.muted, FontWeight.Bold, Modifier.weight(1f))
            if (b != RoutineStore.EVENT)
                GameChip(
                    if (limit >= 0) hm(limit) else "sin hora",
                    if (limit >= 0) Act.TIME.face else c.muted,
                    Modifier.padding(end = 8.dp).clip(GameSmall).clickable {
                        pickDeadline(ctx, limit) { m ->
                            RoutineStore.setBlockDeadline(ctx, b, m)
                            limit = RoutineStore.blockDeadline(ctx, b)
                        }
                    },
                    icon = Icons.Rounded.Schedule,
                )
            HomeText(if (tasks.isNotEmpty() && doneCount == tasks.size) "✓ completa" else if (pending > 0) "~$pending′" else "", 11.sp, if (doneCount == tasks.size && tasks.isNotEmpty()) k.ok else c.muted, FontWeight.Bold)
        }
        var showDone by remember(b) { mutableStateOf(false) }
        CoreCard(Modifier.animateContentSize(), padV = 6.dp) {
            if (tasks.isEmpty())
                HomeText("Sin tareas. Toca ＋ Nueva tarea.", 12.5.sp, c.muted, modifier = Modifier.padding(vertical = 10.dp))
            tasks.forEachIndexed { i, t ->
                if (i !in done)
                    TaskRow(t, false, i in missed, RoutineStore.hasPhoto(ctx, b, i), k,
                        onCheck = { onDo(Doing(b, i, t)) },
                        onLong = { markEditHint(ctx); onEdit(Editing(t, block = b, index = i, today = true)) })
            }
            if (doneCount > 0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showDone = !showDone }.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { HomeText(if (showDone) "Ocultar hechas" else "+$doneCount ${if (doneCount == 1) "hecha" else "hechas"}", 12.sp, c.muted, FontWeight.SemiBold) }
                AnimatedVisibility(showDone, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        tasks.forEachIndexed { i, t ->
                            if (i in done)
                                TaskRow(t, true, i in missed, RoutineStore.hasPhoto(ctx, b, i), k,
                                    onCheck = { a.uncheckTask(b, i) },
                                    onLong = { markEditHint(ctx); onEdit(Editing(t, block = b, index = i, today = true)) })
                        }
                    }
                }
            }
        }
    }
    // v39: el consejo sale solo hasta que editas una tarea por primera vez.
    if (!editHintSeen(ctx)) HomeText("Mantén pulsada una tarea para editarla.", 11.5.sp, c.muted, modifier = Modifier.padding(start = 4.dp))
}

private fun editHintSeen(ctx: android.content.Context): Boolean =
    ctx.getSharedPreferences("foco-home", 0).getBoolean("consejo_editar_visto", false)

private fun markEditHint(ctx: android.content.Context) {
    ctx.getSharedPreferences("foco-home", 0).edit().putBoolean("consejo_editar_visto", true).apply()
}

private fun hm(minute: Int): String = "${minute / 60}:${if (minute % 60 < 10) "0" else ""}${minute % 60}"

/** Reloj del sistema para la hora límite de una franja; «Sin hora» la quita. */
private fun pickDeadline(ctx: android.content.Context, current: Int, onPick: (Int) -> Unit) {
    val base = if (current >= 0) current else 9 * 60
    val dlg = android.app.TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, base / 60, base % 60, true)
    dlg.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Sin hora") { _, _ -> onPick(-1) }
    dlg.show()
}

/**
 * v36: al tocar una tarea, «¿Cómo lo hiciste?» y la foto, los dos opcionales: van al informe del
 * día. Propone la nota de la última vez (aunque sea lo mismo, un toque).
 */
@Composable
private fun DoneSheet(d: Doing, k: CoreColors, onDismiss: () -> Unit, onPhoto: () -> Unit, onDone: () -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val last = remember(d) { RoutineStore.lastNote(ctx, d.task.label) }
    var note by remember(d) { mutableStateOf(RoutineStore.note(ctx, d.block, d.index, RoutineStore.today())) }
    CoreSheet(k, onDismiss) {
        HomeText(d.task.label, 19.sp, weight = FontWeight.Bold)
        HomeText(meta(d.task), 12.5.sp, c.muted)
        Label("¿CÓMO? (OPCIONAL)")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.text.copy(alpha = .06f))
                .border(1.5.dp, c.accent.copy(alpha = .6f), RoundedCornerShape(16.dp)).padding(14.dp),
        ) {
            if (note.isEmpty()) HomeText("Ej.: qué hiciste, cuánto tardaste, qué te costó", 14.sp, c.muted)
            BasicTextField(
                note, { if (it.length <= RoutineStore.NOTE_MAX) note = it },
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            )
        }
        if (last.isNotEmpty() && note.isEmpty())
            Box(
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(999.dp)).background(c.text.copy(alpha = .07f))
                    .clickable { note = last }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) { HomeText("↺ Igual que la última vez: ${last.take(40)}${if (last.length > 40) "…" else ""}", 12.sp, c.text) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton("Foto", Act.PHOTO, Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera) {
                RoutineStore.saveNote(ctx, d.block, d.index, d.task.label, note)
                onPhoto()
            }
            GameButton("Hecha", Act.DONE, Modifier.weight(1f), icon = Icons.Rounded.Check) {
                RoutineStore.saveNote(ctx, d.block, d.index, d.task.label, note)
                RoutineStore.checkBlock(ctx, d.block, d.index, true)
                onDone()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** La siguiente tarea de hoy: la que vence antes; sin hora, la primera pendiente. */
private fun nextTask(ctx: android.content.Context, d: LocalDate): Triple<String, Int, Routine.Task>? {
    var best: Triple<String, Int, Routine.Task>? = null
    var bestKey = Int.MAX_VALUE
    for (b in RoutineStore.BLOCKS) {
        val tasks = RoutineStore.blockTasks(ctx, b, d)
        val i = FocoCore.next(tasks, RoutineStore.blockDone(ctx, b, d))
        if (i < 0) continue
        val key = if (tasks[i].window >= 0) tasks[i].window else 10_000 + RoutineStore.BLOCKS.indexOf(b)
        if (key < bestKey) {
            bestKey = key
            best = Triple(b, i, tasks[i])
        }
    }
    return best
}

private fun meta(t: Routine.Task): String =
    listOfNotNull(
        "${t.minutes} min",
        if (t.window >= 0) "antes de las ${t.windowText()}" else null,
        if (t.difficulty > 0) Routine.DIFFICULTY[t.difficulty].lowercase() else null,
        if (t.photo) "con foto" else null,
    ).joinToString(" · ")

@Composable
private fun TaskRow(t: Routine.Task, done: Boolean, missed: Boolean, hasPhoto: Boolean, k: CoreColors, onCheck: () -> Unit, onLong: () -> Unit) {
    val c = LocalHome.current
    // v39: cada tarea es una ficha con casilla cuadrada que salta al marcarla.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(GameMid).background(c.text.copy(alpha = if (done) .03f else .07f))
            .combinedClickable(onClick = onCheck, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameCheck(done)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                t.label, color = if (done) c.muted else c.text, fontSize = 15.sp, fontFamily = Lexend, fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            if (missed && !done) GameText("Fuera de hora", 11.5.sp, Act.STOP.face, FontWeight.Bold)
        }
        if (!done) {
            if (t.window >= 0) GameChip(t.windowText(), Act.TIME.face, Modifier.padding(start = 5.dp), icon = Icons.Rounded.Schedule)
            if (t.photo || hasPhoto) Icon(Icons.Rounded.PhotoCamera, null, tint = Act.PHOTO.face, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            if (t.difficulty > 0)
                Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(when (t.difficulty) { 1 -> Act.GO.face; 2 -> Act.TIME.face; else -> Act.STOP.face }))
            GameText("${t.minutes}′", 13.sp, c.muted, FontWeight.ExtraBold, Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(Modifier.padding(start = 5.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        HomeText(text, 10.5.sp, color, FontWeight.SemiBold)
    }
}

// ----------------------------------- Semana -----------------------------------

@Composable
private fun WeekTab(tick: Int, k: CoreColors, onMoney: () -> Unit, onEdit: (Editing) -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val budget = remember(tick) { MoneyStore.budget(ctx) }
    val debt = remember(tick) { MoneyStore.debt(ctx) }
    CoreCard(Modifier.clickable(onClick = onMoney)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HomeText("DINERO DE LA SEMANA", 11.sp, c.muted, FontWeight.Bold)
                HomeText(if (budget > 0) "Presupuesto ${Money.fmt(budget)}" else "Sin presupuesto", 16.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                HomeText(if (debt > 0) "Debes ${Money.fmt(debt)}" else "Sin deudas anotadas", 12.5.sp, c.muted)
            }
            HomeText("Cambiar ›", 12.5.sp, c.accent, FontWeight.SemiBold)
        }
    }
    val lists = remember(tick) { RoutineStore.ownLists(ctx) }
    for (l in lists) {
        val (key, block, title) = Triple(l[0], l[1], l[2])
        val tasks = remember(tick, key) { Routine.parse(RoutineStore.keyText(ctx, key, block)) }
        HomeText(title.uppercase() + " · ~${Routine.totalMinutes(tasks)} min", 11.sp, c.muted, FontWeight.Bold, Modifier.padding(start = 4.dp, top = 4.dp))
        CoreCard(padV = 4.dp) {
            if (tasks.isEmpty()) HomeText("Vacía.", 12.5.sp, c.muted, modifier = Modifier.padding(vertical = 10.dp))
            tasks.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(Editing(t, key = key, block = block, index = i)) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        HomeText(t.label, 14.sp, weight = FontWeight.Medium)
                        HomeText(meta(t), 11.5.sp, c.muted)
                    }
                    HomeText("›", 18.sp, c.muted)
                }
            }
        }
    }
    HomeText("Toca una tarea para cambiarla o quitarla. Quitar una de hoy vale desde mañana.", 11.5.sp, c.muted, modifier = Modifier.padding(start = 4.dp))
}

// ----------------------------------- Reportes -----------------------------------

private data class DayStat(val date: LocalDate, val has: Boolean, val total: Int, val done: Int, val minutes: Int, val penalties: Int, val spent: Long)

private fun dayStat(ctx: android.content.Context, d: LocalDate, entries: List<Money.Entry>): DayStat {
    if (!RoutineStore.hasData(ctx, d)) return DayStat(d, false, 0, 0, 0, 0, Money.spent(entries, d, d))
    var total = 0
    var done = 0
    var minutes = 0
    for (b in RoutineStore.BLOCKS) {
        val t = RoutineStore.blockTasks(ctx, b, d)
        val h = RoutineStore.blockDone(ctx, b, d)
        total += t.size
        t.forEachIndexed { i, task -> if (i in h) { done++; minutes += task.minutes } }
    }
    return DayStat(d, true, total, done, minutes, RoutineStore.penalties(ctx, d), Money.spent(entries, d, d))
}

@Composable
private fun ReportsTab(tick: Int, k: CoreColors, motion: Boolean) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val today = RoutineStore.today()
    val entries = remember(tick) { MoneyStore.entries(ctx) }
    val week = remember(tick) { (6 downTo 0).map { dayStat(ctx, today.minusDays(it.toLong()), entries) } }
    var sel by remember { mutableIntStateOf(6) }
    ProgressCard(tick, k, motion)
    // Siete anillos, uno por día.
    CoreCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEachIndexed { i, s ->
                val pct = if (s.total == 0) 0f else s.done / s.total.toFloat()
                val col = when {
                    !s.has -> c.muted
                    pct >= 1f -> k.ok
                    pct >= .6f -> lerp(k.ok, k.warn, .5f)
                    else -> k.warn
                }
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { sel = i }.background(if (i == sel) c.accent.copy(alpha = .14f) else Color.Transparent).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeText(Routine.dayName(s.date.dayOfWeek).take(1).uppercase(), 11.sp, if (i == sel) c.text else c.muted, FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    ProgressRing(if (s.has) pct else 0f, if (!s.has) "–" else if (pct >= 1f) "✓" else "${(pct * 100).toInt()}", null, k, motion, 36.dp, col)
                }
            }
        }
    }
    // Tres cifras de la semana.
    val counted = week.filter { it.has }
    val pctWeek = if (counted.sumOf { it.total } == 0) 0 else counted.sumOf { it.done } * 100 / counted.sumOf { it.total }
    var streak = 0
    for (s in week.reversed()) if (s.has && s.total > 0 && s.done == s.total) streak++ else if (s.date != today) break
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Kpi("$pctWeek %", "tareas cumplidas", Modifier.weight(1f))
        Kpi(if (streak > 0) "$streak 🔥" else "0", "días al 100 %", Modifier.weight(1f))
        Kpi("${week.sumOf { it.penalties }}", "pausas por tareas", Modifier.weight(1f))
    }
    // Minutos en tareas reales, por día.
    CoreCard {
        Row {
            HomeText("MINUTOS EN TAREAS REALES", 11.sp, c.muted, FontWeight.Bold, Modifier.weight(1f))
            val m = week.sumOf { it.minutes }
            HomeText(if (m >= 60) "${m / 60} h ${m % 60}" else "$m min", 11.sp, c.text, FontWeight.Bold)
        }
        val max = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, s ->
                val target = (s.minutes / max.toFloat()).coerceIn(.04f, 1f)
                val h by animateFloatAsState(target, if (motion) tween(600, delayMillis = i * 60) else snap(), label = "barra")
                val pct = if (s.total == 0) 0f else s.done / s.total.toFloat()
                Box(
                    Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (!s.has) c.text.copy(alpha = .08f) else if (i == 6) c.accent else if (pct >= .8f) k.ok else k.warn),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { s -> HomeText(Routine.dayName(s.date.dayOfWeek).take(1).uppercase(), 10.5.sp, c.muted, modifier = Modifier.weight(1f).wrapContentWidth()) }
        }
    }
    // Dinero de la semana frente al presupuesto.
    val budget = remember(tick) { MoneyStore.budget(ctx) }
    val spentWeek = Money.spentWeek(entries, today)
    if (budget > 0 || spentWeek > 0) {
        CoreCard {
            Row {
                HomeText("DINERO DE LA SEMANA", 11.sp, c.muted, FontWeight.Bold, Modifier.weight(1f))
                HomeText(Money.fmt(spentWeek) + if (budget > 0) " de ${Money.fmt(budget)}" else "", 11.sp, if (budget > 0 && spentWeek > budget) k.bad else c.text, FontWeight.Bold)
            }
            if (budget > 0) {
                val part = (spentWeek.toFloat() / maxOf(budget, spentWeek)).coerceIn(0f, 1f)
                val over = (spentWeek - budget).coerceAtLeast(0L).toFloat() / maxOf(budget, spentWeek)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(12.dp).clip(RoundedCornerShape(8.dp)).background(k.track)) {
                    if (part - over > 0f) Box(Modifier.weight(part - over).fillMaxHeight().background(k.ok))
                    if (over > 0f) Box(Modifier.weight(over).fillMaxHeight().background(k.bad))
                    if (1f - part > 0f) Spacer(Modifier.weight(1f - part))
                }
            }
            val dear = week.maxByOrNull { it.spent }
            if (dear != null && dear.spent > 0)
                HomeText("El día más caro fue el ${Routine.dayName(dear.date.dayOfWeek)} (${Money.fmt(dear.spent)}).", 12.sp, c.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
    // El día elegido: tareas y fotos.
    val day = week[sel]
    CoreCard(Modifier.animateContentSize()) {
        HomeText(if (sel == 6) "Hoy" else Routine.dayName(day.date.dayOfWeek).replaceFirstChar { it.uppercase() } + " ${day.date.dayOfMonth}", 16.sp, weight = FontWeight.Bold)
        if (!day.has) HomeText("Sin registro de Foco Core ese día.", 12.5.sp, c.muted, modifier = Modifier.padding(top = 6.dp))
        else for (b in RoutineStore.BLOCKS) {
            val tasks = remember(tick, day.date, b) { RoutineStore.blockTasks(ctx, b, day.date) }
            if (tasks.isEmpty()) continue
            val done = remember(tick, day.date, b) { RoutineStore.blockDone(ctx, b, day.date) }
            val missed = remember(tick, day.date, b) { RoutineStore.blockMissed(ctx, b, day.date) }
            HomeText((BLOCK_TITLES[b] ?: b).uppercase(), 10.5.sp, c.muted, FontWeight.Bold, Modifier.padding(top = 10.dp, bottom = 2.dp))
            tasks.forEachIndexed { i, t ->
                val ok = i in done
                val bad = !ok && (i in missed || day.date.isBefore(today))
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomeText(if (ok) "✓" else if (bad) "✗" else "·", 13.sp, if (ok) k.ok else if (bad) k.bad else c.muted, FontWeight.Bold, Modifier.width(20.dp))
                    HomeText(t.label + if (ok && i in missed) "  (tarde)" else "", 13.sp, if (bad) k.bad else c.text)
                }
            }
        }
        val photos = remember(tick, day.date) { RoutineStore.photos(ctx, day.date) }
        if (photos.isNotEmpty()) {
            HomeText("EVIDENCIAS", 10.5.sp, c.muted, FontWeight.Bold, Modifier.padding(top = 12.dp))
            photos.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { f -> Thumb(f, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    // El búho analiza la semana (lunes a hoy).
    CoreCard {
        Row(verticalAlignment = Alignment.Top) {
            Image(androidx.compose.ui.res.painterResource(es.foco.next.R.drawable.owl_face), null, Modifier.size(42.dp))
            Column(Modifier.padding(start = 12.dp)) {
                HomeText("El búho", 14.5.sp, weight = FontWeight.Bold)
                val text = remember(tick) { weeklyText(ctx) }
                HomeText(text, 13.sp, c.text, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    val c = LocalHome.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.card.copy(alpha = .9f)).border(1.dp, c.text.copy(alpha = .06f), RoundedCornerShape(18.dp)).padding(12.dp)) {
        HomeText(value, 20.sp, weight = FontWeight.Bold)
        HomeText(label, 11.sp, c.muted)
    }
}

// ----------------------------------- v31: cupones y progreso -----------------------------------

/** Tus cupones: normales (30 min) y de semana perfecta (dorados), con el que está en uso. */
@Composable
private fun CouponsCard(tick: Int, k: CoreColors, onUse: (Boolean) -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val list = remember(tick) { es.foco.next.runtime.RewardStore.coupons(ctx) }
    val inUse = remember(tick) { es.foco.next.runtime.RewardStore.grantApp(ctx) }
    val left = remember(tick) { es.foco.next.runtime.RewardStore.grantLeftMs(ctx) }
    val normal = list.count { !it.week }
    val week = list.count { it.week }
    if (list.isEmpty() && inUse.isEmpty()) {
        CoreCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ConfirmationNumber, null, tint = Act.PRIZE.face, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    HomeText("Cupones", 15.sp, weight = FontWeight.Bold)
                    HomeText("Todo hecho → 30′ para la app que quieras", 12.5.sp, c.muted)
                }
            }
        }
        return
    }
    val gold = Color(0xFFE2C27A)
    Column(
        Modifier.fillMaxWidth().clip(CardShape)
            .background(Brush.linearGradient(listOf(gold.copy(alpha = if (week > 0) .30f else .14f), c.card.copy(alpha = .92f))))
            .border(1.dp, gold.copy(alpha = .45f), CardShape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ConfirmationNumber, null, tint = Act.PRIZE.face, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                HomeText(
                    listOfNotNull(
                        if (normal > 0) "$normal ${if (normal == 1) "cupón" else "cupones"} de 30 min" else null,
                        if (week > 0) "$week de semana perfecta" else null,
                    ).joinToString(" · ").ifEmpty { "Cupón en uso" },
                    15.sp, weight = FontWeight.Bold,
                )
                val next = list.filter { !it.week }.minByOrNull { it.expires }
                HomeText(
                    if (inUse.isNotEmpty()) "En uso: $inUse · quedan ${(left + 59_999) / 60_000} min"
                    else if (next != null) "El más antiguo caduca el ${Routine.dayName(next.expires.dayOfWeek)} ${next.expires.dayOfMonth}. Redes: 15 min."
                    else "Valen hasta el domingo y saltan las reglas (menos la noche).",
                    12.sp, c.muted,
                )
            }
        }
        if (inUse.isEmpty()) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (normal > 0) GameButton("Usar", Act.PRIZE, Modifier.weight(1f), icon = Icons.Rounded.ConfirmationNumber) { onUse(false) }
            if (week > 0) GameButton("Semana", Act.PHOTO, Modifier.weight(1f), icon = Icons.Rounded.ConfirmationNumber) { onUse(true) }
        }
    }
}

/** Elegir la app del cupón. Solo apps de ocio; las condiciones se comprueban al canjear. */
@Composable
private fun CouponSheet(vm: HomeViewModel, week: Boolean, k: CoreColors, onDismiss: () -> Unit, onDone: () -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val why = remember { es.foco.next.runtime.RewardStore.whyNot(ctx, week) }
    var message by remember { mutableStateOf(why) }
    val apps = remember(vm.apps) {
        vm.apps.filter {
            it.section == HomeSections.Section.OCIO || es.foco.next.core.AbsoluteRules.social(it.pkg) // v32: Telegram (bloqueo total) no se abre con cupón
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    CoreSheet(k, onDismiss) {
        HomeText(if (week) "Cupón de semana perfecta" else "Usar un cupón", 19.sp, weight = FontWeight.Bold)
        HomeText(
            if (week) "30 min que saltan las reglas (menos la noche). Puedes usar los dos seguidos: 1 h." else "30 min para una app de ocio; 15 si es una red social. Uno al día.",
            12.5.sp, c.muted,
        )
        message?.let {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(k.warn.copy(alpha = .16f)).padding(12.dp)) {
                HomeText(it, 13.sp, k.warn, FontWeight.SemiBold)
            }
        }
        if (why == null) {
            Label("¿PARA QUÉ APP?")
            if (apps.isEmpty()) HomeText("No hay apps de ocio en tu plan.", 12.5.sp, c.muted)
            apps.forEach { app ->
                val social = es.foco.next.core.AbsoluteRules.social(app.pkg)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        val r = es.foco.next.runtime.RewardStore.redeem(ctx, app.pkg, app.label, week)
                        if (r != null) message = r
                        else {
                            es.foco.next.ui.HomeApps.open(ctx, app)
                            onDone()
                        }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val img = vm.iconOf(app)
                    if (img != null) Image(img, null, Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
                    HomeText(app.label, 14.5.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Chip("${es.foco.next.core.care.Rewards.minutes(social)} min", if (social) k.warn else k.ok)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Racha, próximo desbloqueo y camino a la semana perfecta. */
@Composable
private fun ProgressCard(tick: Int, k: CoreColors, motion: Boolean) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val streak = remember(tick) { es.foco.next.runtime.RewardStore.streak(ctx) }
    val have = remember(tick) { es.foco.next.runtime.RewardStore.unlocked(ctx) }
    val perfect = remember(tick) { es.foco.next.runtime.RewardStore.perfectDaysThisWeek(ctx) }
    val next = es.foco.next.core.care.Rewards.nextMilestone(have)
    CoreCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HomeText("TU CONSTANCIA", 11.sp, c.muted, FontWeight.Bold)
                HomeText(if (streak == 0) "Hoy empieza tu racha" else "$streak ${if (streak == 1) "día" else "días"} seguidos al 100 % 🔥", 17.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (next != null) {
            val target = next[0]
            val v by animateFloatAsState((streak / target.toFloat()).coerceIn(0f, 1f), if (motion) tween(700) else snap(), label = "hito")
            HomeText("Próximo: ${es.foco.next.core.care.Rewards.UNLOCK_NAMES[next[1]]} a los $target días", 12.5.sp, c.muted, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(k.track)) {
                Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(k.ok))
            }
        } else HomeText("Lo desbloqueaste todo. Sigue por ti.", 12.5.sp, c.muted, modifier = Modifier.padding(top = 8.dp))
        FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            es.foco.next.core.care.Rewards.UNLOCK_IDS.forEachIndexed { i, id ->
                val ok = id in have
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (ok) k.ok.copy(alpha = .16f) else c.text.copy(alpha = .06f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    HomeText((if (ok) "✓ " else "🔒 ") + es.foco.next.core.care.Rewards.UNLOCK_NAMES[i] + " · ${es.foco.next.core.care.Rewards.MILESTONES[i]}d", 11.5.sp, if (ok) k.ok else c.muted, FontWeight.SemiBold)
                }
            }
        }
        HomeText("Semana perfecta: $perfect de 7 días · con fotos y sin atajos ganas 2 cupones especiales.", 12.sp, c.muted, modifier = Modifier.padding(top = 10.dp))
    }
}

// ----------------------------------- Piezas -----------------------------------

@Composable
private fun CoreCard(modifier: Modifier = Modifier, padV: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    val c = LocalHome.current
    Column(
        modifier.fillMaxWidth().clip(GameLarge).background(if (c.card.alpha > .99f) c.card.copy(alpha = .94f) else c.card)
            .border(1.dp, c.text.copy(alpha = .07f), GameLarge).padding(horizontal = 16.dp, vertical = padV),
    ) { content() }
}

/** Anillo de progreso que se llena con animación (quieto si hay que reducir movimiento). */
@Composable
private fun ProgressRing(value: Float, center: String, sub: String?, k: CoreColors, motion: Boolean, size: androidx.compose.ui.unit.Dp, color: Color = k.ok) {
    val c = LocalHome.current
    val v by animateFloatAsState(value.coerceIn(0f, 1f), if (motion) tween(800) else snap(), label = "anillo")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * .11f
            val inset = w / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(k.track, 0f, 360f, false, tl, arc, style = Stroke(w))
            if (v > 0f) drawArc(color, -90f, 360f * v, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HomeText(center, if (size > 60.dp) 22.sp else 10.sp, c.text, FontWeight.Bold)
            if (sub != null) HomeText(sub, 10.5.sp, c.muted)
        }
    }
}

@Composable
private fun ActionButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { HomeText(text, 13.5.sp, fg, FontWeight.SemiBold) }
}

/** Hoja inferior propia (no un diálogo gris del sistema): sube desde abajo con el color del tema. */
@Composable
private fun CoreSheet(k: CoreColors, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visibleState = shown, enter = slideInVertically { it } + fadeIn()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(k.sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(LocalHome.current.muted.copy(alpha = .5f)))
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    HomeText(text, 11.sp, LocalHome.current.muted, FontWeight.Bold, Modifier.padding(top = 14.dp, bottom = 7.dp))

/** Fila de opciones; la elegida con el acento del tema. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T?, k: CoreColors, onPick: (T) -> Unit) {
    val c = LocalHome.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            val bg by animateColorAsState(if (on) c.accent else c.text.copy(alpha = .07f), tween(200), label = "opción")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onPick(v) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { HomeText(label, 13.sp, if (on) k.onAccent else c.text, if (on) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun TaskSheet(e: Editing, k: CoreColors, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    val t0 = e.task
    val isNew = t0 == null
    var name by remember { mutableStateOf(t0?.label ?: "") }
    var whenSel by remember { mutableIntStateOf(RoutineStore.WHEN_TODAY) }
    var kind by remember { mutableStateOf(if (java.time.LocalTime.now().hour >= 14) Routine.Kind.NIGHT else Routine.Kind.MORNING) }
    var days by remember { mutableStateOf(setOf(LocalDate.now().dayOfWeek)) }
    var minutes by remember { mutableIntStateOf(t0?.minutes ?: 15) }
    var window by remember { mutableIntStateOf(t0?.window ?: -1) }
    var difficulty by remember { mutableIntStateOf(t0?.difficulty ?: 0) }
    var photo by remember { mutableStateOf(t0?.photo ?: false) }
    val suggestions = remember { recentLabels(ctx) }
    CoreSheet(k, onDismiss) {
        HomeText(if (isNew) "Nueva tarea" else "Editar tarea", 19.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.text.copy(alpha = .06f)).border(1.5.dp, c.accent, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (name.isEmpty()) HomeText("¿Qué vas a hacer?", 16.sp, c.muted)
            BasicTextField(
                name, { if (it.length <= 60) name = it.replace("\n", " ") },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isNew && suggestions.isNotEmpty())
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s ->
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.text.copy(alpha = .07f)).clickable { name = s }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        HomeText(s, 12.sp, c.text)
                    }
                }
            }
        if (isNew) {
            Label("CUÁNDO")
            Segmented(
                listOf(RoutineStore.WHEN_TODAY to "Hoy", RoutineStore.WHEN_TOMORROW to "Mañana", RoutineStore.WHEN_ALWAYS to "Siempre", RoutineStore.WHEN_DAYS to "Días"),
                whenSel, k,
            ) { whenSel = it }
            if (whenSel == RoutineStore.WHEN_DAYS) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    java.time.DayOfWeek.values().forEach { w ->
                        val on = w in days
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (on) c.accent else c.text.copy(alpha = .07f))
                                .clickable { days = if (on) days - w else days + w },
                            contentAlignment = Alignment.Center,
                        ) { HomeText(listOf("L", "M", "X", "J", "V", "S", "D")[w.value - 1], 13.sp, if (on) k.onAccent else c.text, FontWeight.SemiBold) }
                    }
                }
            }
            if (whenSel == RoutineStore.WHEN_ALWAYS || whenSel == RoutineStore.WHEN_DAYS) {
                Label("FRANJA")
                Segmented(listOf(Routine.Kind.MORNING to "☀ Mañana", Routine.Kind.NIGHT to "☾ Tarde y noche"), kind, k) { kind = it }
            }
        }
        Label("DURA")
        Segmented(listOf(5, 10, 15, 30, 45, 60).map { it to (if (it == 60) "60′" else "$it") }, minutes, k) { minutes = it }
        Label("HORA LÍMITE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val off = window < 0
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (off) c.accent else c.text.copy(alpha = .07f))
                    .clickable { window = -1 }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { HomeText("Sin hora", 13.sp, if (off) k.onAccent else c.text, FontWeight.SemiBold) }
            fun shift(by: Int) {
                val base = if (window < 0) ((java.time.LocalTime.now().hour + 1) * 60) else window
                window = ((base + by) % 1440 + 1440) % 1440
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.text.copy(alpha = .07f)).clickable { shift(-15) }.padding(horizontal = 16.dp, vertical = 10.dp)) { HomeText("−", 15.sp, c.text, FontWeight.Bold) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (!off) c.accent else c.text.copy(alpha = .07f)).clickable { if (off) shift(0) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { HomeText(if (off) "⏰ Poner" else "⏰ " + Routine.Task("", 1, window, 0).windowText(), 13.sp, if (!off) k.onAccent else c.text, FontWeight.Bold) }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.text.copy(alpha = .07f)).clickable { shift(15) }.padding(horizontal = 16.dp, vertical = 10.dp)) { HomeText("+", 15.sp, c.text, FontWeight.Bold) }
        }
        Label("DIFICULTAD")
        Segmented(listOf(1 to "Fácil", 2 to "Media", 3 to "Difícil"), difficulty, k) { difficulty = if (difficulty == it) 0 else it }
        Label("EVIDENCIA")
        Segmented(listOf(true to "📷 Pedir foto", false to "Sin foto"), photo, k) { photo = it }
        val label = Routine.cleanLabel(name)
        val ready = label.isNotEmpty() && (whenSel != RoutineStore.WHEN_DAYS || days.isNotEmpty() || !isNew)
        val summary = buildString {
            append(if (isNew) "Añadir" else "Guardar")
            append(" · $minutes min")
            if (window >= 0) append(" antes de las " + Routine.Task("", 1, window, 0).windowText())
        }
        Spacer(Modifier.height(18.dp))
        GameButton(summary, Act.GO, Modifier.fillMaxWidth(), enabled = ready) {
            if (!ready) return@GameButton
            val t = Routine.Task(label, minutes, window, difficulty, photo)
            when {
                isNew -> RoutineStore.addTask(ctx, whenSel, kind, days, t)
                e.today -> RoutineStore.replaceToday(ctx, e.block, e.index, t)
                e.key != null -> RoutineStore.replaceInKey(ctx, e.key, e.block, e.index, t)
            }
            onSaved()
        }
        if (!isNew) {
            Text(
                if (e.today) "Quitar (vale desde mañana)" else "Quitar",
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    if (e.today) RoutineStore.removeToday(ctx, e.block, e.index)
                    else if (e.key != null) RoutineStore.removeFromKey(ctx, e.key, e.block, e.index)
                    onSaved()
                }.padding(vertical = 8.dp),
                color = k.bad, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Sugerencias: nombres de tus listas (sin repetir), los más recientes primero. */
private fun recentLabels(ctx: android.content.Context): List<String> {
    val out = LinkedHashSet<String>()
    val d = RoutineStore.today()
    for (i in 0..7) for (t in Routine.parse(RoutineStore.eventsText(ctx, d.plusDays(i.toLong())))) out.add(t.label)
    for (l in RoutineStore.ownLists(ctx)) for (t in Routine.parse(RoutineStore.keyText(ctx, l[0], l[1]))) out.add(t.label)
    return out.take(6)
}

/** Presupuesto y deuda en una hoja del tema (antes, diálogos grises). */
@Composable
private fun MoneySheet(k: CoreColors, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val c = LocalHome.current
    val ctx = LocalContext.current
    var budget by remember { mutableStateOf(MoneyStore.budget(ctx).let { if (it > 0) Money.fmt(it).replace("$", "") else "" }) }
    var debt by remember { mutableStateOf(MoneyStore.debt(ctx).let { if (it > 0) Money.fmt(it).replace("$", "") else "" }) }
    var spend by remember { mutableStateOf("") }
    CoreSheet(k, onDismiss) {
        HomeText("Dinero", 19.sp, weight = FontWeight.Bold)
        HomeText("No es para mortificarte: es para que el búho te diga cuánto queda.", 12.5.sp, c.muted)
        CoreMoneyField("PRESUPUESTO DE LA SEMANA", budget) { budget = it }
        CoreMoneyField("LO QUE DEBO", debt) { debt = it }
        CoreMoneyField("ANOTAR UN GASTO DE HOY", spend) { spend = it }
        Spacer(Modifier.height(18.dp))
        GameButton("Guardar", Act.DONE, Modifier.fillMaxWidth()) {
            MoneyStore.setBudget(ctx, Money.parse(budget).coerceAtLeast(0L))
            MoneyStore.setDebt(ctx, Money.parse(debt).coerceAtLeast(0L))
            val s = Money.parse(spend)
            if (s > 0) MoneyStore.add(ctx, s, "")
            onSaved()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CoreMoneyField(title: String, value: String, set: (String) -> Unit) {
    val c = LocalHome.current
    Label(title)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.text.copy(alpha = .06f)).padding(14.dp)) {
        if (value.isEmpty()) HomeText("0", 16.sp, c.muted)
        BasicTextField(
            value, { set(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(9)) },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Datos de lunes a hoy para el análisis del búho. */
private fun weeklyText(ctx: android.content.Context): String {
    val today = RoutineStore.today()
    val start = Money.weekStart(today)
    val entries = MoneyStore.entries(ctx)
    val days = ArrayList<FocoCore.Day>()
    var d = start
    while (!d.isAfter(today)) {
        // Días sin datos (antes de usar Foco Core) no cuentan como 0 %.
        if (!RoutineStore.hasData(ctx, d)) {
            d = d.plusDays(1)
            continue
        }
        fun count(b: String): IntArray {
            val t = RoutineStore.blockTasks(ctx, b, d)
            val h = RoutineStore.blockDone(ctx, b, d)
            return intArrayOf(t.size, t.indices.count { it in h })
        }
        val m = count(RoutineStore.MORNING)
        val n = count(RoutineStore.NIGHT)
        val e = count(RoutineStore.EVENT)
        days.add(FocoCore.Day(m[0], m[1], n[0], n[1], e[0], e[1], RoutineStore.penalties(ctx, d), Money.spent(entries, d, d), 0))
        d = d.plusDays(1)
    }
    if (days.isEmpty()) return "Cuando completes tu primer día, aquí verás lo que te cuesta más y qué cambiar."
    return FocoCore.weekly(days, MoneyStore.budget(ctx), Money.spentWeek(entries, today))
}

@Composable
private fun Thumb(f: java.io.File, modifier: Modifier) {
    val img: ImageBitmap? = remember(f.path) {
        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
        android.graphics.BitmapFactory.decodeFile(f.path, o)?.asImageBitmap()
    }
    if (img != null) Image(img, null, modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    else Spacer(modifier)
}
