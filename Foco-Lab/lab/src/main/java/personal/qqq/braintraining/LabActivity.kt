package personal.qqq.braintraining

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.*
import personal.qqq.braintraining.design.*

class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferHighRefreshRate()
        val shortPractice = LabLaunch.isShort(intent) // Fase 2: práctica corta desde Foco
        val chest = LabLaunch.isChest(intent) // Lote C: cofre desde la pausa
        val directGame = LabLaunch.gameOf(intent) // Foco Home: reto rápido
        setContent {
            val vm: BrainViewModel = viewModel()
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            fun pref(key: String) = prefs.firstOrNull { it.key == key }?.value
            // Without a saved choice, follow the system (One UI) dark mode.
            val dark = pref("dark")?.let { it == "true" } ?: isSystemInDarkTheme()
            val amoled = pref("amoled") == "true"
            val dynamic = pref("dynamic") == "true"
            DisposableEffect(dark) {
                val style =
                    if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            BrainTheme(dark, amoled, dynamic) {
                ExperienceHost(vm) {
                    var mode by rememberSaveable {
                        mutableStateOf(
                            if (chest) "chest" else if (shortPractice) "short" else "full"
                        )
                    }
                    when (mode) {
                        "chest" -> ChestScreen(vm, { mode = "short" }) { closeShort() }
                        "short" -> ShortPracticeScreen(vm) { closeShort() }
                        else -> {
                            // Foco Home: arranca ESE juego en cuanto Room está listo (una vez).
                            var started by rememberSaveable { mutableStateOf(false) }
                            LaunchedEffect(vm.ready) {
                                if (directGame != null && vm.ready && !started) {
                                    started = true
                                    if (Games.all.any { it.id == directGame }) vm.start(directGame)
                                }
                            }
                            LiteApp(vm)
                        }
                    }
                }
            }
        }
    }

    /**
     * Fase 2: desde la pausa cierra su propia tarea (vuelve a la app de debajo); desde "Hoy" vuelve
     * a Foco.
     */
    private fun closeShort() {
        if (LabLaunch.fromPause(intent)) finishAndRemoveTask() else finish()
    }

    /**
     * Asks for the fastest mode at the current resolution (120 Hz on Galaxy S23 Ultra). The system
     * may still lower it to save battery.
     */
    private fun preferHighRefreshRate() = runCatching {
        // display can throw when Android gives the activity no display yet (e.g. multi-window
        // transitions); 120 Hz is optional.
        val d = display ?: return@runCatching
        val current = d.mode
        val best =
            d.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return@runCatching
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }
}

