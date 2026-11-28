package personal.qqq.braintraining

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*

@Composable
fun EventsScreen(vm: BrainViewModel) {
    val events by vm.events.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var application by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val df = remember { DateTimeFormatter.ofPattern("d MMM", Locale("es")) }
    if (application != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) application = null },
            title = { Text("Lo apliqué en mi día") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Cuenta qué hiciste y qué resultado obtuviste. Puede ser algo pequeño. Este registro es personal y se guarda en tu teléfono."
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        note,
                        { note = it.take(500) },
                        label = { Text("Mi aplicación real") },
                        minLines = 3,
                        maxLines = 5,
                        supportingText = {
                            Text("${note.trim().length}/500 · mínimo 10 caracteres")
                        },
                        enabled = !busy,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    {
                        val date = LocalDate.parse(application)
                        busy = true
                        scope.launch {
                            try {
                                val added = vm.registerApplication(date, note)
                                application = null
                                note = ""
                                vm.message =
                                    if (added)
                                        "Aplicación registrada. Tus recompensas se añadieron una sola vez."
                                    else "La aplicación de este día ya estaba registrada."
                            } catch (e: Exception) {
                                error =
                                    "No se pudo guardar. Comprueba que sigues en el día del reto y vuelve a intentarlo."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && note.trim().length >= 10,
                ) {
                    Text(if (busy) "Guardando…" else "Guardar aplicación")
                }
            },
            dismissButton = {
                TextButton({ application = null }, enabled = !busy) { Text("Cancelar") }
            },
        )
    }
    fun registerToday() {
        application = events.today.toString()
        note = ""
        error = null
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("events-list"),
        contentPadding = PaddingValues(22.dp, 18.dp, 22.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header("Retos y recompensas", "Aprende algo y úsalo en tu realidad") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallIcon(
                        Icons.Rounded.WorkspacePremium,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${events.bonusXp} XP de eventos",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    "${events.journal.size} retos completados · ${events.badges.size} insignias",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Las recompensas reconocen la práctica y lo que registras. Descansar o dejar pasar un reto no te quita puntos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Diarios", "Semanales", "Registro").forEachIndexed { i, title ->
                    FilterChip(tab == i, { tab = i }, label = { Text(title) })
                }
            }
        }
        if (tab < 2) {
            val missions = if (tab == 0) events.daily else events.weekly
            item {
                Text(
                    if (tab == 0) "Hoy · ${events.today.format(df)}"
                    else
                        "Semana · ${events.weekly.first().start.format(df)} – ${events.weekly.first().end.format(df)}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(missions, key = { it.id }) { mission ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(if (mission.practical) "APLICACIÓN REAL" else "PRÁCTICA GUIADA")
                            Text(mission.title, style = MaterialTheme.typography.titleLarge)
                        }
                        if (mission.complete)
                            SmallIcon(
                                Icons.Rounded.CheckCircle,
                                "Reto completado",
                                MaterialTheme.colorScheme.primary,
                            )
                    }
                    Text(mission.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { mission.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${mission.progress}/${mission.target} ${mission.unit}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "+${mission.reward} XP${if(mission.complete)" · obtenidos" else " al completar"}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (mission.badge != null)
                        Text(
                            "Insignia: ${mission.badge}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    if (!mission.complete) {
                        if (mission.practical) {
                            val available = events.daily.last().complete.not()
                            OutlinedButton(
                                { registerToday() },
                                Modifier.fillMaxWidth(),
                                enabled = available,
                            ) {
                                Text(if (available) "Registrar aplicación" else "Continúa otro día")
                            }
                        } else Primary("Empezar reto", { mission.game?.let { vm.start(it) } })
                    }
                }
            }
            item {
                Text(
                    if (tab == 0)
                        "Se renuevan al cambiar el día. Las sesiones cuentan en la fecha en que terminan."
                    else
                        "La semana va de lunes a domingo. Los días de práctica no tienen que ser consecutivos. Las recompensas se guardan al cumplir el objetivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { Text("Tu registro de recompensas", style = MaterialTheme.typography.titleLarge) }
            if (events.journal.isEmpty())
                item {
                    Panel {
                        Text("Tu primer reto completado aparecerá aquí.")
                        Text(
                            "Empieza con un juego o registra una aplicación concreta en tu día.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            if (events.badges.isNotEmpty())
                item {
                    Panel {
                        Label("TUS INSIGNIAS")
                        events.badges.forEach {
                            Text("✓ $it", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            items(events.journal.take(200), key = { it.id }) { mission ->
                Panel {
                    Label(
                        if (mission.period == EventPeriod.DAILY) "RETO DIARIO" else "RETO SEMANAL"
                    )
                    Text(mission.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "+${mission.reward} XP · ${Instant.ofEpochMilli(mission.completedAt!!).atZone(vm.zone).format(DateTimeFormatter.ofPattern("d MMM · HH:mm",Locale("es")))}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    mission.badge?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (mission.practical && mission.period == EventPeriod.DAILY) {
                        val prefs by vm.preferences.collectAsStateWithLifecycle()
                        val saved = prefs.firstOrNull { it.key == "practice:${mission.start}" }
                        val text =
                            remember(saved?.value) {
                                saved?.value?.let {
                                    runCatching { org.json.JSONObject(it).getString("note") }
                                        .getOrNull()
                                }
                            }
                        if (text != null)
                            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(
                    "El registro completo se conserva en tu copia de seguridad. Aquí se muestran las últimas 200 recompensas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
