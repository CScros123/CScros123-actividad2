@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package es.foco.next.ui

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Avisos que piden acción: ámbar suave, nunca rojo. */
val WarnBg = Color(0xFFFEF3C7)
val WarnFg = Color(0xFF92400E)
val WarnStroke = Color(0xFFFDE68A)

/**
 * Etapa 5 con el diseño del prototipo: barra plegable "Protección activa". Siempre visible: escudo,
 * estado y píldora del DNS. Desplegada: No molestar, aviso de DNS y chips de estado (tocables:
 * abren la pantalla del sistema). Mantener pulsado: Ajustes del sistema.
 */
@Composable
fun ProtectionBar(
    s: SystemState,
    open: (Intent) -> Unit,
    onDnd: (Boolean) -> Unit,
    onFixDns: () -> Unit,
) {
    val c = LocalHome.current
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Cerrada: ⌄ (hacia abajo). Abierta: gira a ⌃ con un muelle suave.
    val arrow by animateFloatAsState(
        if (expanded) 180f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = .7f, stiffness = 400f),
        label = "flecha",
    )
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(if (c.card.alpha > .99f) c.card.copy(alpha = .96f) else c.card).border(1.dp, Color.White.copy(alpha = if (c.moon || c.darkCard) .07f else .6f), shape)) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = !expanded
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        open(Intent(Settings.ACTION_SETTINGS))
                    },
                    onClickLabel = "Ver controles",
                    onLongClickLabel = "Abrir Ajustes",
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(lerp(c.card, c.accent, .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = c.accent, modifier = Modifier.size(14.dp))
            }
            HomeText(
                if (s.accessibility) "Protección activa" else "Protección: activa Accesibilidad",
                13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            DnsPill(s.dnsOk, s.dnsWaiting.isNotEmpty())
            Icon(
                androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                if (expanded) "Cerrar controles" else "Ver controles",
                tint = c.muted,
                modifier = Modifier.padding(start = 6.dp).size(20.dp).graphicsLayer { rotationZ = arrow },
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth().background(lerp(c.card, c.background, .25f)).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    HomeText("No molestar", 13.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Switch(
                        s.dnd,
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDnd(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.card, uncheckedTrackColor = lerp(c.card, c.muted, .25f), uncheckedBorderColor = Color.Transparent, uncheckedThumbColor = c.card),
                    )
                }
                if (!s.dnsOk && s.dnsWaiting.isNotEmpty())
                    // Cambio pedido y ya puesto en el teléfono: una nota calmada, sin amarillo ni botón.
                    HomeText("DNS en cambio: Foco adopta tu nuevo proveedor el ${s.dnsWaiting}.", 11.5.sp, c.muted)
                else if (!s.dnsOk)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(WarnBg).border(1.dp, WarnStroke, RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeText("Filtro DNS privado inactivo", 11.5.sp, Color(0xFF78350F), modifier = Modifier.weight(1f))
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(WarnFg).clickable(onClick = onFixDns).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            HomeText("Revisar DNS", 11.sp, Color.White, FontWeight.SemiBold)
                        }
                    }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(s.wifi) { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                    Chip(if (s.batteryPct < 0) "Batería" else "${s.batteryPct} %" + if (s.charging) " ⚡" else "") { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
                    Chip("Accesibilidad: " + yes(s.accessibility)) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    Chip("Hora auto.: " + yes(s.autoTime)) { open(Intent(Settings.ACTION_DATE_SETTINGS)) }
                    Chip("Inicio predet.: " + yes(s.homeRole)) { open(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    Chip("VPN: " + yes(s.vpn)) { open(Intent(Settings.ACTION_VPN_SETTINGS)) }
                }
            }
        }
    }
}

private fun yes(b: Boolean) = if (b) "sí" else "no"

@Composable
fun DnsPill(ok: Boolean, waiting: Boolean = false) {
    val c = LocalHome.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.clip(shape)
            .background(if (ok || waiting) lerp(c.card, c.accent, .12f) else WarnBg)
            .border(1.dp, if (ok || waiting) Color.Transparent else WarnStroke, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        HomeText(
            if (ok) "✓ DNS: protegido" else if (waiting) "DNS: en cambio" else "⚠ DNS: revisar",
            11.sp, if (ok || waiting) c.accent else WarnFg, FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val c = LocalHome.current
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(lerp(c.card, c.accent, .09f)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp)) {
        HomeText(label, 11.sp, c.text, FontWeight.Medium)
    }
}

