package es.foco.next.ui

import android.app.Application
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import es.foco.next.core.AbsoluteRules
import es.foco.next.core.DnsGuard
import es.foco.next.core.home.CompanionMood
import es.foco.next.core.home.HomePalette
import es.foco.next.core.home.HomeSections
import es.foco.next.core.home.HomeState
import es.foco.next.data.Repository
import es.foco.next.log.BlockLog
import es.foco.next.log.InspectorLog
import es.foco.next.log.UsageContrast
import es.foco.next.runtime.AppKinds
import es.foco.next.runtime.DnsMonitor
import es.foco.next.runtime.FocusAccessibilityService
import es.foco.next.runtime.HomeHub
import es.foco.next.runtime.HomeRole
import es.foco.next.runtime.QuickGame
import es.foco.next.runtime.Weekend
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Reto rápido calculado (juego de Lab que lleva más días sin jugarse). */
data class QuickInfo(val game: String, val title: String, val level: Int, val daysSince: Int)

/** Un día del resumen semanal. */
data class DayBar(val day: LocalDate, val investedMin: Long, val ocioMin: Long)

/** Estado del sistema para la barra de protección. */
data class SystemState(
    val dnsOk: Boolean = false,
    /**
     * El teléfono ya usa el proveedor pedido con el candado; Foco lo adopta a esta hora ("" si no).
     */
    val dnsWaiting: String = "",
    val accessibility: Boolean = false,
    val autoTime: Boolean = true,
    val homeRole: Boolean = false,
    val vpn: Boolean = false,
    val wifi: String = "Wi-Fi",
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val dnd: Boolean = false,
    val dndAccess: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
)

/** Qué se puede esperar al abrir ocio ahora (para la Aduana; el que decide es el servicio). */
data class LeisureOutlook(
    val night: Boolean,
    val hiddenHere: Boolean,
    val text: String,
    val focus: Boolean = true,
)

