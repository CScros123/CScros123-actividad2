package es.foco.next.ui

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.foco.next.core.care.Awareness
import es.foco.next.runtime.AwarenessStore
import es.foco.next.runtime.MediaPause
import es.foco.next.runtime.RoutineStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * v33: la ventana aparte de «¿Lo que veo ahora me aporta?». Es una actividad propia (no una capa
 * encima): al abrirse, la app de ocio pasa a segundo plano y se pausa aunque no obedezca la tecla
 * de pausa, y aquí el teclado funciona bien. Al decidir: «sí» devuelve a la app 5 min; «no» la deja
 * cerrada y lleva a algo útil (una app productiva al azar, tus tareas o el inicio).
 */
class AwarenessActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PKG = "es.foco.next.extra.AWARE_PKG"
        const val EXTRA_SESSION = "es.foco.next.extra.AWARE_SESSION"
        const val EXTRA_LEISURE = "es.foco.next.extra.AWARE_LEISURE"
        const val EXTRA_VIDEOS = "es.foco.next.extra.AWARE_VIDEOS"

        /** La abre la Accesibilidad. false si no se pudo. */
        @JvmStatic
        fun open(c: Context, pkg: String, sessionMs: Long): Boolean = open(c, pkg, sessionMs, 0L, 0)

        /**
         * v34: con el ocio de hoy (cambia el texto y cuánto dura el «sí»); v35: y los videos
         * vistos.
         */
        @JvmStatic
        fun open(
            c: Context,
            pkg: String,
            sessionMs: Long,
            leisureTodayMs: Long,
            videos: Int,
        ): Boolean =
            runCatching {
                    c.startActivity(
                        Intent(c, AwarenessActivity::class.java)
                            .putExtra(EXTRA_PKG, pkg)
                            .putExtra(EXTRA_SESSION, sessionMs)
                            .putExtra(EXTRA_LEISURE, leisureTodayMs)
                            .putExtra(EXTRA_VIDEOS, videos)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            )
                    )
                    true
                }
                .getOrDefault(false)
    }

    private var pkg = ""
    private var sessionMs = 0L
    private var leisureMs = 0L
    private var videos = 0
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        if (pkg.isEmpty()) {
            finish()
            return
        }
        sessionMs = intent.getLongExtra(EXTRA_SESSION, 0)
        leisureMs = intent.getLongExtra(EXTRA_LEISURE, 0)
        videos = intent.getIntExtra(EXTRA_VIDEOS, 0)
        // Doble seguro para el video: la tecla de pausa (si la app la obedece) y la música, que
        // pide
        // el foco de audio. Además esta ventana tapa la app y la deja en segundo plano.
        runCatching { MediaPause.pauseOthers(this) }
        AwarenessStore.musicOn(this)
        val label = AwarenessStore.label(this, pkg)
        val today = todayMs(pkg)
        val sinceNo = AwarenessStore.minutesSinceNo(this, pkg)
        val productive = AwarenessStore.randomProductive(this, pkg)
        val productiveLabel = productive?.let { AwarenessStore.label(this, it) }
        setContent {
            // v41: letra del modo juego en toda la ventana (los títulos piden Rubik aparte).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides
                    androidx.compose.ui.text.TextStyle(fontFamily = Lexend)
            ) {
                AwarenessScreen(
                    label = label,
                    sessionMs = sessionMs,
                    todayMs = today,
                    leisureMs = leisureMs,
                    videos = videos,
                    sinceNo = sinceNo,
                    productiveLabel = productiveLabel,
                    onNo = { answerNo() },
                    onYes = { kind, note -> answerYes(label, kind, note) },
                    onProductive = {
                        go(productive?.let { packageManager.getLaunchIntentForPackage(it) })
                    },
                    onTasks = {
                        go(
                            Intent()
                                .setClassName(this, "es.foco.next.ui.HomeActivity")
                                .putExtra(RoutineStore.EXTRA_CORE, true)
                        )
                    },
                    onAlternative = { what -> alternative(what) },
                    onHome = { goHome() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Otra pregunta mientras esta sigue abierta: se queda la que está (no se pierde lo
        // escrito).
    }

    override fun onDestroy() {
        // Sin decidir (se cerró de otra forma): la música no se queda sonando.
        if (!decided) AwarenessStore.musicOff(0)
        super.onDestroy()
    }

    private fun answerNo(): Int {
        decided = true
        val n = AwarenessStore.answerNo(this, pkg, sessionMs)
        AwarenessStore.musicOff(Awareness.MUSIC_MS) // la música sigue 3 min
        return n
    }

    private fun answerYes(label: String, kind: Awareness.Kind, note: String) {
        decided = true
        AwarenessStore.answerYes(
            this,
            pkg,
            label,
            kind,
            note,
            sessionMs,
            Awareness.extendMs(leisureMs),
        )
        AwarenessStore.musicOff(0)
        go(packageManager.getLaunchIntentForPackage(pkg))
    }

    private fun alternative(what: String) {
        es.foco.next.log.InspectorLog.event(
            this,
            es.foco.next.log.InspectorLog.ALTERNATIVA,
            "",
            System.currentTimeMillis(),
            0,
            1,
            0,
            "conciencia_$what",
            null,
        )
        if (what == "leer") {
            // Leer: una app de lectura instalada si la hay; si no, el inicio.
            val reader =
                listOf(
                        "com.google.android.apps.books",
                        "com.amazon.kindle",
                        "com.sirma.mobile.bible.android",
                    )
                    .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            if (reader != null) {
                go(reader)
                return
            }
        }
        goHome()
    }

    private fun goHome() {
        go(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun go(i: Intent?) {
        if (i == null) {
            goHome()
            return
        }
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    /** Tiempo de hoy en esa app (Acceso de uso); 0 si no hay permiso. */
    private fun todayMs(pkg: String): Long {
        if (!runCatching { es.foco.next.log.UsageContrast.allowed(this) }.getOrDefault(false))
            return 0L
        return runCatching {
                val start =
                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val usm = getSystemService(UsageStatsManager::class.java)
                usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())[pkg]
                    ?.totalTimeInForeground ?: 0L
            }
            .getOrDefault(0L)
    }
}

private val AwBg = Color(0xFF121014)
private val AwCardBg = Color(0xFF1C1A1F)
private val AwInk = Color(0xFFF2EEE9)
private val AwSoft = Color(0xFFB9B2AC)
private val AwCoral = Color(0xFFE8806F)
private val AwGreenBg = Color(0xFF2E3B34)
private val AwGreenLine = Color(0xFF3F6B52)
private val AwGreenInk = Color(0xFF9FE3B8)
private val AwChip = Color(0xFF26232A)
private val AwLine = Color(0xFF3A3640)

@Composable
private fun AwarenessScreen(
    label: String,
    sessionMs: Long,
    todayMs: Long,
    leisureMs: Long,
    videos: Int,
    sinceNo: Long,
    productiveLabel: String?,
    onNo: () -> Int,
    onYes: (Awareness.Kind, String) -> Unit,
    onProductive: () -> Unit,
    onTasks: () -> Unit,
    onAlternative: (String) -> Unit,
    onHome: () -> Unit,
) {
    var step by remember { mutableStateOf(AwarenessStep.ASK) }
    var decisions by remember { mutableIntStateOf(0) }
    BackHandler {
        when (step) {
            AwarenessStep.ASK -> {
                decisions = onNo()
                step = AwarenessStep.DONE
            }
            AwarenessStep.NOTE -> step = AwarenessStep.ASK
            AwarenessStep.DONE -> onHome()
        }
    }
    val bg =
        if (step == AwarenessStep.DONE)
            Brush.radialGradient(listOf(Color(0xFF2A3A33), Color(0xFF121417)))
        else Brush.verticalGradient(listOf(Color(0xFF2A1A20), AwBg))
    Box(
        Modifier.fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (step) {
            AwarenessStep.ASK ->
                Ask(
                    label,
                    sessionMs,
                    todayMs,
                    leisureMs,
                    videos,
                    sinceNo,
                    onNo = {
                        decisions = onNo()
                        step = AwarenessStep.DONE
                    },
                    onYes = { step = AwarenessStep.NOTE },
                )
            AwarenessStep.NOTE ->
                Note(
                    extendMin = Awareness.extendMs(leisureMs) / Awareness.MIN,
                    onYes = onYes,
                    onNo = {
                        decisions = onNo()
                        step = AwarenessStep.DONE
                    },
                )
            AwarenessStep.DONE ->
                Done(decisions, productiveLabel, onProductive, onTasks, onAlternative, onHome)
        }
    }
}

private enum class AwarenessStep {
    ASK,
    NOTE,
    DONE,
}

@Composable
private fun OwlBadge(size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFC8754A)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(es.foco.next.R.drawable.owl_face),
            null,
            Modifier.size((size * 0.78f).dp),
        )
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AwCardBg)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun Big(text: String, act: Act, enabled: Boolean = true, onClick: () -> Unit) {
    // v41: botón del modo juego (relieve y color de acción).
    GameButton(
        text,
        act,
        Modifier.fillMaxWidth().padding(top = 10.dp),
        enabled = enabled,
        height = 56.dp,
        onClick = onClick,
    )
}

@Composable
private fun MusicRow(text: String) {
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AwChip)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎵  $text", color = AwSoft, fontSize = 13.sp)
    }
}

