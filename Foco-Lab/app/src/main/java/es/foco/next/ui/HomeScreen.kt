@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package es.foco.next.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.home.HomeState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Lo que la pantalla pide a la actividad (Android: abrir apps, Lab, permisos, gestos). */
interface HomeActions {
    fun openApp(app: HomeApps.App)

    fun openLab(game: String)

    fun stepAction()

    fun requestRole()

    fun pickDock(index: Int)

    fun openFoco()

    fun notifications()

    fun lockScreen()

    fun openIntent(i: android.content.Intent)

    fun setDnd(on: Boolean)

    fun fixDns()

    fun chooseCompanion()

    fun toast(s: String)

    fun openShortPractice()

    fun openAllGames()

    fun openPermission(kind: String)

    fun owlAction(mode: es.foco.next.core.home.OwlBrain.Mode)

    // De control a cuidado.
    fun openModes()

    fun chooseCare()

    fun answerMood(score: Int, word: String): Boolean

    fun moodFromCard()

    fun skipMood()

    fun nightHours()

    fun chooseMoodAgain()

    fun owlBannerOpen(target: String)

    fun pickTheme()

    fun openRoutine()

    fun editRoutine(kind: es.foco.next.core.care.Routine.Kind)

    fun planWeek()

    fun editMoney()

    fun openCore()

    fun openCoreReports()

    fun openStats()

    fun openBlocks()

    fun startFreeHour()

    fun completeTask(block: String, index: Int)

    fun uncheckTask(block: String, index: Int)

    fun toggleTaskTimer(block: String, index: Int)

    fun photoTask(block: String, index: Int)

    fun editEvents()

    /** v41: respirar (menú del búho). */
    fun breathe()
}