@Composable
fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Locale("es")), style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun SmallIcon(
    icon: ImageVector,
    description: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(icon, description, Modifier.size(21.dp), tint = tint)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
                RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Primary(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    PrimaryButton(text, onClick, enabled)
}

@Composable
fun Header(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (back != null)
            IconButton(back) { SmallIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

/**
 * One color per area so the user can orient without reading: attention blue, logic purple, math
 * orange, daily life green.
 */
fun areaColor(a: Area) =
    when (a) {
        Area.ATTENTION -> Color(0xFF5B8DE0)
        Area.LOGIC -> Color(0xFF9478D6)
        Area.MATH -> Color(0xFFE09A62)
        Area.REAL -> Color(0xFF4FAE82)
        Area.MEMORY -> Color(0xFFDB7FA2)
        Area.SPEED -> Color(0xFF45AFAF)
        Area.NETSEC -> Color(0xFFD9A93A)
        Area.ARCADE -> Color(0xFFD9578A)
    } // Fase 8: saturación reducida

fun areaIcon(a: Area) =
    when (a) {
        Area.ATTENTION -> Icons.Rounded.CenterFocusStrong
        Area.LOGIC -> Icons.Rounded.Extension
        Area.MATH -> Icons.Rounded.Calculate
        Area.REAL -> Icons.Rounded.ShoppingBasket
        Area.MEMORY -> Icons.Rounded.Psychology
        Area.SPEED -> Icons.Rounded.Bolt
        Area.NETSEC -> Icons.Rounded.Shield
        Area.ARCADE -> Icons.Rounded.SportsEsports
    }

@Composable
fun ProgressRing(fraction: Float) {
    val f by
        animateFloatAsState(
            fraction,
            if (LocalMotionEnabled.current) tween(450) else snap(),
            label = "Progreso",
        )
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(66.dp).semantics {
            contentDescription = "Progreso: ${(fraction*100).roundToInt()} por ciento"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(5.dp.toPx()))
            drawArc(color, -90f, f * 360, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("${(fraction*100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BrainApp(vm: BrainViewModel) {
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    val results by vm.results.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val eventState by vm.events.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.run?.id) {
        if (vm.run != null) {
            detail = null
            settings = false
            if (tab != 3) tab = 0
        }
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refreshEventDate()
                delay(30000)
            }
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) vm.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (vm.message != null)
        AlertDialog(
            onDismissRequest = { vm.message = null },
            title = { Text("Aviso") },
            text = { Text(vm.message!!) },
            confirmButton = { TextButton({ vm.message = null }) { Text("Entendido") } },
        )
    if (!vm.ready) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (vm.run != null) {
        Player(vm)
        return
    }
    BackHandler(settings || detail != null) {
        settings = false
        detail = null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!settings && detail == null)
                NavigationBar(
                    modifier =
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(26.dp)),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    listOf(
                            "Inicio" to Icons.Rounded.Home,
                            "Entrenar" to Icons.Rounded.GridView,
                            "Progreso" to Icons.Rounded.BarChart,
                            "Eventos" to Icons.Rounded.Event,
                        )
                        .forEachIndexed { i, (name, icon) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = {
                                    if (tab != i) {
                                        feedback(FeedbackCue.TAP)
                                        tab = i
                                    }
                                },
                                icon = { Icon(icon, name) },
                                label = { Text(name) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val route = if (settings) "settings" else detail?.let { "game:$it" } ?: "tab:$tab"
            AnimatedContent(
                route,
                transitionSpec = {
                    fadeIn(tween(if (motion) 180 else 0)) togetherWith
                        fadeOut(tween(if (motion) 100 else 0))
                },
                label = "Pantalla",
            ) { screen ->
                when {
                    screen == "settings" -> Settings(vm) { settings = false }
                    screen.startsWith("game:") -> {
                        val id = screen.removePrefix("game:")
                        GameDetail(Games.get(id), vm.level(id), { detail = null }) { vm.start(id) }
                    }
                    screen == "tab:0" ->
                        Home(
                            vm,
                            results,
                            progress,
                            goals,
                            { settings = true },
                            { tab = 1 },
                            { detail = it },
                            { tab = 3 },
                        )
                    screen == "tab:1" -> Catalog(progress) { detail = it }
                    screen == "tab:2" -> ProgressScreen(results, vm.zone, eventState.bonusXp)
                    else -> EventsScreen(vm)
                }
            }
        }
    }
}

@Composable
fun Home(
    vm: BrainViewModel,
    results: List<ResultRow>,
    progress: List<ProgressRow>,
    goals: List<GoalRow>,
    settings: () -> Unit,
    catalog: () -> Unit,
    game: (String) -> Unit,
    openEvents: () -> Unit,
) {
    val eventState by vm.events.collectAsStateWithLifecycle()
    val totals by vm.dailyTotals.collectAsStateWithLifecycle()
    val today = LocalDate.now(vm.zone)
    val time = totals[today] ?: 0L
    val target = goals.firstOrNull { it.date == today.toString() }?.goalMs ?: vm.goal * 60000L
    val fraction = (time.toFloat() / target).coerceIn(0f, 1f)
    val xp = results.sumOf { it.xp } + eventState.bonusXp
    LazyColumn(
        Modifier.fillMaxSize().testTag("home-list"),
        contentPadding = PaddingValues(18.dp, 16.dp, 18.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CognitiveMark(Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cognitive", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Un espacio para pensar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    settings,
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                ) {
                    SmallIcon(Icons.Rounded.Settings, "Ajustes")
                }
            }
        }
        item { RoutineHero(today) { vm.daily() } }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Metric(
                    "${results.size} sesiones",
                    "Tu práctica acumulada",
                    Icons.Rounded.AutoAwesome,
                    Modifier.weight(1f),
                )
                Metric(
                    "Nivel ${1+xp/200}",
                    "$xp XP de práctica",
                    Icons.Rounded.WorkspacePremium,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Algo nuevo por descubrir",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(catalog) { Text("Ver juegos") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf("orbit", "compass", "balance"), key = { it }) { id ->
                    val g = Games.get(id)
                    GameTile(
                        g,
                        progress.firstOrNull { it.game == id }?.level,
                        { game(id) },
                        Modifier.width(174.dp),
                    )
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tu objetivo de hoy", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${time/60000} de ${target/60000} minutos activos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressRing(fraction)
                }
                Text(
                    if (fraction >= 1) "Ya llegaste a tu objetivo. Puedes descansar."
                    else "Cada sesión terminada suma.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("RETOS Y RECOMPENSAS")
                        Text(
                            "Lo que aprendes, en tu día",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    SmallIcon(Icons.Rounded.Event, tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Hoy: ${eventState.daily.count{it.complete}}/3 · Semana: ${eventState.weekly.count{it.complete}}/3"
                )
                Text(
                    "${eventState.bonusXp} XP de eventos · ${eventState.badges.size} insignias",
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(openEvents, Modifier.fillMaxWidth()) {
                    Text("Ver retos y recompensas")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Explora tus habilidades",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(catalog) { Text("Ver todo") }
            }
        }
        item {
            val areas =
                listOf(Area.MATH, Area.REAL, Area.LOGIC, Area.MEMORY, Area.ATTENTION, Area.SPEED)
            BoardGrid(2, areas.size) { i ->
                val area = areas[i]
                val games = Games.all.filter { it.area == area }
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .pressClick { game(games.first().id) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    GameArt(games.first(), Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
                    Text(area.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${games.size} juegos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Panel {
                Label("DEL JUEGO A TU DÍA")
                val task = RealLifePractice.forDay(today.toEpochDay())
                Text(task.first, style = MaterialTheme.typography.titleLarge)
                Text(task.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Identifica los datos, elige una estrategia y comprueba si la respuesta tiene sentido.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Text(
                "A tu ritmo · sin conexión",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Catalog(progress: List<ProgressRow>, open: (String) -> Unit) {
    var area by rememberSaveable { mutableIntStateOf(-1) }
    val games =
        Games.all
            .filter { area == -1 || it.area.ordinal == area }
            .sortedBy {
                when (it.area) {
                    Area.MATH -> 0
                    Area.REAL -> 1
                    Area.LOGIC -> 2
                    else -> 3
                }
            }
    LazyVerticalGrid(
        GridCells.Adaptive(155.dp),
        Modifier.fillMaxSize().testTag("catalog-grid"),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Entrena a tu ritmo", "${Games.all.size} juegos para explorar · sin conexión")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(area == -1, { area = -1 }, label = { Text("Todos") })
                Area.values().forEachIndexed { i, a ->
                    FilterChip(area == i, { area = i }, label = { Text(a.title) })
                }
            }
        }
        items(games, key = { it.id }) { g ->
            GameTile(g, progress.firstOrNull { it.game == g.id }?.level, { open(g.id) })
        }
    }
}

@Composable
fun GameDetail(g: Game, level: Int, back: () -> Unit, start: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(g.area.title, back = back)
        GameArt(g, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Dificultad $level de 10")
            Text(g.title, style = MaterialTheme.typography.displaySmall)
            Text(g.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            Text("Cómo jugar", style = MaterialTheme.typography.titleLarge)
            Text(g.instructions)
            Text(
                "${Formats.of(g.id).label} · dificultad adaptativa",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Primary("Vamos a jugar", start)
        Text(
            "Puedes pausar cuando quieras. La ronda interrumpida vuelve con un reto nuevo y no cuenta como fallo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ProgressScreen(results: List<ResultRow>, zone: ZoneId, bonusXp: Int = 0) {
    var selected by rememberSaveable { mutableStateOf("math") }
    var record by remember { mutableStateOf<ResultRow?>(null) }
    val series = results.filter { it.game == selected }.take(7).reversed()
    val comparison = Evaluation.compare(results.filter { it.game == selected }.map { it.sample() })
    if (record != null) {
        val r = record!!
        AlertDialog(
            onDismissRequest = { record = null },
            title = { Text(Games.get(r.game).title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val h = headline(r)
                    Text(
                        "${h.value}/${h.total} ${h.noun}\nDificultad: ${r.level} → ${r.nextLevel}\nTiempo activo: ${r.activeMs/1000} s\nExperiencia: +${r.xp} XP\n${r.medianMs?.let{"Mediana: $it ms"}?:"Sin suficientes tiempos correctos para una mediana."}"
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(r.evaluationText())
                }
            },
            confirmButton = { TextButton({ record = null }) { Text("Cerrar") } },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 18.dp, 22.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Header("Cada paso cuenta", "Tu progreso se queda contigo") }
        item {
            Panel {
                Label("TU RECORRIDO")
                Text(
                    "${results.size} entrenamientos",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "${results.sumOf{it.activeMs}/60000} minutos activos · ${results.sumOf{it.xp}+bonusXp} XP",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "Incluye $bonusXp XP de retos y aplicaciones registradas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text("Evolución por juego", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Games.all.forEach { g ->
                    FilterChip(selected == g.id, { selected = g.id }, label = { Text(g.title) })
                }
            }
        }
        item {
            Panel {
                Text(Games.get(selected).title, style = MaterialTheme.typography.titleMedium)
                if (series.isEmpty())
                    Text(
                        "Tu primera sesión dibujará el inicio de esta historia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else {
                    Row(
                        Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        series.forEach { r ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(
                                    "${r.correct*100/r.total}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(
                                            (r.correct.toFloat() / r.total * 90)
                                                .coerceAtLeast(3f)
                                                .dp
                                        )
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("N${r.level}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        "Últimos ${series.size} bloques · N = dificultad. Compara resultados del mismo nivel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Panel {
                Text("Comparación del mismo nivel", style = MaterialTheme.typography.titleMedium)
                val c = comparison
                if (c == null)
                    Text(
                        "Completa 6 sesiones del mismo juego, nivel y modo para comparar dos grupos de 3. Las sesiones anteriores sin modo registrado se conservan en el historial.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else {
                    Text(
                        "Nivel ${c.level} · ${if(c.mode=="untimed")"sin cronómetro" else "con cronómetro"}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Primeras 3: ${c.beforePercent.roundToInt()}% · Últimas 3: ${c.afterPercent.roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Cambio: ${if(c.changePoints>0)"+" else ""}${c.changePoints.roundToInt()} puntos porcentuales. Describe estos ejercicios; no demuestra una mejora general de cognición.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Panel {
                Text("Tu nivel de práctica", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Los niveles y XP reflejan tu entrenamiento en estos juegos. No miden tu CI ni son una evaluación clínica.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Text("Historial", style = MaterialTheme.typography.titleLarge) }
        if (results.isEmpty())
            item {
                Text(
                    "Todavía no hay sesiones. Empieza con un juego en Entrenar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(results.take(100), key = { it.id }) { r ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pressClick { record = r }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Games.get(r.game).title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Instant.ofEpochMilli(r.ended)
                            .atZone(zone)
                            .format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("es"))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${r.correct}/${r.total}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