@Composable
private fun Ask(
    label: String,
    sessionMs: Long,
    todayMs: Long,
    leisureMs: Long,
    videos: Int,
    sinceNo: Long,
    onNo: () -> Unit,
    onYes: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "⏸  Pausé $label",
                color = Color(0xFFFFD9CF),
                fontSize = 13.sp,
                modifier =
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (sinceNo >= 0) {
                Text(
                    "Volviste",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "Hace ${if (sinceNo < 1) "menos de 1" else sinceNo} min elegiste salir de $label.",
                    color = AwSoft,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                Text(
                    Awareness.duration(sessionMs),
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Rubik,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "seguidos en $label" +
                        if (todayMs > 0) " · hoy ${Awareness.duration(todayMs)}" else "",
                    color = AwSoft,
                    fontSize = 14.sp,
                )
            }
            // v35: cuántos videos y a qué ritmo (si se pudo medir).
            val pace = Awareness.videosText(sessionMs, videos)
            if (sinceNo < 0 && pace.isNotEmpty()) {
                Text(
                    pace,
                    color = AwSoft,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // v34: el tramo del día (desde 1 h de ocio hoy).
            val stage = Awareness.stageText(leisureMs)
            if (stage.isNotEmpty()) {
                Text(
                    stage,
                    color = Color(0xFFFFB4A2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
        Card {
            OwlBadge(54)
            Spacer(Modifier.height(10.dp))
            Text(
                "¿Te aporta?",
                color = AwInk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Rubik,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Big("No, ya está", Act.STOP, onClick = onNo)
            Big("Sí, me aporta", Act.GHOST, onClick = onYes)
            MusicRow("Música tranquila")
        }
    }
}

@Composable
private fun Note(extendMin: Long, onYes: (Awareness.Kind, String) -> Unit, onNo: () -> Unit) {
    var kind by remember { mutableStateOf(Awareness.Kind.APRENDO) }
    var text by remember { mutableStateOf("") }
    val words = Awareness.words(text)
    val ok = words >= Awareness.MIN_WORDS
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Card {
            Text(
                "¿Qué te aporta?",
                color = AwInk,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
            )
            Text(
                "Escríbelo con tus palabras. Se guarda en tu informe.",
                color = AwSoft,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in Awareness.Kind.values()) {
                    val on = k == kind
                    Text(
                        k.label,
                        color = if (on) AwGreenInk else AwInk,
                        fontSize = 12.5.sp,
                        modifier =
                            Modifier.clip(RoundedCornerShape(99.dp))
                                .background(AwChip)
                                .border(
                                    1.dp,
                                    if (on) Color(0xFF5FD08A) else AwLine,
                                    RoundedCornerShape(99.dp),
                                )
                                .clickable { kind = k }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121014))
                    .border(1.dp, AwGreenLine, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                if (text.isEmpty())
                    Text(
                        when (kind) {
                            Awareness.Kind.APRENDO -> "Estoy aprendiendo…"
                            Awareness.Kind.RELAJA -> "Me relaja porque…"
                            Awareness.Kind.CONECTA -> "Estoy hablando con… sobre…"
                        },
                        color = AwSoft.copy(alpha = .6f),
                        fontSize = 15.sp,
                    )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(600) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = AwInk, fontSize = 15.sp),
                    cursorBrush = SolidColor(AwGreenInk),
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${minOf(words, Awareness.MIN_WORDS)} de ${Awareness.MIN_WORDS} palabras",
                    color = AwSoft,
                    fontSize = 12.5.sp,
                )
                Text(
                    if (ok) "listo" else "faltan ${Awareness.MIN_WORDS - words}",
                    color = if (ok) AwGreenInk else AwSoft,
                    fontSize = 12.5.sp,
                )
            }
            val f = (words.toFloat() / Awareness.MIN_WORDS).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AwLine)
            ) {
                Box(
                    Modifier.fillMaxWidth(f)
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(AwCoral, Color(0xFFE9B44C), Color(0xFF5FD08A))
                            )
                        )
                )
            }
            Spacer(Modifier.height(6.dp))
            Big("Seguir $extendMin′", Act.GO, enabled = ok) { onYes(kind, text) }
            Text(
                "Mejor lo dejo",
                color = Color(0xFFFFB3A8),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp).clickable(onClick = onNo).padding(8.dp),
            )
        }
    }
}