/**
 * Foco Home en Compose. Estructura estable: el contenido central se desplaza dentro de su propio
 * scroll con margen de la barra de estado (statusBarsPadding), así el reloj nunca queda debajo de
 * la barra ni de la píldora multimedia al desplegar "Protección activa". El pie (dock, buscador,
 * "Todas las apps") vive sobre la colina, con margen de la barra de navegación.
 *
 * Gestos: deslizar hacia abajo en la parte alta = cortina de notificaciones; deslizar hacia arriba
 * (al final del contenido o sobre el pie) = todas las apps con el buscador; doble toque en el cielo
 * = bloquear pantalla (servicio de Accesibilidad).
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    a: HomeActions,
    drawerOpen: Boolean,
    drawerFocus: Boolean,
    homeTick: Int,
    openDrawer: (Boolean) -> Unit,
    closeDrawer: () -> Unit,
) {
    val colors = rememberHomeColors(vm.palette)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var hillTop by remember { mutableFloatStateOf(-1f) }
    // Tres paneles: 0 Ajustes · 1 Inicio · 2 Hacer. El paisaje, la colina, el compañero y el dock
    // no se mueven: solo desliza el contenido.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 3 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(homeTick) {
        if (homeTick > 0) pager.animateScrollToPage(1)
    }
    androidx.compose.runtime.LaunchedEffect(pager) {
        var first = true
        androidx.compose.runtime
            .snapshotFlow { pager.settledPage }
            .collect {
                if (!first)
                    haptic.performHapticFeedback(
                        HapticFeedbackType.TextHandleMove
                    ) // tic al cambiar de panel
                first = false
            }
    }
    androidx.activity.compose.BackHandler(enabled = pager.currentPage != 1 && !drawerOpen) {
        scope.launch { pager.animateScrollToPage(1) }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalHome provides colors) {
        Box(Modifier.fillMaxSize()) {
            // Fondo quieto: la foto del tema (decodificada una vez) o el paisaje dibujado.
            val photo = vm.photo
            if (photo != null) PhotoBackground(photo, hillTop)
            else
                HomeLandscape(hillTop, Modifier.fillMaxSize()) {
                    // 1 en Inicio y 0,2 en Ajustes/Hacer, siguiendo el dedo al deslizar.
                    val dist =
                        kotlin.math
                            .abs(pager.currentPage - 1 + pager.currentPageOffsetFraction)
                            .coerceIn(0f, 1f)
                    1f - .8f * dist
                }
            Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.pager.HorizontalPager(
                    pager,
                    Modifier.weight(1f).fillMaxWidth(),
                    // Fluidez: los paneles vecinos ya están compuestos antes de deslizar (antes se
                    // componían en el primer fotograma del gesto: tirón).
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    // Cada panel en su propia capa: al deslizar solo se mueve, no se vuelve a
                    // pintar.
                    Box(Modifier.fillMaxSize().graphicsLayer {}) {
                        when (page) {
                            0 -> AnalysisPanel(vm, a)
                            2 -> PracticePanel(vm, a)
                            else -> CenterPanel(vm, a, openDrawer)
                        }
                    }
                }
                Footer(
                    vm,
                    a,
                    openDrawer,
                    pager.currentPage,
                    { p -> scope.launch { pager.animateScrollToPage(p) } },
                    Modifier.onGloballyPositioned {
                        hillTop = it.positionInRoot().y - with(density) { 26.dp.toPx() }
                    },
                )
            }
            AnimatedVisibility(
                drawerOpen,
                enter = slideInVertically { it / 4 } + fadeIn(),
                exit = slideOutVertically { it / 4 } + fadeOut(),
            ) {
                AppDrawer(vm, drawerFocus, a::openApp, closeDrawer, a::openIntent)
            }
        }
    }
}

/** Panel central: reloj, tarjetas y protección. Con los gestos de cortina y cajón. */
@Composable
private fun CenterPanel(vm: HomeViewModel, a: HomeActions, openDrawer: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val threshold = with(density) { 72.dp.toPx() }
    val notif by androidx.compose.runtime.rememberUpdatedState(vm.gestureNotifications)
    val drawer by androidx.compose.runtime.rememberUpdatedState(vm.gestureDrawer)
    // Gestos a partir del "sobre-desplazamiento": arriba del todo y tirar hacia abajo =
    // notificaciones; abajo del todo y tirar hacia arriba = todas las apps.
    val gestures = remember {
        object : NestedScrollConnection {
            var pull = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                pull += available.y
                if (pull > threshold) {
                    pull = 0f
                    if (notif) a.notifications()
                } else if (pull < -threshold) {
                    pull = 0f
                    if (drawer) openDrawer(true)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                pull = 0f
                return super.onPostFling(consumed, available)
            }
        }
    }
    // El contenido se recorta bajo la barra de estado: al desplazarse no pasa por detrás del reloj.
    Box(Modifier.fillMaxSize().statusBarsPadding().clipToBounds()) {
        Column(
            Modifier.fillMaxSize()
                .nestedScroll(gestures)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(vm, a)
            // v41: avisos que no corren prisa (plan del domingo, Foco como inicio, resumen de la
            // noche,
            // NextDNS, cuánto estuvo apagado) van a la campana en vez de apilarse como tarjetas.
            var inboxOpen by remember { mutableStateOf(false) }
            val e = vm.estado
            val inbox =
                listOfNotNull(
                    if (vm.planDue) "plan" else null,
                    if (vm.system.accessibility && vm.gapNotice != null) "hueco" else null,
                    if (!vm.system.homeRole) "inicio" else null,
                    if (vm.estadoActual == HomeState.Estado.NOCHE && e != null) "noche" else null,
                    if (vm.dnsLine != null) "dns" else null,
                )
            HudRow(vm, inbox.size, inboxOpen) { inboxOpen = !inboxOpen }
            // El búho es la voz de Foco: su último mensaje, como banner, al volver al inicio.
            vm.owlBanner?.let { b ->
                OwlBanner(
                    b[0],
                    b[1],
                    b[2],
                    onOpen = {
                        a.owlBannerOpen(b[2])
                        vm.closeBanner()
                    },
                    onClose = vm::closeBanner,
                )
            }
            // Rutina obligatoria de ahora: lo primero, con lo que falta y el tiempo estimado.
            // Foco Core: un solo widget para tareas 2+2, penalización, hora libre y dinero.
            vm.core?.let { s -> CoreWidget(s, a::openCore, a::startFreeHour, vm.urgent) }
            // Cuenta atrás del widget: se refresca cada minuto con el inicio a la vista.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    vm.refreshCore()
                    // v29: el fondo cambia solo con la hora aunque el inicio siga a la vista.
                    vm.syncTheme()
                }
            }
            // Gasto del día: lo que queda hoy y una idea para lo que se debe (sin regañar).
            // (El dinero vive ahora dentro del widget de Foco Core.)
            // Foco apagado sí se ve siempre: sin él no protege nada.
            if (!vm.system.accessibility)
                OwlNotice(
                    es.foco.next.core.home.OwlCare.OFF_TEXT,
                    "Encenderme",
                    { a.openPermission("accessibility") },
                )
            androidx.compose.animation.AnimatedVisibility(inboxOpen && inbox.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Domingo: el búho invita a planificar la semana (una vez).
                    if (vm.planDue) PlanCard(a::planWeek)
                    // Cuánto tiempo estuvo apagado Foco.
                    if (vm.system.accessibility)
                        vm.gapNotice?.let {
                            OwlNotice(it, "De nada, búho", vm::closeGap, vm::closeGap)
                        }
                    if (!vm.system.homeRole) RoleCard(a::requestRole)
                    if (vm.estadoActual == HomeState.Estado.NOCHE && e != null) NightCard(e)
                    // NextDNS: el búho cuenta lo que el escudo frenó hoy.
                    vm.dnsLine?.let { line ->
                        HomeCard {
                            Column {
                                HomeText(line, 13.5.sp, weight = FontWeight.Medium)
                                if (vm.dnsTop.isNotEmpty())
                                    HomeText(
                                        "También: " + vm.dnsTop.joinToString(" · "),
                                        11.5.sp,
                                        LocalHome.current.muted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                            }
                        }
                    }
                }
            }
            // Cuidado: la pregunta de ánimo, la primera vez que se abre Foco en el día.
            if (vm.askMood)
                MoodCard(
                    "¿Cómo estás?",
                    "Ahora no",
                    true,
                    onAnswer = { n, w -> a.answerMood(n, w) },
                    onCare = a::moodFromCard,
                    onSkip = a::skipMood,
                )
            // Minimalista: el reto rápido vive en el panel «Hacer» (ya estaba allí); aquí, lo
            // justo.
            ProtectionBar(vm.system, a::openIntent, a::setDnd, a::fixDns)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * v41: marcador del inicio (modo juego): racha, barra del día, cupones y la campana con los avisos
 * que no corren prisa.
 */
@Composable
private fun HudRow(vm: HomeViewModel, inboxCount: Int, inboxOpen: Boolean, onBell: () -> Unit) {
    val s = vm.core
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GameStat(Icons.Rounded.LocalFireDepartment, "${vm.streak}", Act.TIME.face)
        Spacer(Modifier.width(8.dp))
        GameBar(
            if (s == null || s.total == 0) 0f else s.done / s.total.toFloat(),
            Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        GameStat(Icons.Rounded.ConfirmationNumber, "${vm.couponCount}", Act.PRIZE.face)
        if (inboxCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(GameSmall).clickable(onClick = onBell).semantics {
                    contentDescription = "Avisos: $inboxCount"
                }
            ) {
                GameStat(
                    if (inboxOpen) Icons.Rounded.NotificationsActive
                    else Icons.Rounded.Notifications,
                    "$inboxCount",
                    Act.STOP.face,
                )
            }
        }
    }
}

