package personal.qqq.braintraining

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.*

@Composable
fun Settings(vm: BrainViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var reset by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val text = vm.backup()
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri, "wt")?.use {
                                it.write(text.toByteArray(Charsets.UTF_8))
                            } ?: error("Archivo no disponible")
                        }
                        vm.message = "Copia guardada."
                    } catch (e: Exception) {
                        vm.message =
                            "No se pudo exportar. Comprueba la carpeta y el espacio disponible."
                    } finally {
                        busy = false
                    }
                }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        importText =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    val bytes = it.readNBytes(25 * 1024 * 1024 + 1)
                                    require(bytes.size <= 25 * 1024 * 1024)
                                    bytes.toString(Charsets.UTF_8)
                                } ?: error("Archivo no disponible")
                            }
                    } catch (e: Exception) {
                        vm.message = "No se pudo abrir la copia. El límite es de 25 MB."
                    } finally {
                        busy = false
                    }
                }
        }
    if (reset)
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("¿Borrar tu historial?") },
            text = {
                Text(
                    "Se eliminarán resultados, niveles, rachas, aplicaciones registradas y recompensas de eventos. Exporta una copia si deseas conservarlos."
                )
            },
            confirmButton = {
                TextButton({
                    reset = false
                    vm.reset()
                }) {
                    Text("Borrar historial")
                }
            },
            dismissButton = { TextButton({ reset = false }) { Text("Cancelar") } },
        )
    if (importText != null)
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("¿Restaurar esta copia?") },
            text = {
                Text(
                    "Reemplazará el historial y los ajustes actuales. Solo se guardará si supera la validación."
                )
            },
            confirmButton = {
                TextButton({
                    val t = importText!!
                    importText = null
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { vm.restore(t) }
                            vm.message = "Copia restaurada."
                        } catch (e: Exception) {
                            vm.message =
                                "Copia rechazada. El archivo no es válido o no es compatible con esta versión."
                        } finally {
                            busy = false
                        }
                    }
                }) {
                    Text("Restaurar")
                }
            },
            dismissButton = { TextButton({ importText = null }) { Text("Cancelar") } },
        )
    if (about)
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("Cognitive") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Autor: qqq\nVersión: 1.5.0\nFecha: 2026\nIdioma: español\nUso personal, sin conexión."
                    )
                    Text(
                        "Los materiales originales se reservan para qqq, en la medida en que existan derechos aplicables. Las bibliotecas de terceros mantienen sus licencias originales."
                    )
                    Text(
                        "AndroidX / Compose / Room / Kotlin: Apache 2.0. Consulta los avisos incluidos en el código fuente.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton({ about = false }) { Text("Cerrar") } },
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header("A tu manera", back = back)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo oscuro", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Una pausa para tus ojos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    prefs.firstOrNull { it.key == "dark" }?.value == "true",
                    { vm.pref("dark", it.toString()) },
                )
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallIcon(Icons.Rounded.Tune, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Sonido y movimiento", style = MaterialTheme.typography.titleLarge)
            }
            ExperienceSwitch(
                "Sonidos",
                "Toques suaves, aciertos y final de sesión.",
                prefs.firstOrNull { it.key == "sound" }?.value != "false",
            ) {
                vm.pref("sound", it.toString())
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ExperienceSwitch(
                "Vibraciones",
                "Respuesta táctil al jugar, según los ajustes de tu teléfono.",
                prefs.firstOrNull { it.key == "haptics" }?.value != "false",
            ) {
                vm.pref("haptics", it.toString())
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ExperienceSwitch(
                "Animaciones",
                "Transiciones suaves y botones que responden al tocar.",
                prefs.firstOrNull { it.key == "motion" }?.value != "false",
            ) {
                vm.pref("motion", it.toString())
            }
            val preview = LocalFeedback.current
            OutlinedButton(
                { preview(FeedbackCue.COMPLETE) },
                Modifier.fillMaxWidth(),
                enabled =
                    prefs.firstOrNull { it.key == "sound" }?.value != "false" ||
                        prefs.firstOrNull { it.key == "haptics" }?.value != "false",
            ) {
                SmallIcon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Probar sonido y vibración")
            }
            Text(
                "El sonido respeta el volumen, el modo silencio y No molestar. Desactiva Animaciones si prefieres menos movimiento.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Fase 1: recordatorio diario de Lab desactivado; solo queda el de Foco (DailyReminder).
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aprender sin cronómetro", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Matemáticas, lógica y vida cotidiana. Revisa cada solución a tu ritmo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(vm.learningMode, { vm.pref("learning", it.toString()) })
            }
        }
        Panel {
            Text("Objetivo diario", style = MaterialTheme.typography.titleMedium)
            Text(
                "${vm.goal} minutos",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(3, 5, 8, 12, 20).forEach { n ->
                    FilterChip(
                        vm.goal == n,
                        { vm.pref("goal", n.toString()) },
                        label = { Text("$n min") },
                    )
                }
            }
            Text(
                "Si ya entrenaste hoy, el nuevo objetivo empieza el próximo día.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Panel {
            Text("Tus datos, bajo tu control", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sin cuentas, anuncios ni servidores. La copia es un archivo legible: elige almacenamiento local para mantenerla offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                { export.launch("Cognitive-${LocalDate.now()}.json") },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text("Exportar copia de seguridad")
            }
            OutlinedButton(
                {
                    restore.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text("Restaurar una copia")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Panel {
            Text("Zona de tus rachas", style = MaterialTheme.typography.titleMedium)
            Text(vm.zone.id, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Se fija al instalar para que viajar no cambie tus días anteriores.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton({ about = true }, Modifier.fillMaxWidth()) { Text("Acerca de · qqq") }
        TextButton({ reset = true }, Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Borrar historial", color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Cognitive\nHecho para qqq · 2026",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExperienceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked, change, modifier = Modifier.semantics { contentDescription = title })
    }
}