@Composable
private fun Done(
    decisions: Int,
    productiveLabel: String?,
    onProductive: () -> Unit,
    onTasks: () -> Unit,
    onAlternative: (String) -> Unit,
    onHome: () -> Unit,
) {
    // Sin tocar nada en 30 s, al inicio (la música sigue sus 3 min).
    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        delay(30_000)
        if (SystemClock.elapsedRealtime() - start >= 29_000) onHome()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(
            Modifier.fillMaxWidth().padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OwlBadge(70)
            Text(
                "Buena decisión",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Rubik,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Card {
            Text(
                "¿Y ahora?",
                color = AwSoft,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((key, icon, name) in
                    listOf(
                        Triple("caminar", "🚶", "Caminar\ncon música"),
                        Triple("leer", "📖", "Leer"),
                        Triple("cocinar", "🍳", "Cocinar"),
                    )) {
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AwChip)
                            .clickable { onAlternative(key) }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, fontSize = 24.sp)
                        Text(
                            name,
                            color = AwInk,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (productiveLabel != null)
                Big("Abrir $productiveLabel", Act.GO, onClick = onProductive)
            Big("Mis tareas", Act.DONE, onClick = onTasks)
            Text(
                "+1 decisión consciente · llevas $decisions",
                color = Act.GO.face,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            MusicRow("Música tranquila · ${Awareness.MUSIC_MS / Awareness.MIN} min")
        }
    }
}
