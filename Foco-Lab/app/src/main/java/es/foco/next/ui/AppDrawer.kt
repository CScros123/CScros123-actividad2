@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package es.foco.next.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.home.HomeSections
import es.foco.next.core.home.HomeSections.Section
import kotlinx.coroutines.launch

/** Fila del cajón: cabecera de sección o app. */
private sealed class Row_ {
    data class Header(val section: Section) : Row_()
    data class AppRow(val app: HomeApps.App) : Row_()
}

/** Letra del índice A–Z para una app ("#" para números y símbolos). */
private fun letterOf(label: String): Char {
    val f = HomeSections.fold(label).firstOrNull() ?: return '#'
    return if (f in 'a'..'z') f.uppercaseChar() else '#'
}

private val LETTERS = ("#ABCDEFGHIJKLMNOPQRSTUVWXYZ").toList()



/**
 * Cajón "Todas las aplicaciones": buscador abajo (zona del pulgar), lista limpia, insignia SOLO en
 * las apps con fricción ("Usa banco"), "Hoy: N min" cuando Android da el dato, y un índice A–Z en el
 * borde derecho para saltar sin abrir el teclado.
 */
@Composable
fun AppDrawer(
    vm: HomeViewModel,
    focusSearch: Boolean,
    onOpen: (HomeApps.App) -> Unit,
    onClose: () -> Unit,
    onIntent: (android.content.Intent) -> Unit = {},
) {
    val apps = vm.apps
    val usage = vm.usageToday
    val c = LocalHome.current
    var query by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visible = apps
    val rows = remember(visible, query) {
        if (query.isBlank()) {
            val out = ArrayList<Row_>()
            var current: Section? = null
            for (a in visible) {
                if (a.section != current) {
                    current = a.section
                    out.add(Row_.Header(a.section))
                }
                out.add(Row_.AppRow(a))
            }
            out
        } else HomeApps.search(visible, query).map { Row_.AppRow(it) }
    }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    var menu by remember { mutableStateOf<HomeApps.App?>(null) }
    var uninstall by remember { mutableStateOf<HomeApps.App?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.card.copy(alpha = 1f)).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Manija: tocarla o tirar de ella hacia abajo cierra el cajón (sustituye a "Cerrar").
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .draggable(
                    rememberDraggableState { pull += it },
                    Orientation.Vertical,
                    onDragStopped = {
                        if (pull > 80f) onClose()
                        pull = 0f
                    },
                )
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Cerrar todas las apps" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(c.muted.copy(alpha = .45f)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HomeText("Todas las aplicaciones", 17.sp, weight = FontWeight.Bold)
                HomeText(
                    when (vm.careMode) {
                        es.foco.next.core.care.CareMode.Modo.ENFOQUE -> "Enfoque · ocio con tu hora libre de Foco Core"
                        es.foco.next.core.care.CareMode.Modo.CUIDADO -> "Modo Cuidado · el ocio no se cuenta hoy"
                        else -> "Modo Normal · 5 min de ocio por hora"
                    },
                    12.sp, c.muted,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 34.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty())
                    item { HomeText("Nada coincide con ${query.trim()}", 13.sp, c.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) }
                itemsIndexed(
                    rows,
                    key = { i, r -> if (r is Row_.AppRow) r.app.pkg + "/" + r.app.component.className else "h$i" },
                    contentType = { _, r -> if (r is Row_.AppRow) 1 else 0 },
                ) { _, r ->
                    when (r) {
                        // v41: cada grupo con su color de acción (ocio naranja, bloqueadas coral, el resto verde).
                        is Row_.Header -> HomeText(
                            r.section.title.uppercase(), 12.sp,
                            when (r.section) { Section.OCIO -> Act.TIME.face; Section.BLOQUEADA -> Act.STOP.face; else -> Act.GO.face },
                            FontWeight.Bold, Modifier.padding(start = 4.dp, top = 12.dp),
                        )
                        is Row_.AppRow -> AppRowView(r.app, vm.iconOf(r.app), usage[r.app.pkg] ?: 0L, vm.night, { onOpen(r.app) }, vm.careMode, vm.showNumbers) { if (canUninstall(r.app.pkg)) menu = r.app }
                    }
                }
            }
            var bubble by remember { mutableStateOf<Char?>(null) }
            if (query.isBlank() && rows.size > 12)
                AlphabetIndex(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).padding(vertical = 8.dp),
                    onActive = { bubble = it },
                ) { letter ->
                    val i = rows.indexOfFirst { it is Row_.AppRow && letterOf(it.app.label) == letter }
                    if (i >= 0) scope.launch { list.scrollToItem(i) }
                }
            // Versión general explícita: dentro de Box anidada en Column, Kotlin elegiría la de ColumnScope.
            androidx.compose.animation.AnimatedVisibility(bubble != null, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Text((bubble ?: ' ').toString(), color = c.card, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Buscador abajo (Enter abre la primera coincidencia).
        Row(
            Modifier.fillMaxWidth().background(lerp(c.card, c.background, .3f).copy(alpha = 1f)).padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(999.dp))
                    .background(c.raised(.5f))
                    .border(1.dp, lerp(c.card, c.accent, .18f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) HomeText("Filtrar por nombre…", 14.sp, c.muted)
                    BasicTextField(
                        query, { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val first = HomeApps.search(visible, query).firstOrNull()
                            if (first != null) {
                                keyboard?.hide()
                                onOpen(first)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) HomeText("Limpiar", 12.sp, c.muted, FontWeight.SemiBold, Modifier.clickable { query = "" }.padding(6.dp))
            }
        }
    }
    menu?.let { app ->
        AppMenu(app, vm.iconOf(app), usage[app.pkg] ?: 0L, vm.showNumbers, onDismiss = { menu = null },
            onInfo = {
                menu = null
                onIntent(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + app.pkg)))
            },
            onUninstall = if (canUninstall(app.pkg)) ({ menu = null; uninstall = app }) else null,
        )
    }
    uninstall?.let { app ->
        Sheet({ uninstall = null }) {
            HomeText("¿Desinstalar ${app.label}?", 17.sp, weight = FontWeight.Bold)
            HomeText("Android te pedirá confirmarlo una vez más. Sus datos se borran del teléfono.", 13.sp, c.muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Mejor no", Modifier.weight(1f), strong = false) { uninstall = null }
                PillButton("Desinstalar", Modifier.weight(1f), strong = true) {
                    uninstall = null
                    onIntent(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:" + app.pkg)))
                }
            }
        }
    }
    }
}