/**
 * Estado de Foco Home para Compose. Lee de HomeHub (EstadoActual), del plan, de UsageStats (con
 * permiso) y del registro. Todo lo pesado va en un hilo propio; nunca lanza.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app), HomeHub.Listener {
    private val io =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "foco-home-vm").apply { isDaemon = true }
        }
    private val ctx: Context
        get() = getApplication()

    var estado by mutableStateOf<HomeHub.EstadoActual?>(HomeHub.last())
        private set

    var apps by mutableStateOf<List<HomeApps.App>>(emptyList())
        private set

    var usageToday by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    var quick by mutableStateOf<QuickInfo?>(null)
        private set

    var week by mutableStateOf<List<DayBar>>(emptyList())
        private set

    var dock by mutableStateOf(loadDock())
        private set

    var variant by
        mutableStateOf(CompanionMood.Variant.of(prefs().getString("companion", "BONSAI")))
        private set

    // Hasta la primera lectura se supone todo bien: sin avisos que aparecen y desaparecen.
    var system by mutableStateOf(SystemState(accessibility = true, homeRole = true))
        private set

    /**
     * Iconos ya convertidos (fuera del hilo principal): el cajón y el dock no convierten al hacer
     * scroll.
     */
    var icons by mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())
        private set

    var nextStep by mutableStateOf("")
        private set

    // Preferencias del panel izquierdo.
    var reduceMotion by mutableStateOf(ambient().getBoolean("reduce", false))
        private set

    var saving by mutableStateOf(ambient().getBoolean("saving", false))
        private set

    // Gestos siempre encendidos (auditoría v25): doble toque bloquea, abajo notificaciones, arriba
    // apps.
    val gestureLock = true
    val gestureNotifications = true
    val gestureDrawer = true

    // ---- Temas fotográficos (prototipo S23) ----
    var themesOn by mutableStateOf(prefs().getBoolean("themes_on", true))
        private set

    // v38: todo automático. Quien había fijado un tema a mano pasa una vez a automático (el fondo
    // sigue a la hora); después puede volver a elegir uno si quiere.
    var themeChoice by
        mutableStateOf(
            prefs().let { p ->
                if (!p.getBoolean("theme_auto_v38", false))
                    p.edit().putString("theme", "AUTO").putBoolean("theme_auto_v38", true).apply()
                p.getString("theme", "AUTO") ?: "AUTO"
            }
        )
        private set

    var theme by mutableStateOf(es.foco.next.core.home.PhotoTheme.CASCADA)
        private set

    /** Foto del tema ya decodificada a tamaño de pantalla (fuera del hilo principal), o null. */
    var photo by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set

    /** v27: la foto del tema también va a la pantalla de bloqueo, siempre (automático). */
    val lockSync = true
    /** Iconos del dock con color suavizado para combinar con la foto. */
    var dockBlend by mutableStateOf(prefs().getBoolean("dock_blend", true))
        private set

    private var photoKey: String? = "-"

    /** Barra de estado con iconos oscuros (cielo claro). */
    val lightBars: Boolean
        get() = if (themesOn) theme.light else estadoActual != HomeState.Estado.NOCHE

    /** Elige el tema (automático o el tuyo) y carga su foto solo si cambió. Barato. */
    fun syncTheme() {
        val now = LocalDateTime.now()
        val f =
            es.foco.next.core.home.PhotoTheme.Facts().apply {
                hour = now.hour
                minute = now.minute
                night = AbsoluteRules.night(now, AppKinds.autoTime(ctx))
                productive = estadoActual == HomeState.Estado.PRODUCTIVO
                focus = careMode == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                retosHoy = estado?.retosHoy ?: 0
                dayOfYear = now.dayOfYear
                aurora = "fondo_aurora" in unlocks
                mar = "fondo_mar" in unlocks
            }
        val t = es.foco.next.core.home.PhotoTheme.choose(themeChoice, f)
        if (t != theme) theme = t
        val key = if (themesOn) t.photo else null
        // v28: la pausa usa la misma foto que el inicio.
        if (prefs().getString("theme_now", null) != (key ?: ""))
            prefs().edit().putString("theme_now", key ?: "").apply()
        if (key == photoKey) return
        photoKey = key
        if (key == null) {
            photo = null
            return
        }
        val res = photoRes(key)
        val sync = lockSync
        io.execute {
            val img = runCatching { decodePhoto(res) }.getOrNull()
            postMain { if (photoKey == key) photo = img }
            if (sync) applyLock(key, res)
        }
    }

    private fun photoRes(key: String): Int =
        when (key) {
            "cafe" -> es.foco.next.R.drawable.theme_cafe
            "cascada" -> es.foco.next.R.drawable.theme_cascada
            "pradera" -> es.foco.next.R.drawable.theme_pradera
            "tarde" -> es.foco.next.R.drawable.theme_tarde
            "dunas" -> es.foco.next.R.drawable.theme_dunas
            "ruta" -> es.foco.next.R.drawable.theme_ruta
            "horizonte" -> es.foco.next.R.drawable.theme_horizonte
            "luna" -> es.foco.next.R.drawable.theme_luna
            "cumbre" -> es.foco.next.R.drawable.theme_cumbre
            "aurora" -> es.foco.next.R.drawable.theme_aurora
            "mar" -> es.foco.next.R.drawable.theme_mar
            else -> es.foco.next.R.drawable.theme_lava
        }

    /** RGB_565 y como mucho del tamaño de la pantalla: menos memoria y dibujo más rápido. */
    private fun decodePhoto(res: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val r = ctx.resources
        val dm = r.displayMetrics
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeResource(r, res, o)
        var sample = 1
        while (
            o.outWidth / (sample * 2) >= dm.widthPixels &&
                o.outHeight / (sample * 2) >= dm.heightPixels
        ) sample *= 2
        val opts =
            android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
        val b = android.graphics.BitmapFactory.decodeResource(r, res, opts) ?: return null
        b.prepareToDraw()
        return b.asImageBitmap()
    }

    /** Pantalla de bloqueo con la misma foto (opcional). Solo cuando cambia el tema. */
    private fun applyLock(key: String, res: Int) {
        if (prefs().getString("theme_lock_applied", "") == key) return
        runCatching {
            ctx.resources.openRawResource(res).use {
                android.app.WallpaperManager.getInstance(ctx)
                    .setStream(it, null, true, android.app.WallpaperManager.FLAG_LOCK)
            }
            prefs().edit().putString("theme_lock_applied", key).apply()
        }
    }

    fun chooseTheme(choice: String) {
        prefs().edit().putString("theme", choice).apply()
        themeChoice = choice
        syncTheme()
    }

    // ---- El búho, voz de Foco: banner del último mensaje ----
    var owlBanner by mutableStateOf<Array<String>?>(null)
        private set

    fun refreshBanner() {
        owlBanner = es.foco.next.runtime.CompanionNotifier.lastForBanner(ctx)
        gapNotice = es.foco.next.runtime.OwlWatch.gapNotice(ctx)
    }

    /** "Estuve apagado de 5:00 a 10:07…" (6 h tras volver) o null. */
    var gapNotice by mutableStateOf<String?>(null)
        private set

    fun closeGap() {
        es.foco.next.runtime.OwlWatch.closeGap(ctx)
        gapNotice = null
    }

    fun closeBanner() {
        owlBanner?.let { es.foco.next.runtime.CompanionNotifier.closeBanner(ctx, it[3]) }
        owlBanner = null
    }

    // ---- De control a cuidado ----
    var careMode by mutableStateOf(es.foco.next.runtime.CareStore.mode(app))
        private set

    var focusLeft by mutableStateOf(0L)
        private set

    /** Minutos que quedan del Cuidado actual y del tope diario de Cuidado. */
    var careLeft by mutableStateOf(0L)
        private set

    var careBudgetMin by mutableStateOf(Long.MAX_VALUE)
        private set

    var showNumbers by mutableStateOf(true)
        private set

    /** La pregunta de ánimo del día está pendiente (primera vez que se abre Foco hoy). */
    var askMood by mutableStateOf(false)
        private set

    var nightLabel by mutableStateOf("21:00 a 07:00")
        private set

    var nightPending by mutableStateOf("")
        private set

    /** "Hoy leíste y practicaste idiomas": lo bueno de hoy, sin números. */
    var goodToday by mutableStateOf("")
        private set

    /** Relee modo, ánimo y preferencias (barato: SharedPreferences). */
    fun refreshCare() {
        val c = ctx
        val n = es.foco.next.runtime.CareStore.night(c)
        careMode = es.foco.next.runtime.CareStore.mode(c)
        focusLeft = es.foco.next.runtime.CareStore.focusMinutesLeft(c)
        careLeft = es.foco.next.runtime.CareStore.careMinutesLeft(c)
        careBudgetMin =
            es.foco.next.runtime.CareStore.careBudgetLeft(c).let {
                if (it == Long.MAX_VALUE) it else it / 60_000
            }
        showNumbers = es.foco.next.core.care.CareMode.showNumbers(careMode, false)
        nightLabel = n.label()
        val st = es.foco.next.runtime.CareStore.nightSetting(c)
        nightPending =
            if (
                st.pending != null &&
                    st.pendingFrom != null &&
                    st.pendingFrom.isAfter(LocalDate.now())
            )
                st.pending.label()
            else ""
        goodToday = goodPhrase()
        syncTheme()
    }

    /** Pie del búho: "3 hábitos hoy · 5 min/h disponibles". */
    var owlMetrics by mutableStateOf("")
        private set

    private fun owlLine(habits: Int): String =
        runCatching {
                val c = ctx
                val r = Repository.get(c)
                val now = LocalDateTime.now()
                val auto = AppKinds.autoTime(c)
                val st =
                    es.foco.next.core.LeisureStatus.of(
                        now,
                        auto,
                        r.enabled(),
                        FocusAccessibilityService.connected(),
                        r.liveUsed(),
                        r.locked(),
                        r.remaining(),
                    )
                es.foco.next.core.care.CareMode.owlLine(
                    habits,
                    careMode,
                    st.night,
                    AbsoluteRules.nightEnd(),
                    (st.availableMs + 59_999) / 60_000,
                    showNumbers,
                )
            }
            .getOrDefault("")

    private fun goodPhrase(): String {
        val kinds = HashSet<es.foco.next.core.care.GoodApps.Kind>()
        val byPkg = apps.associateBy { it.pkg }
        for ((pkg, ms) in usageToday) {
            if (ms < 60_000) continue
            val a = byPkg[pkg]
            if (
                a != null &&
                    (a.section == HomeSections.Section.OCIO ||
                        a.section == HomeSections.Section.BLOQUEADA)
            )
                continue
            if (a == null && !es.foco.next.core.care.GoodApps.good(pkg)) continue
            es.foco.next.core.care.GoodApps.kind(pkg, a?.label)?.let { kinds.add(it) }
        }
        val retos = estado?.retosHoy ?: 0
        owlMetrics = owlLine(kinds.size + if (retos > 0) 1 else 0)
        return es.foco.next.core.care.GoodApps.phrase(kinds, retos)
    }

    fun moodDone() {
        askMood = false
    }

    fun askMoodAgain() {
        askMood = true
    }

    // ---- Búho de Foco ----
    // Búho siempre completo (auditoría v25): tono cercano, palabra del día y burbuja de ideas.
    val owlBro = true
    val owlFaith = true
    val owlBubble = true
    /** Posición en "Otra idea ↻" y variante del texto; [owlTalk] hace hablar al búho. */
    var owlStep by mutableStateOf(0)
        private set

    var owlTalk by mutableStateOf(0)
        private set

    private var prodRunMin by mutableStateOf(0L)
    private var ocioWinMin by mutableStateOf(0L)
    private var lastAuto: es.foco.next.core.home.OwlBrain.Mode? = null

    /** Plan en caché: nada de leer el repositorio en cada fotograma. */
    @Volatile private var cfg: es.foco.next.core.AppConfig = Repository.get(app).config()

    // ---------------- Postura del búho (noche > música > app reciente > azar) ----------------

    var owlPose by mutableStateOf(es.foco.next.core.home.OwlState.Pose.CALM)
        private set

    private var musicLive = false
    private var idlePose = es.foco.next.core.home.OwlState.randomIdle(Math.random())
    private val audio = app.getSystemService(android.media.AudioManager::class.java)
    private val owlMain = android.os.Handler(android.os.Looper.getMainLooper())

    // Música al instante: el sistema avisa en cuanto un reproductor empieza, pausa o para (sin
    // consultas periódicas y sin permisos). Ver docs: por qué no MediaSessionManager.
    private val playback =
        object : android.media.AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(
                configs: MutableList<android.media.AudioPlaybackConfiguration>?
            ) {
                musicLive = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
                refreshOwl()
            }
        }

    fun refreshOwl() {
        val c = ctx
        owlPose =
            es.foco.next.core.home.OwlState.resolve(
                es.foco.next.core.AbsoluteRules.esNocheAbsoluta(java.time.LocalDateTime.now()),
                musicLive,
                es.foco.next.runtime.OwlWatch.recentCat(c),
                es.foco.next.runtime.OwlWatch.recentAt(c),
                es.foco.next.runtime.OwlWatch.recentOver(c),
                System.currentTimeMillis(),
                idlePose,
            )
    }

    /**
     * Pantalla encendida o cada pocos minutos: otra postura tranquila al azar (solo cuenta de día).
     */
    fun reshuffleOwl() {
        var next = es.foco.next.core.home.OwlState.randomIdle(Math.random())
        if (next == idlePose) next = es.foco.next.core.home.OwlState.randomIdle(Math.random())
        idlePose = next
        refreshOwl()
    }

    // ---------------- NextDNS: lo que el escudo frenó hoy (si hay clave) ----------------

    var dnsLine by mutableStateOf<String?>(null)
        private set

    var dnsTop by mutableStateOf<List<String>>(emptyList())
        private set

    /** v50: «Tu semana con NextDNS» en Análisis (y los avisos de redes quitados de noche). */
    var dnsWeek by mutableStateOf<List<String>>(emptyList())
        private set

    fun refreshDns() {
        val c = ctx
        val week = es.foco.next.runtime.NextDnsSync.week(c)
        dnsWeek =
            es.foco.next.core.backup.WeekLines.dnsWeek(
                week[1],
                week[0],
                es.foco.next.runtime.NextDnsSync.weekSocial(c),
                es.foco.next.runtime.NightQuiet.lastWeek(c),
                showNumbers,
            )
        if (!es.foco.next.runtime.NextDnsSync.hasKey(c)) {
            dnsLine = null
            dnsTop = emptyList()
            return
        }
        val s = es.foco.next.runtime.NextDnsSync.snapshot(c)
        dnsLine = es.foco.next.core.NextDnsStats.owlLine(s.blocked, s.top, showNumbers)
        dnsTop =
            s.top.drop(1).take(3).map {
                if (showNumbers) "${it.domain} · ${it.queries}" else it.domain
            }
    }

    private val dnsListener = Runnable { owlMain.post { refreshDns() } }

    /**
     * Al volver al inicio: ahora y otra vez en un momento (la Accesibilidad guarda la app al
     * salir).
     */
    fun owlOnResume() {
        refreshMoney()
        refreshRoutine()
        es.foco.next.runtime.RoutineStore.sync(ctx)
        refreshDns()
        es.foco.next.runtime.NextDnsSync.refresh(ctx, false)
        musicLive = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
        refreshOwl()
        owlMain.removeCallbacks(owlTick)
        owlMain.postDelayed(owlTick, 800)
        // Al cumplirse los 4 minutos de memoria, vuelve solo a su postura.
        owlMain.postDelayed(owlTick, es.foco.next.core.home.OwlState.MEMORY_MS + 1_000)
    }

    private val owlTick = Runnable { refreshOwl() }

    // ---------------- Rutinas obligatorias ----------------

    var routineKind by mutableStateOf<es.foco.next.core.care.Routine.Kind?>(null)
        private set

    var routineTasks by mutableStateOf<List<es.foco.next.core.care.Routine.Task>>(emptyList())
        private set

    var routineDone by mutableStateOf<Set<Int>>(emptySet())
        private set

    var routinesOn by mutableStateOf(true)
        private set

    var routinesOffFrom by mutableStateOf("")
        private set

    var planDue by mutableStateOf(false)
        private set

    var moneyLine by mutableStateOf<String?>(null)
        private set

    // ---------------- Foco Core ----------------
    var core by mutableStateOf<CoreSummary?>(null)
        private set

    /** v31: lo desbloqueado con la constancia (bufanda del búho, fondos, dock dorado). */
    var unlocks by mutableStateOf<Set<String>>(emptySet())
        private set

    var coreTick by mutableStateOf(0)
        private set

    // v41: el marcador del inicio y las caras del búho.
    /** Sube cada vez que marcas una tarea: el búho festeja (salto y confeti). */
    var celebrate by mutableStateOf(0)
        private set

    var streak by mutableStateOf(0)
        private set

    var couponCount by mutableStateOf(0)
        private set

    /** Hora límite cerca: {tarea, texto}. El búho apunta y lo dice. */
    var urgent by mutableStateOf<Array<String>?>(null)
        private set

    fun refreshCore() {
        unlocks = es.foco.next.runtime.RewardStore.unlocked(ctx)
        val before = core
        core = runCatching { coreSummary(ctx) }.getOrNull()
        val after = core
        if (before != null && after != null && after.done > before.done) celebrate++
        streak = runCatching { es.foco.next.runtime.RewardStore.streak(ctx) }.getOrDefault(0)
        couponCount =
            runCatching { es.foco.next.runtime.RewardStore.coupons(ctx).size }.getOrDefault(0)
        urgent = es.foco.next.runtime.RoutineStore.urgentNow(ctx)
        coreTick++
    }

    fun refreshMoney() {
        moneyLine = es.foco.next.runtime.MoneyStore.owlLine(ctx)
        refreshCore()
    }

    fun refreshRoutine() {
        refreshCore()
        val c = ctx
        routinesOn = es.foco.next.runtime.RoutineStore.enabled(c)
        routinesOffFrom = es.foco.next.runtime.RoutineStore.offPending(c)
        planDue = es.foco.next.runtime.RoutineStore.planDue(c)
        val k = es.foco.next.runtime.RoutineStore.current(c)
        routineKind = k
        routineTasks = if (k == null) emptyList() else es.foco.next.runtime.RoutineStore.tasks(c, k)
        routineDone = if (k == null) emptySet() else es.foco.next.runtime.RoutineStore.done(c, k)
    }

    fun checkRoutine(index: Int, on: Boolean) {
        val k = routineKind ?: return
        es.foco.next.runtime.RoutineStore.check(ctx, k, index, on)
        refreshRoutine()
    }

    fun updateRoutinesOn(on: Boolean) {
        es.foco.next.runtime.RoutineStore.setEnabled(ctx, on)
        refreshRoutine()
    }

    init {
        HomeHub.addListener(this)
        es.foco.next.runtime.RoutineStore.listen { postMain { refreshRoutine() } }
        es.foco.next.runtime.CareStore.listen { postMain { refreshCare() } }
        runCatching { audio?.registerAudioPlaybackCallback(playback, owlMain) }
        es.foco.next.runtime.NextDnsSync.addListener(dnsListener)
    }

    override fun onCleared() {
        runCatching { audio?.unregisterAudioPlaybackCallback(playback) }
        es.foco.next.runtime.NextDnsSync.removeListener(dnsListener)
        owlMain.removeCallbacks(owlTick)
        HomeHub.removeListener(this)
        es.foco.next.runtime.CareStore.listen(null)
        es.foco.next.runtime.RoutineStore.listen(null) // no retener este ViewModel
        io.shutdown()
    }

    override fun changed(e: HomeHub.EstadoActual) {
        estado = e
        nextStep = e.proximoPaso
        prodRunMin = HomeHub.productiveRunMin(ctx)
        ocioWinMin = HomeHub.ocioWindowMin(ctx)
        goodToday = goodPhrase()
        syncOwl()
        syncTheme()
    }

    private fun ambient() = ctx.getSharedPreferences("foco-ambient", Context.MODE_PRIVATE)

    private fun label(a: HomeApps.App) = HomeSections.fold(a.label)

    /**
     * App de lectura instalada (El Libro Total, Kindle, Libros…), si la hay. Se calcula al cargar
     * apps.
     */
    var readerApp by mutableStateOf<HomeApps.App?>(null)
        private set

    /** App de la Biblia instalada, si la hay. */
    var bibleApp by mutableStateOf<HomeApps.App?>(null)
        private set

    private fun findReader(list: List<HomeApps.App>): HomeApps.App? =
        list.firstOrNull { a ->
            a.section != HomeSections.Section.OCIO &&
                a.section != HomeSections.Section.BLOQUEADA &&
                label(a).let {
                    it.contains("libro total") ||
                        it.contains("kindle") ||
                        it.contains("lectura") ||
                        it == "libros" ||
                        it.contains("play libros") ||
                        it.contains("books")
                }
        }

    private fun findBible(list: List<HomeApps.App>): HomeApps.App? =
        list.firstOrNull { label(it).contains("biblia") || label(it).contains("bible") }

    /**
     * Si lo que pasa de verdad cambia, el búho vuelve a su modo automático (fuera del dibujado).
     */
    private fun syncOwl() {
        val auto = es.foco.next.core.home.OwlBrain.auto(owlFacts())
        if (auto != lastAuto) {
            lastAuto = auto
            owlStep = 0
        }
    }

    /** Datos reales para el búho. */
    fun owlFacts(): es.foco.next.core.home.OwlBrain.Facts {
        val f = es.foco.next.core.home.OwlBrain.Facts()
        val e = estado
        f.estado = estadoActual
        f.investedMin = e?.minutosInvertidosHoy ?: 0
        f.productiveRunMin = prodRunMin
        f.ocioWindowMin = ocioWinMin
        f.night = night
        readerApp?.let {
            f.reader = it.label
            f.readerProductive = it.section == HomeSections.Section.PRODUCTIVA
        }
        bibleApp?.let { f.bible = it.label }
        quick?.let {
            f.game = it.title
            f.gameLevel = it.level
        }
        return f
    }

    /** Modo que muestra el búho ahora (automático, o el elegido con "Otra idea ↻"). */
    fun owlMode(): es.foco.next.core.home.OwlBrain.Mode {
        val f = owlFacts()
        val cycle = es.foco.next.core.home.OwlBrain.cycle(f, owlFaith)
        return cycle[Math.floorMod(owlStep, cycle.size)]
    }

    fun owlText(m: es.foco.next.core.home.OwlBrain.Mode) =
        es.foco.next.core.home.OwlBrain.say(
            m,
            owlFacts(),
            owlStep / 2 + LocalDate.now().dayOfYear,
            owlBro,
        )

    fun owlCta(m: es.foco.next.core.home.OwlBrain.Mode) =
        es.foco.next.core.home.OwlBrain.cta(m, owlFacts())

    fun owlNext() {
        owlStep++
        owlTalk++
    }

    val estadoActual: HomeState.Estado
        get() = estado?.estado ?: HomeState.Estado.NEUTRO

    val palette: HomePalette
        get() = if (themesOn) theme.palette else HomePalette.of(estadoActual)

    val night: Boolean
        get() = estadoActual == HomeState.Estado.NOCHE || estadoActual == HomeState.Estado.DESPERTAR

    fun mood(): CompanionMood {
        val e = estado
        return CompanionMood.of(
            estadoActual,
            e?.minutosInvertidosHoy ?: 0,
            e?.retosHoy ?: 0,
            e?.extraHoyMin ?: 0,
            e?.descansosHoy ?: 0,
            LocalDateTime.now().hour,
        )
    }

    private fun prefs() = ctx.getSharedPreferences("foco-home", Context.MODE_PRIVATE)

    /** Al volver al inicio: todo se refresca (sin temporizadores). */
    fun refresh() {
        HomeHub.refresh(ctx)
        // v27: estos ajustes se cambian en Foco (Ajustes › Inicio); al volver se releen.
        themesOn = prefs().getBoolean("themes_on", true)
        dockBlend = prefs().getBoolean("dock_blend", true)
        refreshCare()
        refreshBanner()
        // La pregunta de ánimo se decide al volver al inicio (no al guardar: la tarjeta debe poder
        // mostrar la respuesta y la oferta de CUIDADO).
        if (!es.foco.next.runtime.CareStore.moodAskedToday(ctx) && LocalDateTime.now().hour >= 5)
            askMood = true
        refreshSystem()
        val config = Repository.get(ctx).config()
        cfg = config
        nextStep = es.foco.next.runtime.RoutineStore.nextTaskLabel(ctx)
        reduceMotion = ambient().getBoolean("reduce", false)
        prodRunMin = HomeHub.productiveRunMin(ctx)
        ocioWinMin = HomeHub.ocioWindowMin(ctx)
        owlTalk++
        saving = ambient().getBoolean("saving", false)
        val known = icons
        io.execute {
            val loaded = HomeApps.load(ctx, config)
            // Iconos a imagen una sola vez por app (reutiliza los ya hechos).
            val px = (48 * ctx.resources.displayMetrics.density).toInt().coerceIn(96, 192)
            val newIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
            for (a in loaded) {
                val key = a.pkg + "/" + a.component.className
                val img =
                    known[key]
                        ?: a.icon?.let { d -> runCatching { drawableImage(d, px) }.getOrNull() }
                if (img != null) newIcons[key] = img
            }
            postMain { icons = newIcons }
            val usage = usage()
            val q =
                runCatching {
                        val c = personal.qqq.braintraining.QuickChallenge.load(ctx)
                        QuickGame.save(ctx, c.game, c.title, c.level)
                        QuickInfo(c.game, c.title, c.level, c.daysSince)
                    }
                    .getOrNull()
            val reader = findReader(loaded)
            val bible = findBible(loaded)
            postMain {
                apps = loaded
                readerApp = reader
                bibleApp = bible
                usageToday = usage
                quick = q
                goodToday = goodPhrase()
                syncOwl()
            }
        }
        loadWeek(config)
    }

    private fun postMain(block: () -> Unit) =
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)

    /** Tiempo de hoy por app (Acceso de uso; sin permiso, vacío y la lista queda limpia). */
    private fun usage(): Map<String, Long> =
        runCatching {
                if (!UsageContrast.allowed(ctx)) return emptyMap()
                val start =
                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val usm = ctx.getSystemService(UsageStatsManager::class.java)
                usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).mapValues {
                    it.value.totalTimeInForeground
                }
            }
            .getOrDefault(emptyMap())

    /** Últimos 7 días desde el registro (mismo origen que el informe de Foco). */
    private fun loadWeek(config: es.foco.next.core.AppConfig) {
        val today = LocalDate.now()
        // v32 (R6): con Acceso de uso el ocio total ya sale del sistema (incluye premios y
        // Cuidado);
        // sin él, se suman las clases que mide la Accesibilidad.
        val systemTotal = runCatching { UsageContrast.allowed(ctx) }.getOrDefault(false)
        BlockLog.query(
            ctx,
            BlockLog.Work { db -> InspectorLog.rows(ctx, db, today, 7, config) },
            { rows ->
                val inv = HashMap<String, Long>()
                val oc = HashMap<String, Long>()
                for (r in rows ?: emptyList()) {
                    when (r.type) {
                        InspectorLog.OCIO -> oc[r.day] = (oc[r.day] ?: 0) + r.ms
                        InspectorLog.OCIO_PREMIO,
                        InspectorLog.OCIO_CUIDADO,
                        InspectorLog.OCIO_FUERA ->
                            if (!systemTotal) oc[r.day] = (oc[r.day] ?: 0) + r.ms
                        InspectorLog.USO_PRODUCTIVO,
                        InspectorLog.ALTERNATIVA -> inv[r.day] = (inv[r.day] ?: 0) + r.ms
                    }
                }
                week =
                    (6 downTo 0).map { back ->
                        val d = today.minusDays(back.toLong())
                        DayBar(
                            d,
                            (inv[d.toString()] ?: 0) / 60_000,
                            (oc[d.toString()] ?: 0) / 60_000,
                        )
                    }
            },
            null,
        )
    }

    fun refreshSystem() {
        val c = ctx
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val wifi =
            when {
                caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    "Wi-Fi: no"
                else -> {
                    val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.replace("\"", "")
                    when {
                        ssid != null && !ssid.contains("unknown ssid") && ssid.isNotEmpty() ->
                            "Wi-Fi: $ssid"
                        else -> "Wi-Fi: sí"
                    }
                }
            }
        val nm = c.getSystemService(NotificationManager::class.java)
        system =
            system.copy(
                dnsOk = dnsOk(c),
                dnsWaiting = dnsWaiting(c),
                accessibility = FocusAccessibilityService.connected(),
                autoTime = AppKinds.autoTime(c),
                homeRole = HomeRole.held(c),
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                wifi = wifi,
                dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
                dndAccess = nm.isNotificationPolicyAccessGranted,
                usageAccess = runCatching { UsageContrast.allowed(c) }.getOrDefault(false),
                notifications =
                    c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
    }

    fun battery(pct: Int, charging: Boolean) {
        system = system.copy(batteryPct = pct, charging = charging)
    }

    // ---- Dock: 5 ranuras ----

    private fun loadDock(): List<String> {
        val p = prefs()
        val raw = p.getString("dock5", null)
        val out =
            if (raw == null)
                p.getString("dock", "")
                    .orEmpty()
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .toMutableList()
            else raw.split(",").toMutableList()
        while (out.size < DOCK_SLOTS) out.add("")
        return out.take(DOCK_SLOTS)
    }

    /** Última app quitada del dock (espacio, paquete), para poder deshacerlo unos segundos. */
    var dockUndo by mutableStateOf<Pair<Int, String>?>(null)
        private set

    fun removeFromDock(index: Int) {
        val pkg = dock.getOrNull(index) ?: return
        if (pkg.isEmpty()) return
        setDock(index, "")
        dockUndo = index to pkg
        owlMain.removeCallbacks(clearUndo)
        owlMain.postDelayed(clearUndo, 6_000)
    }

    fun undoDock() {
        val (i, pkg) = dockUndo ?: return
        if (dock.getOrNull(i)?.isEmpty() == true) setDock(i, pkg)
        dockUndo = null
    }

    private val clearUndo = Runnable { dockUndo = null }

    fun setDock(index: Int, pkg: String) {
        val d = dock.toMutableList()
        d[index] = pkg
        prefs().edit().putString("dock5", d.joinToString(",")).apply()
        dock = d
    }

    /** Ranuras visibles: ocio y bloqueo total nunca; de noche solo esenciales. (Plan en caché.) */
    fun dockAllowed(pkg: String): Boolean {
        val c = cfg
        if (!HomeSections.dockAllowed(c, pkg)) return false
        return !night || es.foco.next.core.FocusEngine.essential(c, pkg)
    }

    fun iconOf(a: HomeApps.App) = icons[a.pkg + "/" + a.component.className]

    fun chooseVariant(v: CompanionMood.Variant) {
        prefs().edit().putString("companion", v.name).apply()
        variant = v
    }

    // ---- Aduana ----

    fun outlook(): LeisureOutlook {
        val c = ctx
        val now = LocalDateTime.now()
        val auto = AppKinds.autoTime(c)
        if (AbsoluteRules.night(now, auto))
            return LeisureOutlook(
                true,
                false,
                "Ocio cerrado hasta las ${AbsoluteRules.nightEnd()}:00",
            )
        when (es.foco.next.runtime.CareStore.mode(c)) {
            es.foco.next.core.care.CareMode.Modo.CUIDADO ->
                return LeisureOutlook(false, false, "Modo Cuidado · Sin límite hoy", focus = false)
            es.foco.next.core.care.CareMode.Modo.NORMAL ->
                if (!Weekend.active(c))
                    return LeisureOutlook(
                        false,
                        false,
                        if (es.foco.next.runtime.CareStore.normalCooldownMs(c) > 0)
                            "Modo Normal · 5 min y 30 min de pausa"
                        else "Modo Normal · Límite: 5 min/hora",
                        focus = false,
                    )
            else -> {}
        }
        // v26: en Enfoque y en el Weekend Lock el ocio se abre con la hora libre de Foco Core.
        val who = if (Weekend.active(c)) "Weekend Lock" else "Enfoque"
        val text =
            when {
                es.foco.next.runtime.RoutineStore.freeActive(c) -> "$who · Hora libre en marcha"
                es.foco.next.runtime.RoutineStore.freeUsedToday(c) ->
                    "$who · La hora libre de hoy ya se usó"
                else -> "$who · Cumple tus tareas de hoy para abrir 1 h libre"
            }
        return LeisureOutlook(false, false, text)
    }

    companion object {
        const val DOCK_SLOTS = 5

        /**
         * Espera tranquila: el teléfono ya usa el proveedor que pediste con el candado y Foco lo
         * adopta al cumplirse las 24 h. Devuelve el día y la hora, o "" si no es el caso. No afloja
         * nada: Foco sigue tratando el DNS como no conforme hasta entonces.
         */
        fun dnsWaiting(c: Context): String =
            runCatching {
                    val pending = DnsMonitor.pendingProvider(c)
                    if (pending.isEmpty()) return ""
                    val cm = c.getSystemService(ConnectivityManager::class.java)
                    val lp = cm.getLinkProperties(cm.activeNetwork) ?: return ""
                    if (
                        !lp.isPrivateDnsActive ||
                            !DnsGuard.sameHost(lp.privateDnsServerName, pending)
                    )
                        return ""
                    java.time.Instant.ofEpochMilli(DnsMonitor.pendingAt(c))
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                "EEEE H:mm",
                                java.util.Locale("es"),
                            )
                        )
                }
                .getOrDefault("")

        /** DNS privado activo con el proveedor elegido (misma regla que la Fase 3). */
        fun dnsOk(c: Context): Boolean =
            runCatching {
                    val cm = c.getSystemService(ConnectivityManager::class.java)
                    val lp = cm.getLinkProperties(cm.activeNetwork) ?: return false
                    DnsGuard.compliant(
                        DnsGuard.State(
                            true,
                            true,
                            lp.isPrivateDnsActive,
                            lp.privateDnsServerName,
                            DnsMonitor.provider(c),
                        )
                    )
                }
                .getOrDefault(false)
    }
}
