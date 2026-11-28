package es.foco.next.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * v27: los ajustes del inicio viven en Foco (Ajustes › Inicio). Los paneles laterales del inicio
 * quedan para lo interesante: a la izquierda Análisis (lo que haces, dentro y fuera del teléfono) y
 * a la derecha Practicar (juegos, Foco Core y enfoque). Las tareas viven solo en Foco Core.
 */

@Composable
private fun PanelTitle(title: String, sub: String) {
    val c = LocalHome.current
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            title,
            color = c.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-.5).sp,
        )
        HomeText(sub, 13.sp, lerp(c.text, c.muted, .5f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    // Jerarquía: etiquetas de sección pequeñas, espaciadas y discretas.
    Text(
        text.uppercase(),
        Modifier.padding(start = 4.dp, top = 6.dp),
        color = LocalHome.current.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun StatusRow(label: String, ok: Boolean, why: String, onClick: () -> Unit) {
    val c = LocalHome.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp)
                .clip(CircleShape)
                .background(if (ok) c.accent else WarnFg.copy(alpha = .8f))
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            HomeText(label, 13.5.sp, weight = FontWeight.Medium)
            HomeText(why, 11.5.sp, c.muted)
        }
        HomeText(
            if (ok) "Listo" else "Activar →",
            11.5.sp,
            if (ok) c.muted else c.accent,
            FontWeight.SemiBold,
        )
    }
}

/** Panel izquierdo "Análisis": permisos que faltan, tu día y tu semana, y los reportes. */
@Composable
fun AnalysisPanel(vm: HomeViewModel, a: HomeActions) {
    val s = vm.system
    val e = vm.estado
    // El contenido se corta limpio bajo la barra de estado (no pasa por detrás del reloj).
    Box(Modifier.fillMaxSize().statusBarsPadding().clipToBounds()) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PanelTitle("Análisis", "Lo que haces, dentro y fuera del teléfono.")
            // Permisos: invisibles si están todos; si falta alguno, aparecen solos arriba.
            val granted =
                listOf(s.accessibility, s.homeRole, s.usageAccess, s.notifications, s.dndAccess)
            if (!granted.all { it }) {
                SectionLabel("Falta un permiso")
                HomeCard {
                    Column {
                        if (!s.accessibility)
                            StatusRow(
                                "Accesibilidad",
                                false,
                                "Mide, protege y hace posibles los gestos.",
                            ) {
                                a.openPermission("accessibility")
                            }
                        if (!s.homeRole)
                            StatusRow(
                                "Inicio predeterminado",
                                false,
                                "Para que el botón Inicio abra Foco Home.",
                            ) {
                                a.openPermission("home")
                            }
                        if (!s.usageAccess)
                            StatusRow(
                                "Acceso de uso",
                                false,
                                "Minutos de hoy en el cajón y en los reportes.",
                            ) {
                                a.openPermission("usage")
                            }
                        if (!s.notifications)
                            StatusRow("Notificaciones", false, "Para que el búho pueda hablarte.") {
                                a.openPermission("notifications")
                            }
                        if (!s.dndAccess)
                            StatusRow(
                                "No molestar",
                                false,
                                "El interruptor de la barra de protección.",
                            ) {
                                a.openPermission("dnd")
                            }
                    }
                }
            }
            SectionLabel("Hoy y tu semana")
            TodayCard(
                e?.minutosInvertidosHoy ?: 0,
                e?.retosHoy ?: 0,
                vm.week,
                vm.showNumbers,
                vm.goodToday,
            )
            vm.core?.let { core ->
                HomeCard(Modifier.clickable(onClick = a::openCoreReports)) {
                    Column {
                        HomeText("Foco Core", 15.sp, weight = FontWeight.SemiBold)
                        HomeText(
                            "Tareas de hoy: ${core.done} de ${core.total}" +
                                (core.money?.let { " · $it" } ?: ""),
                            12.5.sp,
                            LocalHome.current.muted,
                        )
                        HomeText(
                            "Ver reportes y análisis del búho ›",
                            12.sp,
                            LocalHome.current.accent,
                            FontWeight.SemiBold,
                            Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // v50: lo que NextDNS bloqueó en la semana y los avisos de redes quitados de noche.
            if (vm.dnsWeek.isNotEmpty()) {
                SectionLabel("Tu semana con NextDNS")
                HomeCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.dnsWeek.forEach { HomeText(it, 13.sp, LocalHome.current.text) }
                    }
                }
            }
            SectionLabel("Reportes")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    Icons.Rounded.TaskAlt,
                    "Foco Core",
                    "Días, fotos y dinero",
                    Modifier.weight(1f),
                    a::openCoreReports,
                )
                Tile(
                    Icons.Rounded.Insights,
                    "Estadísticas",
                    "Tiempo y bloqueos",
                    Modifier.weight(1f),
                    a::openStats,
                )
            }
            PrimaryButton("Ajustes de Foco", onClick = a::openFoco)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Panel derecho "Practicar": agilizar la mente en Lab, Foco Core y enfoque. */
@Composable
fun PracticePanel(vm: HomeViewModel, a: HomeActions) {
    val c = LocalHome.current
    Box(Modifier.fillMaxSize().statusBarsPadding().clipToBounds()) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val next = vm.nextStep
            PanelTitle(
                "Practicar",
                if (next.isBlank()) "Dos minutos bastan para despertar la mente."
                else "Lo siguiente: $next",
            )
            SectionLabel("Mente")
            val q = vm.quick
            if (q != null) QuickCard(q, vm.night) { a.openLab(q.game) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    Icons.Rounded.Timer,
                    "Práctica 3 min",
                    "Corta, en tu nivel",
                    Modifier.weight(1f),
                    a::openShortPractice,
                )
                Tile(
                    Icons.Rounded.SportsEsports,
                    "Todos los juegos",
                    "Cognitive Lab",
                    Modifier.weight(1f),
                    a::openAllGames,
                )
            }
            SectionLabel("Tareas reales")
            Row(Modifier.fillMaxWidth()) {
                Tile(
                    Icons.Rounded.TaskAlt,
                    "Foco Core",
                    "2+2, dinero y reportes",
                    Modifier.weight(1f),
                    a::openCore,
                )
            }
            SectionLabel("Enfoque")
            HomeCard {
                Column {
                    HomeText("Sesión de enfoque", 14.5.sp, weight = FontWeight.SemiBold)
                    HomeText("Bloqueos con nombre y Pomodoro viven en Foco.", 12.5.sp, c.muted)
                    Text(
                        "Bloqueos y Pomodoro en Foco →",
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = a::openBlocks)
                            .padding(top = 10.dp, bottom = 4.dp),
                        color = c.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Tile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalHome.current
    Column(
        modifier
            .clip(CardShape)
            .background(if (c.card.alpha > .99f) c.card.copy(alpha = .96f) else c.card)
            .border(
                1.dp,
                Color.White.copy(alpha = if (c.moon || c.darkCard) .07f else .55f),
                CardShape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        IconBadge(icon, 30)
        Spacer(Modifier.height(8.dp))
        HomeText(title, 13.5.sp, weight = FontWeight.SemiBold)
        HomeText(sub, 11.5.sp, c.muted)
    }
}