@Composable
private fun AppRowView(
    a: HomeApps.App,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    usedMs: Long,
    night: Boolean,
    onClick: () -> Unit,
    mode: es.foco.next.core.care.CareMode.Modo = es.foco.next.core.care.CareMode.Modo.NORMAL,
    showNumbers: Boolean = true,
    onLongClick: () -> Unit = {},
) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    val friction = a.section == Section.OCIO
    val blocked = a.section == Section.BLOQUEADA
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Row(
        Modifier.fillMaxWidth().clip(GameMid)
            .background(c.raised(.55f))
            .border(1.dp, if (friction) Act.TIME.face.copy(alpha = .35f) else lerp(c.card, c.accent, .1f), GameMid)
            .combinedClickable(onClick = onClick, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null)
            Image(icon, null, Modifier.size(40.dp).alpha(if (friction || blocked) .7f else 1f), colorFilter = if (friction || blocked) grey else null)
        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(lerp(c.card, c.accent, .1f)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            HomeText(a.label, 14.sp, weight = FontWeight.SemiBold)
            val min = usedMs / 60_000
            if (min >= 1 && showNumbers) HomeText("Hoy: ${minutes(min)}", 11.5.sp, c.muted)
        }
        when {
            // De noche el ocio está cerrado (Regla 2): se dice, en vez de invitar a intentarlo.
            friction && night ->
                GameChip("${es.foco.next.core.AbsoluteRules.nightEnd()}:00", c.muted, icon = Icons.Rounded.Lock)
            // Cuidado: la etiqueta dice cómo funciona el ocio en el modo actual, sin alarma.
            friction && mode != es.foco.next.core.care.CareMode.Modo.CUIDADO ->
                GameChip(if (mode == es.foco.next.core.care.CareMode.Modo.ENFOQUE) "Hora libre" else "5′/h", Act.TIME.face)
            blocked -> Icon(Icons.Rounded.Lock, "Bloqueo total", tint = Act.STOP.face, modifier = Modifier.size(18.dp))
        }
    }
}

/** Índice A–Z: tocar o deslizar el pulgar por el borde; burbuja con la letra y tic háptico. */
@Composable
private fun AlphabetIndex(modifier: Modifier, onActive: (Char?) -> Unit, onLetter: (Char) -> Unit) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }
    fun release() {
        active = null
        onActive(null)
    }
    fun pick(y: Float) {
        val i = ((y / height) * LETTERS.size).toInt().coerceIn(0, LETTERS.size - 1)
        val l = LETTERS[i]
        if (l != active) {
            active = l
            onActive(l)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(l)
        }
    }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize()
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); release() })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                    ) { change, _ -> pick(change.position.y) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LETTERS.forEach { HomeText(it.toString(), 9.5.sp, if (it == active) c.accent else c.muted, FontWeight.SemiBold) }
        }
    }
}

/**
 * Foco y sus piezas no tienen menú: su pantalla de información permite Forzar detención y Borrar
 * datos (reiniciar el banco), un punto de fuga.
 */
private fun canUninstall(pkg: String) = !pkg.startsWith("es.foco.")

/** Mantener pulsada una app: lo justo (información y desinstalar), sin texto de más. */
@Composable
private fun AppMenu(
    app: HomeApps.App,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    usedMs: Long,
    showNumbers: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: (() -> Unit)?,
) {
    val c = LocalHome.current
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(44.dp))
            Column(Modifier.padding(start = 12.dp)) {
                HomeText(app.label, 17.sp, weight = FontWeight.Bold)
                val min = usedMs / 60_000
                HomeText(if (showNumbers && min >= 1) "Hoy: ${minutes(min)} · ${app.section.title}" else app.section.title, 12.sp, c.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        MenuRow("Información de la app", onInfo)
        if (onUninstall != null) MenuRow("Desinstalar", onUninstall)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    val c = LocalHome.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .background(c.raised(.5f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeText(label, 14.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        HomeText("›", 16.sp, c.muted)
    }
}

@Composable
private fun PillButton(label: String, modifier: Modifier, strong: Boolean, onClick: () -> Unit) {
    val c = LocalHome.current
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(999.dp))
            .background(if (strong) c.accent else c.raised(.5f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HomeText(label, 14.sp, if (strong) c.card else c.text, FontWeight.SemiBold) }
}
