package personal.qqq.braintraining

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.random.Random
import kotlinx.coroutines.delay
import personal.qqq.braintraining.core.*

/**
 * Content never grows wider than a comfortable column (DeX, landscape, split screen and tablets).
 */
@Composable
private fun Centered(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 640.dp).fillMaxSize(), content = content)
    }
}

private fun depth(page: String) =
    when (page) {
        "home" -> 0
        "settings",
        "catalog",
        "setup",
        "netsec",
        "album",
        "short" -> 1
        else -> 2
    }

@Composable
fun LiteApp(vm: BrainViewModel) {
    var page by rememberSaveable { mutableStateOf("home") }
    var infinite by rememberSaveable { mutableStateOf(false) }
    var difficultyName by rememberSaveable { mutableStateOf(Difficulty.EASY.name) }
    var topicName by rememberSaveable { mutableStateOf(PairTopic.MATH.name) }
    var netMode by rememberSaveable { mutableStateOf(NetMode.NORMAL.name) }
    var flashGame by rememberSaveable { mutableStateOf(NetSec.ids.first()) }
    // Lote C: "Otra vez" restarts the session page with a new key; leaving the page resets the
    // count.
    var runKey by rememberSaveable { mutableIntStateOf(0) }
    var againRow by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(page) { if (page !in listOf("pair", "pulse", "meteor", "flash")) againRow = 0 }
    fun openMission(m: DailyMission) {
        when (m.kind) {
            MissionKind.FLASH -> {
                flashGame = m.game
                page = "flash"
            }
            MissionKind.SHORT -> page = "short"
            else -> vm.start(m.game)
        }
    }
    val motion = LocalMotionEnabled.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.pause() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
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
    // Entering a game rises and fades in; leaving it sinks and fades out. A plain cut when
    // animations are off.
    AnimatedContent(
        vm.run != null,
        transitionSpec = {
            if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
            else if (targetState)
                (slideInVertically(tween(300)) { it / 10 } + fadeIn(tween(240))) togetherWith
                    fadeOut(tween(180))
            else
                fadeIn(tween(260, delayMillis = 60)) togetherWith
                    (slideOutVertically(tween(260)) { it / 10 } + fadeOut(tween(200)))
        },
        label = "Partida",
    ) { playing ->
        if (playing) {
            Player(vm)
            return@AnimatedContent
        }
        BackHandler(page != "home" && page != "short") {
            page =
                when (page) {
                    "pair",
                    "meteor",
                    "pulse" -> "setup"
                    "flash" -> "netsec"
                    else -> "home"
                }
        }
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { p ->
            Box(Modifier.padding(p)) {
                // Forward navigation slides in from the right, going back slides from the left.
                AnimatedContent(
                    page,
                    transitionSpec = {
                        if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                        else {
                            val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
                            (slideInHorizontally(tween(260)) { it * dir / 6 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it * dir / 6 } +
                                    fadeOut(tween(160)))
                        }
                    },
                    label = "Página",
                ) { current ->
                    Centered {
                        CompositionLocalProvider(
                            LocalAgain provides
                                AgainHook(againRow) {
                                    againRow++
                                    runKey++
                                }
                        ) {
                            key(runKey) {
                                when (current) {
                                    "home" ->
                                        LiteHome(
                                            vm,
                                            { page = "setup" },
                                            { page = "catalog" },
                                            { page = "settings" },
                                            { page = "netsec" },
                                            { page = "album" },
                                            ::openMission,
                                        )
                                    "settings" -> LiteSettings(vm) { page = "home" }
                                    "catalog" ->
                                        LiteCatalog(
                                            mindGames(vm.showClassic),
                                            infinite,
                                            { infinite = it },
                                            { page = "home" },
                                        ) {
                                            vm.start(it, endlessMode = infinite)
                                        }
                                    "setup" ->
                                        GameSetup(
                                            vm,
                                            infinite,
                                            { infinite = it },
                                            Difficulty.valueOf(difficultyName),
                                            { difficultyName = it.name },
                                            PairTopic.valueOf(topicName),
                                            { topicName = it.name },
                                            { page = "home" },
                                            { page = it },
                                        )
                                    "pair" ->
                                        PairSession(
                                            vm,
                                            Difficulty.valueOf(difficultyName),
                                            PairTopic.valueOf(topicName),
                                            infinite,
                                        ) {
                                            page = "setup"
                                        }
                                    "pulse" ->
                                        ArcadeSession(vm, "pulse", infinite) { page = "setup" }
                                    "meteor" ->
                                        ArcadeSession(vm, "meteor", infinite) { page = "setup" }
                                    "netsec" ->
                                        NetsecPage(
                                            vm,
                                            NetMode.valueOf(netMode),
                                            { netMode = it.name },
                                            { page = "home" },
                                        ) { id ->
                                            when (NetMode.valueOf(netMode)) {
                                                NetMode.NORMAL -> vm.start(id)
                                                NetMode.UNTIMED -> vm.start(id, endlessMode = true)
                                                NetMode.FLASH -> {
                                                    flashGame = id
                                                    page = "flash"
                                                }
                                            }
                                        }
                                    "flash" -> FlashSession(vm, flashGame) { page = "netsec" }
                                    "album" -> AlbumPage(vm) { page = "home" }
                                    "short" -> ShortPracticeScreen(vm) { page = "home" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One entry per destination: Misiones, Redes, Mente and Arcade (docs/EXAMEN-JUEGOS.md, section 5).
 */
@Composable
private fun LiteHome(
    vm: BrainViewModel,
    arcade: () -> Unit,
    mind: () -> Unit,
    settings: () -> Unit,
    netsec: () -> Unit,
    album: () -> Unit,
    mission: (DailyMission) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CognitiveMark(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Cognitive Lab", style = MaterialTheme.typography.headlineLarge)
                Text("Piensa, juega y descubre", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                settings,
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Rounded.Settings, "Ajustes")
            }
        }
        val prefs by vm.preferences.collectAsStateWithLifecycle()
        HeroCard(remember(prefs) { Rewards.cosmetic(vm.wallet.accent)?.let { Color(it.color) } })
        EnergyCheckIn(vm)
        StreakRow(vm)
        if (vm.energy != Energy.LOW) DailyTrainingCard(vm, mission)
        NetsecHomeCard(vm, netsec)
        AlbumEntry(vm, album)
        Text("Explora", style = MaterialTheme.typography.titleLarge)
        LiteCard(
            "Mente",
            "${mindGames(vm.showClassic).size} juegos: " +
                mindGames(vm.showClassic).take(3).joinToString(", ") { it.title } +
                if (vm.showClassic) "…" else ".",
            Icons.Rounded.Psychology,
            Color(0xFF3567E4),
            mind,
        )
        LiteCard(
            "Arcade",
            "${ARCADE_GAMES.size} juegos: " +
                ARCADE_LAB_GAMES.joinToString(" y ") { it.title } +
                if (vm.showClassic) " · clásicos: Parejas y Pulso." else ".",
            Icons.Rounded.SportsEsports,
            Color(0xFF167F68),
            arcade,
        )
        GardenCard(vm)
        WeeklyChart(vm)
        Text(
            "A tu ritmo · sin conexión",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dark gradient hero with slowly drifting glows (static when animations are off). */
@Composable
private fun HeroCard(accent: Color? = null) {
    val motion = LocalMotionEnabled.current
    val drift =
        if (motion) {
            val t = rememberInfiniteTransition(label = "Fondo")
            t.animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "Brillo",
                )
                .value
        } else .5f
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF151B2E), Color(0xFF0D1018))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf((accent ?: Color(0xFF3667FF)).copy(alpha = .55f), Color.Transparent),
                        Offset(size.width * (.75f + .15f * drift), size.height * .15f),
                        size.minDimension * .7f,
                    ),
                    size.minDimension * .7f,
                    Offset(size.width * (.75f + .15f * drift), size.height * .15f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF1EDB90).copy(alpha = .30f), Color.Transparent),
                        Offset(size.width * (.1f + .1f * drift), size.height),
                        size.minDimension * .6f,
                    ),
                    size.minDimension * .6f,
                    Offset(size.width * (.1f + .1f * drift), size.height),
                )
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Label("TU ESPACIO DE JUEGO", Color(0xFFBAC5DA))
        Text(
            "Activa tu\ncuriosidad.",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            "Conecta ideas, practica a tu ritmo y descubre nuevos juegos.",
            color = Color(0xFFC5CEDF),
        )
    }
}

@Composable
private fun LiteCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    click: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                RoundedCornerShape(24.dp),
            )
            .pressClick(onClick = click)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            color,
                            color.copy(
                                red = color.red * .7f,
                                green = color.green * .7f,
                                blue = color.blue * .8f,
                            ),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(29.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiteCatalog(
    games: List<Game>,
    infinite: Boolean,
    setInfinite: (Boolean) -> Unit,
    back: () -> Unit,
    open: (String) -> Unit,
) {
    LazyVerticalGrid(
        GridCells.Adaptive(155.dp),
        Modifier.fillMaxSize().testTag("lite-catalog"),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Mente", "Memoria de trabajo y atención con redes", back)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ModeSelector(infinite, setInfinite, "Infinito · sin tiempo")
        }
        items(games, key = { it.id }) { g -> GameTile(g, null, { open(g.id) }, endless = infinite) }
    }
}

/** Two-option segmented control (timed / endless). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    infinite: Boolean,
    setInfinite: (Boolean) -> Unit,
    endlessLabel: String = "Infinito",
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            !infinite,
            { setInfinite(false) },
            SegmentedButtonDefaults.itemShape(0, 2),
            icon = { Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)) },
        ) {
            Text("Con tiempo")
        }
        SegmentedButton(
            infinite,
            { setInfinite(true) },
            SegmentedButtonDefaults.itemShape(1, 2),
            icon = { Icon(Icons.Rounded.AllInclusive, null, Modifier.size(18.dp)) },
        ) {
            Text(endlessLabel)
        }
    }
}

@Composable
private fun LiteSettings(vm: BrainViewModel, back: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    fun value(key: String, default: Boolean = true) =
        prefs.firstOrNull { it.key == key }?.value?.let { it == "true" } ?: default
    val dark = value("dark", isSystemInDarkTheme())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header("A tu manera", back = back)
        Label("APARIENCIA")
        Panel {
            LiteSwitch(
                Icons.Rounded.DarkMode,
                "Modo oscuro",
                "Elige el aspecto de la aplicación.",
                dark,
            ) {
                vm.pref("dark", it.toString())
            }
            HorizontalDivider()
            LiteSwitch(
                Icons.Rounded.Contrast,
                "Negro puro",
                "Fondo negro en modo oscuro: ahorra batería en pantallas AMOLED.",
                value("amoled", false),
                enabled = dark,
            ) {
                vm.pref("amoled", it.toString())
            }
            HorizontalDivider()
            LiteSwitch(
                Icons.Rounded.Palette,
                "Colores del fondo de pantalla",
                "Usa la paleta de colores del sistema (Material You).",
                value("dynamic", false),
            ) {
                vm.pref("dynamic", it.toString())
            }
        }
        Label("AL JUGAR")
        Panel {
            LiteSwitch(
                Icons.Rounded.VolumeUp,
                "Sonidos",
                "Toques y respuesta al jugar.",
                value("sound"),
            ) {
                vm.pref("sound", it.toString())
            }
            HorizontalDivider()
            LiteSwitch(
                Icons.Rounded.Vibration,
                "Vibraciones",
                "Respuesta táctil según tu dispositivo.",
                value("haptics"),
            ) {
                vm.pref("haptics", it.toString())
            }
            HorizontalDivider()
            LiteSwitch(
                Icons.Rounded.Animation,
                "Animaciones",
                "Movimiento suave de las tarjetas y botones.",
                value("motion"),
            ) {
                vm.pref("motion", it.toString())
            }
        }
        Label("JUEGOS")
        Panel {
            LiteSwitch(
                Icons.Rounded.History,
                "Mostrar ejercicios clásicos",
                "Vuelve a mostrar los ejercicios retirados del menú: en Mente (Órbitas, Destellos, Cálculo, Transforma…) y en Arcade (Pulso y Parejas).",
                value("classic", false),
            ) {
                vm.pref("classic", it.toString())
            }
        }
        Text(
            "Cognitive Lab · edición de prueba",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiteSwitch(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    change: (Boolean) -> Unit,
) {
    val fade = if (enabled) 1f else .45f
    Row(
        Modifier.graphicsLayer { alpha = fade },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked,
            change,
            Modifier.semantics { contentDescription = title },
            enabled = enabled,
        )
    }
}

@Composable
private fun GameSetup(
    vm: BrainViewModel,
    infinite: Boolean,
    setInfinite: (Boolean) -> Unit,
    difficulty: Difficulty,
    setDifficulty: (Difficulty) -> Unit,
    topic: PairTopic,
    setTopic: (PairTopic) -> Unit,
    back: () -> Unit,
    start: (String) -> Unit,
) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    fun record(game: String) = prefs.let { vm.arcadeBest(game) }?.let { " Récord: $it." } ?: ""
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("Arcade", "Turnos rápidos de redes y seguridad", back)
        Panel {
            Text("Modo de juego", style = MaterialTheme.typography.titleLarge)
            ModeSelector(infinite, setInfinite)
            Text(
                if (infinite) "Sin cronómetro: juega hasta que decidas terminar."
                else
                    "Anti-DDoS: 5 oleadas seguidas (unos 3 min). Parejas: 12 tableros. El nivel se adapta a ti.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Fase 3 (docs/DISENO-JUEGOS-V2.md §0): Turno anti-DDoS replaces Pulso and Parejas de
        // incidentes replaces Parejas.
        LiteCard(
            "Turno anti-DDoS",
            "Bloquea los paquetes maliciosos al ritmo de la cinta: 5 oleadas, regla nueva en la tercera y pico final. Nivel ${vm.level(Ddos.ID)}.",
            Icons.Rounded.Security,
            Color(0xFFB0306A),
        ) {
            vm.start(Ddos.ID, endlessMode = infinite)
        }
        LiteCard(
            "Parejas de incidentes",
            "Ataque ↔ defensa, alerta ↔ causa, puerto ↔ servicio. Nivel ${vm.level(IncidentPairs.ID)}.",
            Icons.Rounded.GridView,
            Color(0xFF5A3ED4),
        ) {
            vm.start(IncidentPairs.ID, endlessMode = infinite)
        }
        // Read from the collected preferences so the section appears as soon as the setting
        // changes.
        if (prefs.any { it.key == "classic" && it.value == "true" }) {
            // The retired sessions come back with "Mostrar ejercicios clásicos"; their records
            // ("arcade:pair", "arcade:pulse") stay readable.
            Label("CLÁSICOS")
            Panel {
                Text("Dificultad de parejas", style = MaterialTheme.typography.titleMedium)
                val check: @Composable () -> Unit = {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                            Difficulty.EASY to "Suave",
                            Difficulty.MEDIUM to "Media",
                            Difficulty.HARD to "Intensa",
                        )
                        .forEach { (v, label) ->
                            FilterChip(
                                difficulty == v,
                                { setDifficulty(v) },
                                label = { Text(label) },
                                leadingIcon = if (difficulty == v) check else null,
                            )
                        }
                }
                Text("Contenido de parejas", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PairTopic.MATH to "Matemáticas", PairTopic.NET to "Redes").forEach {
                        (v, label) ->
                        FilterChip(
                            topic == v,
                            { setTopic(v) },
                            label = { Text(label) },
                            leadingIcon = if (topic == v) check else null,
                        )
                    }
                }
            }
            LiteCard(
                pairTitle(topic),
                (if (topic == PairTopic.NET) "Une puertos con servicios y prefijos con máscaras."
                else "Une una operación con su resultado.") +
                    " Cada acierto mueve las cartas restantes." +
                    record(pairKey(topic)),
                Icons.Rounded.GridView,
                Color(0xFF5A3ED4),
            ) {
                start("pair")
            }
            LiteCard(
                "Pulso",
                "Toca la luz antes de que cambie de lugar. Si se enciende en ámbar, espera la señal." +
                    record("pulse"),
                Icons.Rounded.Bolt,
                Color(0xFF167F68),
            ) {
                start("pulse")
            }
        }
    }
}

/**
 * "Mente" section: visible games outside Redes and Arcade (La maleta del técnico, Vigía de alertas,
 * Ruta de paquetes; Fase 3).
 */
internal val MIND_GAMES
    get() = Games.visible.filter { it.area != Area.NETSEC && it.area != Area.ARCADE }
/** Arcade games played in the 12-round flow (Fase 3): Turno anti-DDoS and Parejas de incidentes. */
internal val ARCADE_LAB_GAMES
    get() = Games.visible.filter { it.area == Area.ARCADE }

/** Mente, plus the retired exercises when "Mostrar ejercicios clásicos" is on (Ajustes, Lote B). */
internal fun mindGames(classic: Boolean) =
    if (classic) Games.all.filter { it.area != Area.NETSEC && it.area != Area.ARCADE }
    else MIND_GAMES

internal fun pairTitle(topic: PairTopic) =
    if (topic == PairTopic.NET) "Parejas de redes" else "Parejas matemáticas"

/** History key: arithmetic and networking decks keep separate records. */
internal fun pairKey(topic: PairTopic) = if (topic == PairTopic.NET) "pair:net" else "pair"

/**
 * Saves an arcade session once (Lote B): when the timer ends, or on leaving an endless session with
 * some score. Returns the function that saves; [score] and [extra] are read when it runs.
 */
@Composable
internal fun rememberArcadeSave(
    vm: BrainViewModel,
    game: String,
    infinite: Boolean,
    score: () -> Int,
    extra: () -> Map<String, Any> = { emptyMap() },
): () -> Unit {
    val saved = remember { mutableStateOf(false) }
    val current by rememberUpdatedState(score)
    val more by rememberUpdatedState(extra)
    val save = remember {
        {
            if (!saved.value && current() > 0) {
                saved.value = true
                vm.saveArcade(game, current(), if (infinite) 0L else SESSION_MS.toLong(), more())
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (infinite) save() } }
    return save
}

/**
 * Arcade menu (Fase 3): Turno anti-DDoS and Parejas de incidentes, both in [Games] (12-round flow,
 * levels 1–10). Ruta espacial ("meteor", Fase 2), Pulso ("pulse") and Parejas matemáticas/de redes
 * ("pair") left the menu; their saved history ("arcade:<game>", "meteor_best") is kept and still
 * readable, and the last two return with "Mostrar ejercicios clásicos".
 */
internal val ARCADE_GAMES
    get() = ARCADE_LAB_GAMES.map { it.id }
internal val CLASSIC_ARCADE = listOf("pair", "pulse")
internal val RETIRED_ARCADE = setOf("meteor", "pair", "pulse")
internal const val SESSION_MS = 60_000

internal class SessionClock(
    val running: Boolean,
    val toggle: () -> Unit,
    val seconds: Int,
    val fraction: Float,
)

/** Ends a timed session using active elapsed time; pauses explicitly on backgrounding. */
@Composable
internal fun rememberSession(infinite: Boolean): SessionClock {
    var running by remember { mutableStateOf(true) }
    var left by remember { mutableIntStateOf(SESSION_MS) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) running = false }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(running, infinite) {
        if (running && !infinite) {
            var previous = SystemClock.elapsedRealtime()
            while (left > 0) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                left =
                    (left - (now - previous).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        .coerceAtLeast(0)
                previous = now
            }
            running = false
        }
    }
    return SessionClock(
        running,
        { if (infinite || left > 0) running = !running },
        if (infinite) -1 else (left + 999) / 1000,
        left / SESSION_MS.toFloat(),
    )
}

@Composable
internal fun SessionHeader(title: String, clock: SessionClock, score: String, back: () -> Unit) {
    val seconds = clock.seconds
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.Rounded.Close, "Salir") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (seconds < 0) "MODO INFINITO" else "${seconds} s",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (seconds in 1..10) colors.error else colors.primary,
                )
            }
            Text(
                score,
                Modifier.clip(CircleShape)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer,
            )
            if (seconds != 0)
                IconButton(clock.toggle) {
                    Icon(
                        if (clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (clock.running) "Pausar" else "Continuar",
                    )
                }
        }
        if (seconds >= 0) {
            val f by
                animateFloatAsState(
                    clock.fraction,
                    if (LocalMotionEnabled.current) tween(120, easing = LinearEasing) else snap(),
                    label = "Tiempo",
                )
            LinearProgressIndicator(
                { f },
                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (seconds in 1..10) colors.error else colors.primary,
                trackColor = colors.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

/** Shown in place of a session when its time ends. */
/**
 * Lote C: "Otra vez" for the page shown (LiteApp restarts it with a new key); null outside LiteApp.
 */
internal class AgainHook(val inRow: Int, val again: () -> Unit)

internal val LocalAgain = staticCompositionLocalOf<AgainHook?> { null }

@Composable
internal fun SessionSummary(
    score: String,
    detail: String,
    back: () -> Unit,
    record: Boolean = false,
    best: Int? = null,
    value: Int = 0,
    unit: String = "puntos",
    extra: @Composable () -> Unit = {},
) {
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    LaunchedEffect(Unit) {
        feedback(FeedbackCue.COMPLETE)
        if (record) {
            delay(420)
            feedback(FeedbackCue.LEVEL_UP)
        }
    }
    // New record (Lote B): a badge that pops in and confetti; static badge with animations off.
    val badge = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(record) {
        if (record && motion) {
            delay(300)
            badge.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f))
        }
    }
    Box {
        Panel {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompletionMark()
                Text("Tiempo terminado", style = MaterialTheme.typography.headlineMedium)
                Text(
                    score,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (record)
                    Row(
                        Modifier.graphicsLayer {
                                val k = .4f + .6f * badge.value
                                scaleX = k
                                scaleY = k
                                alpha = badge.value
                            }
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            null,
                            Modifier.size(20.dp),
                            tint = Color(0xFF2B1D00),
                        )
                        Text(
                            "¡Nuevo récord!",
                            color = Color(0xFF2B1D00),
                            fontWeight = FontWeight.Black,
                        )
                    }
                Text(
                    detail,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LocalAgain.current?.let { h -> AgainOffer(value, best, h.inRow, unit, h.again) }
            extra()
            Primary("Volver a elegir", back)
        }
        CelebrationBurst(if (record) score else null, Modifier.matchParentSize())
    }
}

@Composable
internal fun PausedBanner(resume: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            "Pausa · pulsa continuar para seguir",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FilledTonalButton(resume) { Text("Continuar") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PairSession(
    vm: BrainViewModel,
    difficulty: Difficulty,
    topic: PairTopic,
    infinite: Boolean,
    back: () -> Unit,
) {
    val clock = rememberSession(infinite)
    val running = clock.running
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    var deck by remember {
        mutableStateOf(PairGame.newDeck(difficulty, Random.Default, topic = topic))
    }
    val key = pairKey(topic)
    val previousBest = remember { vm.arcadeBest(key) ?: 0 }
    var misses by remember { mutableIntStateOf(0) }
    val save =
        rememberArcadeSave(vm, key, infinite, { deck.matches }) {
            mapOf("difficulty" to difficulty.name, "misses" to misses)
        }
    LaunchedEffect(clock.seconds == 0) { if (clock.seconds == 0) save() }
    var selected by remember { mutableStateOf<PairCard?>(null) }
    var second by remember { mutableStateOf<PairCard?>(null) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    // true = the two chosen cards match, false = they don't, null = still choosing.
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(second, running) {
        val b = second
        val a = selected
        if (b != null && a != null && running) {
            verdict = a.pair == b.pair
            delay(if (a.pair == b.pair) 260 else 600)
            val next = deck.selectMatch(a.id, b.id, Random.Default)
            if (next != null) {
                feedback(FeedbackCue.CORRECT)
                deck =
                    if (next.left.isEmpty())
                        PairGame.newDeck(difficulty, Random.Default, next.matches, topic)
                    else next
                status = "¡Bien! Las cartas cambiaron de lugar."
            } else {
                misses++
                feedback(FeedbackCue.RETRY)
                status = "Prueba otra combinación."
            }
            selected = null
            second = null
            pending = false
            verdict = null
        }
    }
    fun choose(card: PairCard) {
        if (!running || pending) return
        val first = selected
        if (first == null || first.left == card.left) {
            selected = if (first?.id == card.id) null else card
            feedback(FeedbackCue.TAP)
        } else {
            second = card
            pending = true
        }
    }
    val score = "${deck.matches} ${if(deck.matches==1)"pareja" else "parejas"}"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val net = topic == PairTopic.NET
        SessionHeader(pairTitle(topic), clock, "${deck.matches} ✓", back)
        if (clock.seconds == 0) {
            SessionSummary(
                "${deck.matches}",
                (if (deck.matches == 1) "pareja resuelta" else "parejas resueltas") +
                    " · $misses ${if(misses==1)"intento fallido" else "intentos fallidos"}\n" +
                    if (deck.matches > previousBest && previousBest > 0)
                        "Récord anterior: $previousBest"
                    else "Récord: ${maxOf(previousBest,deck.matches)}",
                back,
                record = deck.matches > previousBest && deck.matches > 0,
                best = previousBest,
                value = deck.matches,
                unit = "parejas",
            )
            return@Column
        }
        if (!running) PausedBanner(clock.toggle)
        else
            Text(
                if (net) "Une cada puerto o prefijo con su pareja."
                else "Une cada operación con su resultado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(if (net) "Concepto" else "Operación")
            Label(if (net) "Pareja" else "Resultado")
        }
        Row(
            Modifier.fillMaxWidth().height(336.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(deck.left, deck.right).forEach { cards ->
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    // With one card left, move it to a free row too; otherwise a card already in
                    // the first row would not move.
                    if (cards.size == 1)
                        item(key = "espacio-final") { Spacer(Modifier.height(154.dp)) }
                    items(cards, key = { it.id }) { card ->
                        val active = card.id == selected?.id || card.id == second?.id
                        val colors = MaterialTheme.colorScheme
                        val targetBorder =
                            when {
                                !active -> colors.outlineVariant
                                verdict == true -> colors.secondary
                                verdict == false -> colors.error
                                else -> colors.primary
                            }
                        val targetFill =
                            when {
                                !active -> colors.surface
                                verdict == true -> colors.secondaryContainer
                                verdict == false -> colors.errorContainer
                                else -> colors.primaryContainer
                            }
                        val border by
                            animateColorAsState(
                                targetBorder,
                                if (motion) tween(160) else snap(),
                                label = "Borde",
                            )
                        val fill by
                            animateColorAsState(
                                targetFill,
                                if (motion) tween(160) else snap(),
                                label = "Relleno",
                            )
                        // Short horizontal shake on a wrong pair.
                        val shake = remember { Animatable(0f) }
                        LaunchedEffect(active && verdict == false) {
                            if (active && verdict == false && motion) {
                                for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(
                                    x,
                                    tween(45),
                                )
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth()
                                .height(72.dp)
                                .then(
                                    if (motion)
                                        Modifier.animateItemPlacement(
                                            spring(dampingRatio = .8f, stiffness = 380f)
                                        )
                                    else Modifier
                                )
                                .graphicsLayer { translationX = shake.value * density }
                                .clip(RoundedCornerShape(20.dp))
                                .background(fill)
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    border,
                                    RoundedCornerShape(20.dp),
                                )
                                .pressClick(
                                    enabled = running && !pending,
                                    feedbackOnClick = false,
                                ) {
                                    choose(card)
                                }
                                .semantics {
                                    contentDescription =
                                        (if (card.left) (if (net) "Concepto " else "Operación ")
                                        else (if (net) "Pareja " else "Resultado ")) + card.text
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                card.text,
                                Modifier.padding(horizontal = 6.dp),
                                style =
                                    if (net && card.text.length > 9)
                                        MaterialTheme.typography.titleSmall
                                    else MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        Text(
            if (status.isEmpty()) "Elige una carta de cada columna." else status,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "$score en esta partida",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArcadeSession(vm: BrainViewModel, game: String, infinite: Boolean, back: () -> Unit) {
    if (game == "pulse") PulseSession(vm, infinite, back) else MeteorSession(vm, infinite, back)
}

/**
 * Pulse gets faster as you hit: the light moves sooner every 4 hits, down to 450 ms. From the third
 * hit some lights start amber ("espera la señal"): touching them before they turn green is an early
 * start. A light that escapes leaves a red mark.
 */
@Composable
private fun PulseSession(vm: BrainViewModel, infinite: Boolean, back: () -> Unit) {
    val clock = rememberSession(infinite)
    val running = clock.running
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    var target by remember { mutableIntStateOf(Random.nextInt(9)) }
    var hits by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var ripple by remember { mutableIntStateOf(-1) }
    val rippleAnim = remember { Animatable(1f) }
    var waiting by remember { mutableStateOf(false) }
    var early by remember { mutableIntStateOf(0) }
    var escaped by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(-1) }
    var missTick by remember { mutableIntStateOf(0) }
    val missAnim = remember { Animatable(1f) }
    val previousBest = remember { vm.arcadeBest("pulse") ?: 0 }
    val save =
        rememberArcadeSave(vm, "pulse", infinite, { hits }) {
            mapOf("bestStreak" to best, "early" to early, "escaped" to escaped)
        }
    LaunchedEffect(clock.seconds == 0) { if (clock.seconds == 0) save() }
    // Ritmo suave: sube con aciertos y baja tras 2 fallos seguidos (antes solo subía).
    var soft by remember { mutableStateOf(SoftPace()) }
    val pace = soft.ms
    fun advance() {
        target = (target + Random.nextInt(1, 9)) % 9
        waiting = hits >= 3 && Random.nextInt(4) == 0
    }
    fun lost(i: Int) {
        missed = i
        missTick++
    }
    LaunchedEffect(running, target, pace, waiting) {
        if (running) {
            if (waiting) {
                delay(Random.nextLong(700, 1600))
                waiting = false
            } else {
                delay(pace.toLong())
                streak = 0
                soft = soft.miss()
                escaped++
                lost(target)
                advance()
            }
        }
    }
    LaunchedEffect(ripple, hits) {
        if (ripple >= 0 && motion) {
            rippleAnim.snapTo(0f)
            rippleAnim.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(missTick) {
        if (missTick > 0 && motion) {
            missAnim.snapTo(0f)
            missAnim.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionHeader("Pulso", clock, "$hits ⚡", back)
        if (clock.seconds == 0) {
            SessionSummary(
                "$hits",
                (if (hits == 1) "luz tocada" else "luces tocadas") +
                    " · mejor serie $best · $early ${if(early==1)"salida anticipada" else "salidas anticipadas"}\n" +
                    if (hits > previousBest && previousBest > 0) "Récord anterior: $previousBest"
                    else "Récord: ${maxOf(previousBest,hits)}",
                back,
                record = hits > previousBest && hits > 0,
                best = previousBest,
                value = hits,
                unit = "luces",
            )
            return@Column
        }
        if (!running) PausedBanner(clock.toggle)
        else
            Text(
                if (waiting) "Espera la señal: tócala cuando se ponga verde."
                else "Toca la luz antes de que cambie.",
                color =
                    if (waiting) Color(0xFFC88A00) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Ritmo ${1 + soft.level}", Icons.Rounded.Speed, Modifier.weight(1f))
            StatChip("Luces $hits", Icons.Rounded.Spa, Modifier.weight(1f))
        }
        val glow =
            if (motion) {
                val t = rememberInfiniteTransition(label = "Pulso")
                t.animateFloat(
                        .9f,
                        1f,
                        infiniteRepeatable(
                            tween(420, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse,
                        ),
                        label = "Escala",
                    )
                    .value
            } else 1f
        BoardGrid(3, 9) { i ->
            val on = i == target
            val hold = on && waiting
            val light = if (hold) Color(0xFFFFB300) else Color(0xFF1EDB90)
            val bg by
                animateColorAsState(
                    if (on) light else MaterialTheme.colorScheme.surfaceVariant,
                    if (motion) tween(140) else snap(),
                    label = "Casilla",
                )
            val rippleColor = Color(0xFF1EDB90)
            val missColor = Color(0xFFFF5C7A)
            // Escaped light or early touch: a red ring that fades (static with animations off,
            // until the next hit).
            val missAlpha = if (i != missed) 0f else if (motion) (1f - missAnim.value) else 1f
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .drawBehind {
                        if (i == ripple && rippleAnim.value < 1f) {
                            val r = size.minDimension * (.5f + rippleAnim.value * .6f)
                            drawCircle(
                                rippleColor.copy(alpha = (1f - rippleAnim.value) * .7f),
                                r,
                                style =
                                    androidx.compose.ui.graphics.drawscope.Stroke(
                                        6f * (1f - rippleAnim.value) + 1f
                                    ),
                            )
                        }
                        if (missAlpha > 0f)
                            drawCircle(
                                missColor.copy(alpha = missAlpha * .8f),
                                size.minDimension *
                                    (.42f + (if (motion) missAnim.value * .12f else 0f)),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()),
                            )
                    }
                    .graphicsLayer {
                        val s = if (on && !hold) glow else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .then(
                        if (on)
                            Modifier.drawBehind {
                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(light.copy(alpha = .45f), Color.Transparent)
                                    ),
                                    size.minDimension * .75f,
                                )
                            }
                        else Modifier
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (on)
                            Brush.radialGradient(
                                listOf(if (hold) Color(0xFFFFE6A8) else Color(0xFFB8FFE0), bg)
                            )
                        else Brush.linearGradient(listOf(bg, bg))
                    )
                    .pressClick(enabled = running, feedbackOnClick = false) {
                        if (i == target && waiting) {
                            early++
                            streak = 0
                            soft = soft.miss()
                            lost(i)
                            feedback(FeedbackCue.RETRY)
                        } else if (i == target) {
                            hits++
                            soft = soft.hit()
                            streak++
                            best = maxOf(best, streak)
                            ripple = i
                            missed = -1
                            feedback(FeedbackCue.CORRECT)
                            advance()
                        } else {
                            streak = 0
                            soft = soft.miss()
                            feedback(FeedbackCue.RETRY)
                        }
                    }
                    .semantics {
                        contentDescription =
                            if (hold) "Luz en espera: no la toques todavía"
                            else if (on) "Luz activa" else "Casilla ${i+1}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (hold)
                    Icon(
                        Icons.Rounded.HourglassTop,
                        null,
                        Modifier.size(34.dp),
                        tint = Color(0xFF2B1D00),
                    )
                else if (on)
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(38.dp), tint = Color(0xFF073B29))
                else if (missAlpha > 0f)
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        Modifier.size(28.dp).graphicsLayer { alpha = missAlpha },
                        tint = missColor,
                    )
            }
        }
        Text(
            "$hits ${if(hits==1)"luz tocada" else "luces tocadas"}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun StatChip(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MeteorSession(vm: BrainViewModel, infinite: Boolean, back: () -> Unit) {
    val clock = rememberSession(infinite)
    val feedback = LocalFeedback.current
    val stats = remember { MeteorStats() }
    var lane by remember { mutableIntStateOf(1) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val best = prefs.firstOrNull { it.key == "meteor_best" }?.value?.toIntOrNull() ?: 0
    val startBest = remember { best }
    val over = clock.seconds == 0 || stats.lives == 0
    val save =
        rememberArcadeSave(vm, "meteor", infinite, { stats.score }) {
            mapOf(
                "level" to stats.level,
                "dodged" to stats.dodged,
                "crystals" to stats.crystals,
                "destroyed" to (stats.lives == 0),
            )
        }
    LaunchedEffect(over) {
        if (over) {
            if (stats.score > best) vm.pref("meteor_best", stats.score.toString())
            save()
        }
    }
    fun move(to: Int) {
        if (to != lane) {
            lane = to
            feedback(FeedbackCue.TAP)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SessionHeader("Ruta espacial", clock, "${stats.dodged} ✓", back)
        if (over) {
            SessionSummary(
                "${stats.score}",
                (if (stats.lives == 0) "Nave destruida · " else "") +
                    "nivel ${stats.level} · ${stats.dodged} meteoros esquivados · ${stats.crystals} cristales\n" +
                    (if (stats.score > startBest) "Récord anterior: $startBest"
                    else "Récord: $startBest"),
                back,
                record = stats.score > startBest && stats.score > 0,
                best = startBest,
                value = stats.score,
            )
            return@Column
        }
        if (!clock.running) PausedBanner(clock.toggle)
        else
            Text(
                "Toca un lado, desliza o usa las flechas. Cada ${METEORS_PER_LEVEL} meteoros sube el nivel y la velocidad.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        MeteorBoard(
            clock.running,
            stats,
            lane,
            ::move,
            Modifier.fillMaxWidth().aspectRatio(.74f),
            skin = Rewards.cosmetic(vm.wallet.skin)?.let { Color(it.color) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                { move((lane - 1).coerceAtLeast(0)) },
                Modifier.weight(1f).height(60.dp),
                enabled = clock.running,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.ChevronLeft, null)
                Text("Izquierda")
            }
            FilledTonalButton(
                { move((lane + 1).coerceAtMost(2)) },
                Modifier.weight(1f).height(60.dp),
                enabled = clock.running,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Derecha")
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        Text(
            "${stats.dodged} esquivados · ${METEOR_LIVES-stats.lives} encuentros · récord $best",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
