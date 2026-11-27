package es.foco.next.ui

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import es.foco.next.core.home.CompanionMood
import es.foco.next.core.home.HomeSections
import es.foco.next.core.home.HomeSections.Section
import es.foco.next.core.home.HomeState
import es.foco.next.data.Repository
import es.foco.next.runtime.AduanaStore
import es.foco.next.runtime.CompanionNotifier
import es.foco.next.runtime.FocusAccessibilityService
import es.foco.next.runtime.HomeHub
import es.foco.next.runtime.HomeRole
import java.time.LocalDate

/**
 * Foco Home en Jetpack Compose. Nunca cuenta como ocio ni productiva (el paquete de Foco y los
 * lanzadores están excluidos del registro). No cobra ni bloquea por sí mismo: el servicio aplica la
 * regla de 5 minutos y el banco; la Aduana solo puede acortar un bloque.
 *
 * Respaldo: si el inicio se cierra con un error dos veces en 10 minutos, arranca en una versión
 * mínima (buscador + lista, en Vistas) hasta que se pulse "Reintentar".
 */
class HomeActivity : ComponentActivity(), HomeActions {
    private val vm: HomeViewModel by viewModels()
    private var drawerOpen by mutableStateOf(false)
    private var drawerFocus by mutableStateOf(false)
    private var aduanaApp by mutableStateOf<HomeApps.App?>(null)
    private var showModes by mutableStateOf(false)
    /** Al elegir CUIDADO: antes, la pregunta de ánimo (se puede saltar; el cambio va igual). */
    private var careMoodAsk by mutableStateOf(false)
    /** Minutos de Cuidado elegidos (se aplican tras la pregunta de ánimo). */
    private var pendingCareMin = 60
    /** Despertar con el búho y "Respirar 30 s" (inquietud). */
    private var showRoutine by mutableStateOf(false)
    private var bankApp by mutableStateOf<HomeApps.App?>(null)
    private var coreTab by mutableStateOf(-1)
    private var photoFor: Pair<String, Int>? = null
    private val cameraFile by lazy { java.io.File(java.io.File(cacheDir, "camara").apply { mkdirs() }, "foto.jpg") }
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val (block, i) = photoFor ?: return@registerForActivityResult
            photoFor = null
            es.foco.next.runtime.RoutineStore.checkBlock(this, block, i, true)
            vm.refreshRoutine()
            if (ok) {
                val app = applicationContext
                Thread {
                    val saved = es.foco.next.runtime.RoutineStore.saveEvidence(app, cameraFile, block, i)
                    runOnUiThread {
                        vm.refreshCore()
                        toast(if (saved) "Hecho, con evidencia" else "Hecho (no se pudo guardar la foto)")
                    }
                }.start()
            } else toast("Hecho, sin foto")
        }
    private var showBreathe by mutableStateOf(false)
    private var resumed by mutableStateOf(false)
    private var homeTick by mutableStateOf(0)

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { HomeRole.check(this); vm.refreshSystem() }

    private val battery =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = readBattery(i)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HomeCrashGuard.install(this)
        if (HomeCrashGuard.minimal(this)) {
            MinimalHome.show(this)
            handleMessage(intent)
            return
        }
        enableEdgeToEdge()
        preferHighRefreshRate()
        setContent {
            CompositionLocalProvider(LocalMotion provides (resumed && UiMotion.allowed(this))) {
                BackHandler(true) {
                    when {
                        bankApp != null -> bankApp = null
                        coreTab >= 0 -> coreTab = -1
                        showRoutine -> showRoutine = false
                        showBreathe -> showBreathe = false
                        careMoodAsk -> careMoodAsk = false
                        showModes -> showModes = false
                        aduanaApp != null -> aduanaApp = null
                        drawerOpen -> drawerOpen = false
                        else -> {} // un inicio no se cierra con "atrás"
                    }
                }
                // Iconos de la barra de estado según el cielo del tema (claro u oscuro).
                val lightBars = vm.lightBars
                androidx.compose.runtime.LaunchedEffect(lightBars) {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightBars
                }
                HomeScreen(vm, this, drawerOpen, drawerFocus, homeTick, { focus -> drawerFocus = focus; drawerOpen = true }, { drawerOpen = false })
                val app = aduanaApp
                if (app != null) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        val outlook = androidx.compose.runtime.remember(app) { vm.outlook() }
                        AduanaSheet(app.label, outlook, { m ->
                            AduanaStore.choose(this, app.pkg, m)
                            aduanaApp = null
                            launch(app)
                        }) { aduanaApp = null }
                    }
                }
                if (showModes)
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        val now = java.time.ZonedDateTime.now()
                        ModeSheet(
                            vm.careMode,
                            vm.focusLeft,
                            es.foco.next.core.care.CareMode.focusEnd(
                                es.foco.next.core.care.CareMode.Duracion.HASTA_LA_NOCHE, now,
                                es.foco.next.core.AbsoluteRules.nightStart(),
                            ) >= 0,
                            es.foco.next.core.AbsoluteRules.nightStart(),
                            onCare = { m -> showModes = false; pendingCareMin = m; chooseCare() },
                            onNormal = {
                                showModes = false
                                if (es.foco.next.runtime.CareStore.choose(this, es.foco.next.core.care.CareMode.Modo.NORMAL, "elegido"))
                                    toast("Modo Normal: 5 min de ocio por hora, con avisos y sin cierres.")
                                vm.refreshCare()
                            },
                            onFocus = { d ->
                                showModes = false
                                if (es.foco.next.runtime.CareStore.focus(this, d)) {
                                    vm.refreshCare()
                                    toast("Enfoque activado. Termina en ${minutes(vm.focusLeft)}.")
                                } else toast("Esa duración ya no está disponible hoy.")
                            },
                            onDismiss = { showModes = false },
                            careBudgetMin = vm.careBudgetMin,
                            careLeft = vm.careLeft,
                        )
                    }
                if (careMoodAsk)
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        Sheet({ careMoodAsk = false }) {
                            MoodCard(
                                "Antes de pasar a Cuidado: ¿cómo estás?",
                                "Prefiero no decirlo",
                                false,
                                onAnswer = { n, w ->
                                    es.foco.next.runtime.CareStore.recordMood(this, n, w, "al_elegir_cuidado")
                                    enterCare("elegido_animo_$n")
                                    toast(es.foco.next.core.care.Mood.reply(n))
                                    false
                                },
                                onCare = {},
                                onSkip = { enterCare("elegido") },
                            )
                        }
                    }
                if (showRoutine)
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        val k = vm.routineKind
                        if (k != null)
                            RoutineSheet(
                                k, vm.routineTasks, vm.routineDone, vm::checkRoutine,
                                if (k == es.foco.next.core.care.Routine.Kind.MORNING) vm.moneyLine else null,
                            ) { showRoutine = false }
                    }
                if (coreTab >= 0)
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        FocoCoreScreen(vm, this, coreTab) { coreTab = -1 }
                    }
                bankApp?.let { app ->
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        BankSheet(
                            app.label,
                            vm.moneyLine,
                            onSave = { cents, note ->
                                es.foco.next.runtime.MoneyStore.add(this, cents, note)
                                vm.refreshMoney()
                                bankApp = null
                                launch(app)
                            },
                            onOpen = { bankApp = null; launch(app) },
                            onDismiss = { bankApp = null },
                        )
                    }
                }
                if (showBreathe)
                    CompositionLocalProvider(LocalHome provides rememberHomeColors(vm.palette)) {
                        BreatheSheet { showBreathe = false }
                    }
            }
        }
        handleMessage(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pulsar Inicio estando aquí: cierra cajón y Aduana y vuelve al panel central.
        drawerOpen = false
        aduanaApp = null
        homeTick++
        handleMessage(intent)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        HomeRole.check(this)
        if (HomeCrashGuard.minimal(this)) return
        es.foco.next.runtime.OwlWatch.ensure(this)
        // Auditoría: los retos de Lab entran al registro también desde el inicio (antes solo al
        // abrir Foco completo), tras el primer dibujado.
        window.decorView.post { runCatching { es.foco.next.log.InspectorLog.drainLab(this) } }
        vm.refresh()
        vm.owlOnResume()
        runCatching { readBattery(registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)) }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = vm.lightBars
    }

    override fun onPause() {
        resumed = false
        runCatching { unregisterReceiver(battery) }
        super.onPause()
    }

    /**
     * Pide el modo más rápido de la pantalla a su resolución (120 Hz en Galaxy S23 Ultra), como hace
     * Lab. Android puede bajarlo para ahorrar batería.
     */
    private fun preferHighRefreshRate() {
        runCatching {
            val d = display ?: return
            val current = d.mode
            val best =
                d.supportedModes
                    .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                    .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        }
    }

    private fun readBattery(i: Intent?) {
        if (i == null) return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        vm.battery(
            if (level < 0 || scale <= 0) -1 else level * 100 / scale,
            st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    // ===================== HomeActions =====================

    override fun openApp(app: HomeApps.App) {
        when (app.section) {
            Section.BLOQUEADA -> toast("${app.label}: bloqueo total, siempre.")
            // Cuidado: en modo CUIDADO el ocio se abre sin peaje; en NORMAL la Aduana solo pregunta
            // (no cierra nada) y en ENFOQUE funciona como siempre.
            Section.OCIO -> {
                if (es.foco.next.runtime.CareStore.mode(this) == es.foco.next.core.care.CareMode.Modo.CUIDADO) launch(app)
                else aduanaApp = app
            }
            // Banco: antes de entrar, cuánto vas a sacar o pagar y para qué (se puede saltar).
            else -> if (es.foco.next.core.SensitiveApps.isBank(app.pkg)) bankApp = app else launch(app)
        }
    }

    private fun launch(app: HomeApps.App) {
        if (!HomeApps.open(this, app)) toast("No se pudo abrir ${app.label}") else drawerOpen = false
    }

    override fun openLab(game: String) {
        runCatching { startActivity(personal.qqq.braintraining.LabLaunch.game(this, game)) }
            .onFailure { toast("No se pudo abrir Cognitive Lab") }
    }

    /** v27: el próximo paso vive en Foco Core. */
    override fun stepAction() = openCore()

    override fun requestRole() {
        runCatching {
            val r = getSystemService(RoleManager::class.java)
            if (r.isRoleAvailable(RoleManager.ROLE_HOME) && !r.isRoleHeld(RoleManager.ROLE_HOME))
                roleLauncher.launch(r.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }.onFailure { openIntent(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    override fun pickDock(index: Int) {
        val cfg = Repository.get(this).config()
        val taken = vm.dock.toSet()
        val options =
            listOf(Section.PRODUCTIVA, Section.HERRAMIENTA).flatMap { s ->
                // De noche solo se ofrecen las esenciales (las únicas que el dock muestra a esa hora).
                vm.apps.filter { it.section == s && HomeSections.dockAllowed(cfg, it.pkg) && vm.dockAllowed(it.pkg) && it.pkg !in taken && it.pkg != packageName }
            }
        if (options.isEmpty())
            return toast(if (vm.night) "De noche solo caben apps esenciales; las demás se pueden fijar desde las 7:00." else "No hay apps disponibles para el dock.")
        AlertDialog.Builder(this)
            .setTitle("Fijar app en el dock")
            .setItems(options.map { it.label + if (it.section == Section.PRODUCTIVA) "  · productiva" else "" }.toTypedArray()) { _, w ->
                vm.setDock(index, options[w].pkg)
                toast("${options[w].label} fijada en tu dock")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Ajustes de Foco (v27: ahí viven también los del inicio). */
    override fun openFoco() =
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, 2))

    override fun notifications() {
        if (!FocusAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
            toast("Activa la Accesibilidad de Foco para usar este gesto.")
    }

    override fun lockScreen() {
        if (!FocusAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            toast("Activa la Accesibilidad de Foco para bloquear con doble toque.")
    }

    override fun openIntent(i: Intent) {
        runCatching { startActivity(i) }.onFailure { toast("Android no permite abrir esta pantalla aquí.") }
    }

    override fun setDnd(on: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        runCatching {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        vm.refreshSystem()
    }

    override fun fixDns() = openFoco()

    private fun homePrefs() = getSharedPreferences("foco-home", Context.MODE_PRIVATE)

    override fun chooseCompanion() {
        val all = CompanionMood.Variant.values()
        AlertDialog.Builder(this)
            .setTitle("Tu compañero en la colina")
            .setSingleChoiceItems(all.map { it.label }.toTypedArray(), all.indexOf(vm.variant)) { d, w ->
                vm.chooseVariant(all[w])
                d.dismiss()
            }
            .show()
    }

    override fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ===================== Cuidado =====================

    override fun openModes() {
        vm.refreshCare()
        showModes = true
    }

    /** CUIDADO siempre se puede elegir; antes, la pregunta de ánimo. */
    override fun chooseCare() {
        careMoodAsk = true
    }

    /** v32 (R8): en Enfoque o en el Weekend Lock, Cuidado pide una acción consciente con motivo. */
    private fun enterCare(motivo: String) {
        careMoodAsk = false
        val why = es.foco.next.runtime.CareStore.exitNotice(this)
        if (why == null) {
            applyCare(motivo)
            return
        }
        val motives = es.foco.next.runtime.CareStore.EXIT_MOTIVES
        AlertDialog.Builder(this)
            .setTitle(why)
            .setItems(motives.map { it[1] }.toTypedArray()) { _, w -> applyCare(motivo + ";motivo_" + motives[w][0]) }
            .setNegativeButton("Seguir como estoy", null)
            .show()
    }

    private fun applyCare(motivo: String) {
        // Cuidado con tiempo: al acabar vuelve solo al modo de antes. Tope de 2 h al día (3 h si el
        // ánimo de hoy es 1 o 2).
        val got = es.foco.next.runtime.CareStore.care(this, pendingCareMin, motivo)
        if (got <= 0) toast("Hoy ya usaste tu tiempo de Cuidado (2 h; 3 h si el ánimo está bajo).")
        else toast("Cuidado durante ${minutes(got)}. Luego vuelves a tu modo de antes.")
        vm.moodDone()
        vm.refreshCare()
    }

    override fun answerMood(score: Int, word: String): Boolean {
        val low = es.foco.next.runtime.CareStore.recordMood(this, score, word, "inicio")
        return low && vm.careMode != es.foco.next.core.care.CareMode.Modo.CUIDADO
    }

    override fun moodFromCard() {
        // El usuario tocó "Sí, modo Cuidado" tras contestar: ya respondió, no se pregunta otra vez.
        // Ánimo bajo: 1 h de Cuidado (tope diario de 3 h hoy); luego vuelve a tu modo. Ya es una
        // elección consciente con motivo (ánimo bajo).
        val got = es.foco.next.runtime.CareStore.care(this, 60, "animo_bajo")
        vm.moodDone()
        vm.refreshCare()
        toast(if (got > 0) "Modo Cuidado durante ${minutes(got)}. Vamos suave." else "Ahora no se pudo pasar a Cuidado.")
    }

    override fun skipMood() {
        es.foco.next.runtime.CareStore.skipMood(this)
        vm.moodDone()
    }

    override fun chooseMoodAgain() = vm.askMoodAgain()

    override fun breathe() {
        showBreathe = true
    }

    override fun owlBannerOpen(target: String) {
        when (target) {
            "reto" -> vm.quick?.let { openLab(it.game) }
            "respirar" -> showBreathe = true
            else -> stepAction()
        }
    }

    /** Temas fotográficos: automático o uno fijo. Los que aún no tienen foto se dibujan. */
    override fun openRoutine() {
        vm.refreshRoutine()
        if (vm.routineKind != null) showRoutine = true
    }

    /** v30: las rutinas se editan en Foco Core › Semana (sin escribir formatos). */
    override fun editRoutine(kind: es.foco.next.core.care.Routine.Kind) = openWeek()

    private fun openWeek() {
        vm.refreshCore()
        coreTab = 1
    }


    /** El domingo el búho invita a planificar: se abre Foco Core › Semana. */
    override fun planWeek() {
        es.foco.next.runtime.RoutineStore.planSeen(this)
        vm.refreshRoutine()
        openWeek()
    }

    // ---------------- Foco Core ----------------

    override fun openCore() {
        vm.refreshCore()
        // v30: siempre en Hoy; sin plan, el resumen lo dice y el botón ＋ Nueva tarea está a mano.
        coreTab = 0
    }

    override fun openCoreReports() {
        vm.refreshCore()
        coreTab = 2
    }

    override fun openStats() {
        runCatching { startActivity(Intent(this, StatsActivity::class.java)) }
    }

    /** Foco en la pestaña Bloqueos (3). */
    override fun openBlocks() =
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, 3))

    override fun startFreeHour() {
        if (es.foco.next.runtime.RoutineStore.startFree(this)) toast("1 h de ocio libre. Disfrútala.")
        else toast(if (es.foco.next.runtime.RoutineStore.freeUsedToday(this)) "La hora libre de hoy ya se usó." else "Ya no queda tiempo antes de la noche.")
        vm.refreshCore()
    }

    /** v30: «Hecha» marca al momento; si la tarea pide foto, primero la cámara. */
    override fun completeTask(block: String, index: Int) {
        val t = es.foco.next.runtime.RoutineStore.blockTasks(this, block, es.foco.next.runtime.RoutineStore.today()).getOrNull(index)
        if (t != null && t.photo) return photoTask(block, index)
        es.foco.next.runtime.RoutineStore.checkBlock(this, block, index, true)
        vm.refreshRoutine()
        vm.refreshCore()
    }

    /** Foto de evidencia: al volver de la cámara la tarea queda hecha con su foto. */
    override fun photoTask(block: String, index: Int) {
        photoFor = block to index
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.evidencias", cameraFile)
        runCatching { takePhoto.launch(uri) }.onFailure {
            photoFor = null
            es.foco.next.runtime.RoutineStore.checkBlock(this, block, index, true)
            vm.refreshRoutine()
            vm.refreshCore()
            toast("No hay cámara disponible: hecha, sin foto")
        }
    }

    override fun uncheckTask(block: String, index: Int) {
        es.foco.next.runtime.RoutineStore.checkBlock(this, block, index, false)
        vm.refreshRoutine()
    }

    override fun toggleTaskTimer(block: String, index: Int) {
        es.foco.next.runtime.RoutineStore.toggleTimer(this, block, index)
        vm.refreshCore()
    }

    override fun editEvents() = openWeek()



    /** Presupuesto y deuda (desde la tarjeta del gasto). */
    override fun editMoney() {
        val items = arrayOf(
            "Presupuesto de la semana · " + es.foco.next.core.care.Money.fmt(es.foco.next.runtime.MoneyStore.budget(this)),
            "Lo que debo · " + es.foco.next.core.care.Money.fmt(es.foco.next.runtime.MoneyStore.debt(this)),
            "Anotar un gasto",
        )
        AlertDialog.Builder(this)
            .setTitle("Gasto del día")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> askAmount("Presupuesto de la semana", "Lo que quieres gastar de lunes a domingo.", es.foco.next.runtime.MoneyStore.budget(this)) {
                        es.foco.next.runtime.MoneyStore.setBudget(this, it); vm.refreshMoney()
                    }
                    1 -> askAmount("Lo que debo", "El total pendiente.", es.foco.next.runtime.MoneyStore.debt(this)) {
                        es.foco.next.runtime.MoneyStore.setDebt(this, it); vm.refreshMoney()
                    }
                    else -> askAmount("Anotar un gasto", "Lo que gastaste o vas a gastar hoy.", 0) {
                        es.foco.next.runtime.MoneyStore.add(this, it, ""); vm.refreshMoney()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun askAmount(title: String, message: String, current: Long, then: (Long) -> Unit) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0.00"
            if (current > 0) setText(es.foco.next.core.care.Money.fmt(current).removePrefix("$"))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val t = input.text.toString()
                val cents = if (t.isBlank()) 0L else es.foco.next.core.care.Money.parse(t)
                if (cents < 0) toast("Escribe solo el monto, por ejemplo 12.50") else then(cents)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    override fun pickTheme() {
        // v31: Aurora y Mar al alba solo aparecen cuando la constancia los desbloquea.
        val have = es.foco.next.runtime.RewardStore.unlocked(this)
        val all = es.foco.next.core.home.PhotoTheme.values().filter { it.unlock() == null || it.unlock() in have }
        val labels = listOf("Automático (hora y lo que haces)") +
            all.map { it.label + " · " + it.`when` + if (it.photo == null) " (dibujado)" else "" }
        val current = if (vm.themeChoice == "AUTO") 0 else all.indexOfFirst { it.name == vm.themeChoice } + 1
        AlertDialog.Builder(this)
            .setTitle("Tema del inicio")
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, w ->
                vm.chooseTheme(if (w == 0) "AUTO" else all[w - 1].name)
                d.dismiss()
            }
            .show()
    }

    /** Horario de la noche: endurecer, desde ya; aflojar, desde mañana. */
    override fun nightHours() {
        val n = es.foco.next.runtime.CareStore.night(this)
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 24, 24, 0)
        }
        val start = android.widget.NumberPicker(this).apply {
            minValue = es.foco.next.core.care.NightHours.MIN_START
            maxValue = es.foco.next.core.care.NightHours.MAX_START
            value = n.start
            setFormatter { "$it:00" }
        }
        val end = android.widget.NumberPicker(this).apply {
            minValue = es.foco.next.core.care.NightHours.MIN_END
            maxValue = es.foco.next.core.care.NightHours.MAX_END
            value = n.end
            setFormatter { "$it:00" }
        }
        box.addView(start)
        box.addView(android.widget.TextView(this).apply { text = "  a  " })
        box.addView(end)
        AlertDialog.Builder(this)
            .setTitle("Horario de la noche")
            .setMessage("Proteger el sueño se aplica al momento. Soltar horas, desde mañana.")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                toast(es.foco.next.runtime.CareStore.requestNight(this, start.value, end.value))
                vm.refreshCare()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun owlAction(mode: es.foco.next.core.home.OwlBrain.Mode) {
        when (mode) {
            es.foco.next.core.home.OwlBrain.Mode.OCIO -> stepAction()
            es.foco.next.core.home.OwlBrain.Mode.LIBRO -> vm.readerApp?.let { openApp(it) }
            es.foco.next.core.home.OwlBrain.Mode.JUEGO -> vm.quick?.let { openLab(it.game) }
            es.foco.next.core.home.OwlBrain.Mode.BIBLIA -> vm.bibleApp?.let { openApp(it) }
            es.foco.next.core.home.OwlBrain.Mode.DESPERTAR -> openCore()
            es.foco.next.core.home.OwlBrain.Mode.LEVANTATE -> {
                // Sin minutos (Regla 4): solo un "bien hecho" y vuelve a la calma.
                toast("¡Bien hecho! Tu cuerpo te lo agradece.")
                vm.owlNext()
            }
            else -> vm.owlNext()
        }
    }

    override fun openShortPractice() {
        runCatching { startActivity(personal.qqq.braintraining.LabLaunch.shortSession(this, false)) }
            .onFailure { toast("No se pudo abrir la práctica") }
    }

    override fun openAllGames() {
        runCatching { startActivity(personal.qqq.braintraining.LabLaunch.full(this)) }
            .onFailure { toast("No se pudo abrir Cognitive Lab") }
    }

    override fun openPermission(kind: String) {
        when (kind) {
            "accessibility" -> openIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "home" -> requestRole()
            "usage" -> openIntent(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "notifications" -> openIntent(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            "dnd" -> openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    /** Abierta desde un mensaje de acompañamiento: mensaje_tocado y su destino. */
    private fun handleMessage(i: Intent?) {
        if (i == null) return
        // v27: Ajustes › Inicio de Foco abre aquí los editores que viven en el inicio.
        i.getStringExtra(EXTRA_HOME_ACTION)?.let { act ->
            i.removeExtra(EXTRA_HOME_ACTION)
            when (act) {
                "plan" -> planWeek()
                "rutina_manana" -> editRoutine(es.foco.next.core.care.Routine.Kind.MORNING)
                "rutina_noche" -> editRoutine(es.foco.next.core.care.Routine.Kind.NIGHT)
                "tema" -> pickTheme()
                "noche" -> nightHours()
                "animo" -> chooseMoodAgain()
            }
        }
        // Despertar con el búho (primer desbloqueo de la mañana) e inicio perdido.
        if (i.getBooleanExtra(es.foco.next.runtime.OwlWatch.EXTRA_WAKE, false)) {
            i.removeExtra(es.foco.next.runtime.OwlWatch.EXTRA_WAKE)
            if (!HomeCrashGuard.minimal(this)) {
                // El búho abre el día con Foco Core: la rutina de la mañana si toca, si no el plan de hoy.
                vm.refreshRoutine()
                if (vm.routineKind == es.foco.next.core.care.Routine.Kind.MORNING) showRoutine = true
                else { vm.refreshCore(); openCore() }
            }
        }
        if (i.getBooleanExtra(es.foco.next.runtime.RoutineStore.EXTRA_CORE, false)) {
            i.removeExtra(es.foco.next.runtime.RoutineStore.EXTRA_CORE)
            vm.refreshCore()
            coreTab = 0
        }
        if (i.getBooleanExtra(es.foco.next.runtime.RoutineStore.EXTRA_ROUTINE, false)) {
            i.removeExtra(es.foco.next.runtime.RoutineStore.EXTRA_ROUTINE)
            vm.refreshRoutine()
            if (vm.routineKind != null) showRoutine = true
        }
        if (i.getBooleanExtra(es.foco.next.runtime.OwlWatch.EXTRA_ROLE, false)) {
            i.removeExtra(es.foco.next.runtime.OwlWatch.EXTRA_ROLE)
            requestRole()
        }
        val id = i.getStringExtra(CompanionNotifier.EXTRA_ID) ?: return
        CompanionNotifier.tapped(this, id)
        CompanionNotifier.closeBanner(this, id) // ya lo leíste en la notificación
        val target = i.getStringExtra(CompanionNotifier.EXTRA_TARGET)
        i.removeExtra(CompanionNotifier.EXTRA_ID)
        window.decorView.postDelayed({
            // Cuidado: la sugerencia suave no abre nada (solo trae al inicio).
            if (target == "cuidado") return@postDelayed
            if (target == "respirar") {
                if (!HomeCrashGuard.minimal(this)) showBreathe = true
                return@postDelayed
            }
            if (target == "reto") vm.quick?.let { openLab(it.game) } else if (!HomeCrashGuard.minimal(this)) stepAction()
        }, 600)
    }

    companion object {
        /** Acción pedida desde Ajustes › Inicio de Foco (plan, rutinas, tema, noche, ánimo). */
        const val EXTRA_HOME_ACTION = "es.foco.next.extra.HOME_ACTION"
    }
}