@Composable
private fun Header(vm: HomeViewModel, a: HomeActions) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            kotlinx.coroutines.delay(1000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    val mode = vm.owlMode()
    val est = vm.estado
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Zona "vacía" del cielo: doble toque = bloquear; mantener = abrir Foco.
        Column(
            Modifier.weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (vm.gestureLock) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                a.lockScreen()
                            }
                        },
                        onLongPress = { a.openFoco() },
                    )
                }
                .padding(top = 4.dp, bottom = 6.dp)
        ) {
            // v41: reloj del modo juego (Rubik, fuerte).
            Text(
                now.format(DateTimeFormatter.ofPattern("H:mm")),
                color = c.text,
                fontSize = 50.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Rubik,
                letterSpacing = (-1.4).sp,
                lineHeight = 52.sp,
            )
            Text(
                LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es"))),
                color = lerp(c.text, c.muted, .5f),
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            val e = vm.estado
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BreathingDot(e?.estado)
                val since =
                    e?.let {
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(it.desde),
                                ZoneId.systemDefault(),
                            )
                            .format(DateTimeFormatter.ofPattern("H:mm"))
                    }
                Text(
                    if (e == null) "Cargando…" else "${e.estado.label} · desde $since",
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier.padding(start = 8.dp).semantics {
                            contentDescription = (e?.estado?.label ?: "") + ". " + (e?.motivo ?: "")
                        },
                )
            }
            ModeChip(vm.careMode, vm.focusLeft, a::openModes, vm.careLeft)
            HomeText(
                "↻ Arrastra o toca al búho",
                10.sp,
                c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Búho de Foco junto al reloj.
        val c0 = LocalHome.current
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val pose = vm.owlPose
        // Vida espontánea de día: otra postura al encender la pantalla y cada 6 minutos a la vista.
        androidx.compose.runtime.DisposableEffect(Unit) {
            val r =
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(
                        c: android.content.Context?,
                        i: android.content.Intent?,
                    ) = vm.reshuffleOwl()
                }
            runCatching {
                ctx.registerReceiver(
                    r,
                    android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON),
                    android.content.Context.RECEIVER_NOT_EXPORTED,
                )
            }
            onDispose { runCatching { ctx.unregisterReceiver(r) } }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(6 * 60_000L)
                vm.reshuffleOwl()
            }
        }
        val sleeping = pose == es.foco.next.core.home.OwlState.Pose.SLEEPING
        // v41: mantener pulsado el búho abre su menú rápido.
        var owlMenu by remember { mutableStateOf(false) }
        Box {
            Owl(
                if (sleeping) es.foco.next.core.home.OwlBrain.Mode.NOCHE else mode,
                vm.owlTalk,
                onTap = {
                    // De noche no cambia de idea: duerme, y como mucho cada 45 min te da las buenas
                    // noches.
                    if (sleeping) es.foco.next.runtime.OwlWatch.nightRest(ctx) else vm.owlNext()
                },
                modifier =
                    Modifier.size(width = 196.dp, height = 164.dp).offset(x = 8.dp, y = (-4).dp),
                dark = c0.moon || c0.darkCard,
                pose = pose,
                scarf = "buho_bufanda" in vm.unlocks,
                // v40: con foto de fondo la luna ya está en la foto: el búho no dibuja otra.
                nightSky = vm.photo == null && (if (vm.themesOn) vm.theme.nightSky() else c0.moon),
                celebrateKey = vm.celebrate,
                alert = vm.urgent != null,
                crown = vm.streak >= 7,
                onHold = { owlMenu = true },
            )
            // v42: menú propio del modo juego (el desplegable del sistema salía claro y no se
            // leía).
            if (owlMenu) {
                val density = LocalDensity.current
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset =
                        androidx.compose.ui.unit.IntOffset(0, with(density) { 150.dp.roundToPx() }),
                    onDismissRequest = { owlMenu = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier.width(230.dp)
                            .clip(GameLarge)
                            .background(c0.card.copy(alpha = .98f))
                            .border(1.dp, c0.text.copy(alpha = .12f), GameLarge)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GameButton(
                            "Respirar",
                            Act.CARE,
                            Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.SelfImprovement,
                        ) {
                            owlMenu = false
                            a.breathe()
                        }
                        GameButton(
                            "Siguiente tarea",
                            Act.GO,
                            Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.PlayArrow,
                        ) {
                            owlMenu = false
                            a.openCore()
                        }
                        GameButton(
                            "¿Cómo estás?",
                            Act.GHOST,
                            Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Mood,
                        ) {
                            owlMenu = false
                            a.chooseMoodAgain()
                        }
                    }
                }
            }
        }
    }
    // Minimalista: si el búho ya trae un mensaje (banner), no repite su burbuja.
    if (vm.owlBubble && vm.owlBanner == null)
        OwlSpeech(
            mode,
            // v41: con la hora límite cerca, el búho lo dice (y apunta con su reloj naranja).
            vm.urgent?.let { "${it[0]}: ${it[1]}" } ?: vm.owlText(mode),
            // La frase de "lo bueno de hoy" vive solo en la tarjeta Hoy; aquí, métricas compactas.
            vm.owlMetrics,
            vm.owlCta(mode),
            onNext = vm::owlNext,
            onCta = { a.owlAction(mode) },
        )
}

@Composable
private fun BreathingDot(key: Any?) {
    val c = LocalHome.current
    val motion = LocalMotion.current
    // Tres respiraciones al cambiar de estado y quieto después (nada de animación infinita).
    val v = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key, motion) {
        if (!motion) return@LaunchedEffect v.snapTo(0f)
        repeat(3) {
            v.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            v.animateTo(0f, tween(1600, easing = FastOutSlowInEasing))
        }
    }
    // graphicsLayer lee el valor en la fase de dibujo: sin recomposición por fotograma.
    Box(
        Modifier.size(8.dp)
            .graphicsLayer {
                val k = v.value
                scaleX = 1f + .25f * k
                scaleY = 1f + .25f * k
                alpha = .95f - .4f * k
            }
            .clip(CircleShape)
            .background(c.accent)
    )
}

@Composable
private fun Footer(
    vm: HomeViewModel,
    a: HomeActions,
    openDrawer: (Boolean) -> Unit,
    page: Int,
    goTo: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            // Deslizar hacia arriba sobre el pie: todas las apps con el buscador.
            .draggable(
                rememberDraggableState { drag += it },
                Orientation.Vertical,
                onDragStopped = {
                    if (drag < -60f && vm.gestureDrawer) openDrawer(true)
                    drag = 0f
                },
            )
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Dock: 5 ranuras.
        var pinned = 0
        // De noche quedan solo las esenciales: se centran con tamaño fijo (misma proporción que de
        // día) en lugar de estirarse en bloques enormes.
        val nightDock = vm.night
        val gold = "dock_dorado" in vm.unlocks
        var nightEmpty = -1
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                if (nightDock) Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
                else Arrangement.spacedBy(10.dp),
        ) {
            vm.dock.forEachIndexed { i, pkg ->
                val app = if (pkg.isEmpty()) null else vm.apps.firstOrNull { it.pkg == pkg }
                val allowed = app != null && vm.dockAllowed(app.pkg)
                if (app != null && allowed) pinned++
                if (nightDock && !allowed) {
                    if (app == null && nightEmpty < 0)
                        nightEmpty = i // un hueco libre para el «+» de noche
                    return@forEachIndexed
                }
                val shape = RoundedCornerShape(16.dp)
                val tap =
                    Modifier.combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (app != null && allowed) a.openApp(app) else a.pickDock(i)
                            },
                            onLongClick = {
                                if (app != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.removeFromDock(i) // se puede deshacer unos segundos abajo
                                }
                            },
                        )
                        .semantics {
                            contentDescription =
                                app?.label ?: "Fijar una app productiva en el espacio ${i + 1}"
                        }
                if (nightDock && app != null) {
                    Column(
                        Modifier.width(64.dp).clip(shape).then(tap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(54.dp)
                                .clip(shape)
                                .background(Color.White.copy(alpha = .06f))
                                .border(
                                    if (gold) 1.5.dp else 1.dp,
                                    if (gold) GOLD_DOCK else Color.White.copy(alpha = .08f),
                                    shape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val img = vm.iconOf(app)
                            if (img != null)
                                Image(
                                    img,
                                    null,
                                    Modifier.size(28.dp),
                                    colorFilter = if (vm.dockBlend) DOCK_BLEND else null,
                                )
                        }
                        Text(
                            app.label,
                            color = c.onHill.copy(alpha = .85f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    return@forEachIndexed
                }
                // v41: ficha de juego con relieve (borde inferior oscuro); dorada si se ganó.
                Box(
                    Modifier.weight(1f)
                        .aspectRatio(1f)
                        .background(Color.Black.copy(alpha = .35f), shape)
                        .padding(bottom = 4.dp)
                        .clip(shape)
                        .background(lerp(c.card, c.onHill, .12f).copy(alpha = .92f))
                        .border(
                            if (gold) 2.dp else 1.dp,
                            if (gold) GOLD_DOCK else c.onHill.copy(alpha = .18f),
                            shape,
                        )
                        .then(tap),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app != null && allowed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val img = vm.iconOf(app)
                            if (img != null)
                                Image(
                                    img,
                                    null,
                                    Modifier.size(26.dp),
                                    colorFilter = if (vm.dockBlend) DOCK_BLEND else null,
                                )
                            Text(
                                app.label,
                                color = c.onHill,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
                            )
                        }
                    } else
                        Icon(
                            Icons.Rounded.Add,
                            null,
                            tint = c.onHill.copy(alpha = .85f),
                            modifier = Modifier.size(20.dp),
                        )
                }
            }
            // De noche, un solo «+» compacto si queda un hueco: para volver a fijar una esencial.
            if (nightDock && nightEmpty >= 0) {
                val slot = nightEmpty
                Box(
                    Modifier.size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
                        .clickable { a.pickDock(slot) }
                        .semantics { contentDescription = "Fijar una app esencial" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        null,
                        tint = c.onHill.copy(alpha = .6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Con el dock lleno (o de noche) no hace falta explicar nada: la guía solo aparece si falta
        // algo.
        val hint =
            when {
                vm.night -> null
                pinned == 0 ->
                    "Toca + para fijar hasta ${HomeViewModel.DOCK_SLOTS} apps productivas"
                pinned < HomeViewModel.DOCK_SLOTS ->
                    "$pinned/${HomeViewModel.DOCK_SLOTS} apps fijadas · mantén pulsada una para quitarla"
                else -> null
            }
        val undo = vm.dockUndo
        androidx.compose.animation.AnimatedVisibility(undo != null) {
            val label = undo?.let { u -> vm.apps.firstOrNull { it.pkg == u.second }?.label } ?: ""
            Text(
                "$label quitada · Deshacer",
                color = c.onHill,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier.padding(top = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { vm.undoDock() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        androidx.compose.animation.AnimatedVisibility(hint != null && undo == null) {
            Text(
                hint ?: "",
                color = c.onHill.copy(alpha = .9f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        // Buscador en la zona del pulgar.
        Row(
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.raised(.3f))
                .clickable { openDrawer(true) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Buscar una app…", color = c.muted, fontSize = 14.sp)
        }
        // v41: pestañas de juego (icono y nombre) en vez de tres puntos. Se sigue deslizando igual.
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 8.dp)
                .clip(GameMid)
                .background(Color.Black.copy(alpha = .25f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            for (i in 0..2) {
                val on = i == page
                val icon =
                    when (i) {
                        0 -> Icons.Rounded.Insights
                        1 -> Icons.Rounded.Home
                        else -> Icons.Rounded.Bolt
                    }
                Column(
                    Modifier.weight(1f)
                        .clip(GameSmall)
                        .background(if (on) c.card.copy(alpha = .95f) else Color.Transparent)
                        .clickable(enabled = !on) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            goTo(i)
                        }
                        .padding(vertical = 6.dp)
                        .semantics {
                            contentDescription = PAGE_NAMES[i] + if (on) " (aquí)" else ""
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        null,
                        tint = if (on) Act.GO.face else c.onHill.copy(alpha = .7f),
                        modifier = Modifier.size(20.dp),
                    )
                    GameText(
                        PAGE_NAMES[i],
                        10.5.sp,
                        if (on) Act.GO.face else c.onHill.copy(alpha = .7f),
                        FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** v31: dock dorado, ganado con 30 días seguidos al 100 %. */
private val GOLD_DOCK = Color(0xFFE2C27A)

private val PAGE_NAMES = arrayOf("Análisis", "Inicio", "Practicar")

/** "Iconos integrados en el dock": colores vivos suavizados para combinar con la foto. */
private val DOCK_BLEND =
    androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(.45f) }
    )

/** Icono del sistema a imagen (se hace una vez por app en el ViewModel). */
internal fun drawableImage(
    d: android.graphics.drawable.Drawable,
    px: Int = 96,
): androidx.compose.ui.graphics.ImageBitmap {
    val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(b)
    val old = d.copyBounds()
    d.setBounds(0, 0, px, px)
    d.draw(canvas)
    d.bounds = old
    return b.asImageBitmap()
}
