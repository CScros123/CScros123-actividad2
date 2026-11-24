package es.foco.next.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class StrictGuardTest {
    private static boolean d(String... t) {
        return StrictGuard.dangerous(Arrays.asList(t));
    }

    @Test
    public void focoAppInfoSpanish() {
        assertTrue(
                d(
                        "Información de la aplicación",
                        "Foco",
                        "Abrir",
                        "Desinstalar",
                        "Forzar detención"));
    }

    @Test
    public void focoAppInfoEnglish() {
        assertTrue(d("App info", "Foco", "Open", "Uninstall", "Force stop"));
    }

    @Test
    public void otherAppInfoIsFine() {
        assertFalse(
                d(
                        "Información de la aplicación",
                        "WhatsApp",
                        "Abrir",
                        "Desinstalar",
                        "Forzar detención"));
        assertFalse(d("App info", "Instagram", "Uninstall", "Force stop"));
    }

    @Test
    public void accessibilityWithFoco() {
        assertTrue(d("Accesibilidad", "Apps instaladas", "Foco", "Activado"));
        assertTrue(d("Foco", "Use Foco", "Accessibility"));
    }

    @Test
    public void accessibilityWithoutFocoIsFine() {
        assertFalse(d("Accesibilidad", "Mejoras de visibilidad", "TalkBack"));
    }

    @Test
    public void deviceAdminAndDns() {
        assertTrue(d("Apps de administración del dispositivo"));
        assertTrue(d("Device admin apps", "Find My Mobile"));
        assertTrue(d("DNS privado", "Nombre de host"));
        assertTrue(d("Private DNS"));
        assertTrue(d("Fecha y hora", "Fecha y hora automáticas"));
    }

    @Test
    public void uninstallDialog() {
        assertTrue(d("Foco", "¿Quieres desinstalar esta aplicación?", "Aceptar", "Cancelar"));
        assertTrue(d("Foco", "Do you want to uninstall this app?", "OK"));
    }

    @Test
    public void falsePositives() {
        assertFalse(d("Wi-Fi", "MiCasa", "Conectado"));
        assertFalse(d("WhatsApp", "Chats", "Foco en el trabajo hoy?"));
        assertFalse(d());
        assertFalse(StrictGuard.dangerous(null));
        assertFalse(d("Conexiones", "Bluetooth", "VPN"));
    }

    @Test
    public void packages() {
        assertTrue(StrictGuard.guardedPackage("com.android.settings"));
        assertTrue(StrictGuard.guardedPackage("com.google.android.packageinstaller"));
        assertFalse(StrictGuard.guardedPackage("com.whatsapp"));
        assertFalse(StrictGuard.guardedPackage(null));
    }

    private static boolean al(String... t) {
        return StrictGuard.dangerousAlways(Arrays.asList(t));
    }

    @Test
    public void alwaysCoversFocoScreens() {
        assertTrue(al("Información de la aplicación", "Foco", "Desinstalar", "Forzar detención"));
        assertTrue(al("Accesibilidad", "Apps instaladas", "Foco"));
        assertTrue(al("Apps de administración del dispositivo"));
        assertTrue(al("Foco", "¿Quieres desinstalar esta aplicación?", "Aceptar"));
    }

    @Test
    public void alwaysLeavesNetworkAndClock() {
        assertFalse(al("DNS privado", "Nombre de host"));
        assertFalse(al("Private DNS"));
        assertFalse(al("Fecha y hora", "Fecha y hora automáticas"));
        assertFalse(al("VPN", "Foco", "Añadir perfil VPN"));
        assertTrue(d("VPN", "Foco", "Añadir perfil VPN"));
        assertFalse(al("Información de la aplicación", "WhatsApp", "Desinstalar"));
    }

    @Test
    public void oneUiAccessibilityPageOfFocoIsDangerousAlways() {
        org.junit.Assert.assertTrue(
                StrictGuard.dangerousAlways(
                        java.util.Arrays.asList(
                                "Foco",
                                "Activado",
                                "Atajo de Foco",
                                "Información de la aplicación")));
        org.junit.Assert.assertTrue(
                StrictGuard.dangerousAlways(
                        java.util.Arrays.asList(
                                "¿Desea desactivar Foco?", "Cancelar", "Desactivar")));
        org.junit.Assert.assertFalse(
                StrictGuard.dangerousAlways(
                        java.util.Arrays.asList(
                                "WhatsApp",
                                "Activado",
                                "Atajo de WhatsApp",
                                "Información de la aplicación")));
    }

    @Test
    public void forceStopDialogOfFoco() {
        assertTrue(
                d(
                        "Foco",
                        "¿Forzar detención?",
                        "Si fuerzas la detención de una aplicación, es posible que no funcione correctamente.",
                        "Cancelar",
                        "Aceptar"));
        assertTrue(al("Foco", "¿Forzar detención?", "Cancelar", "Aceptar"));
        assertTrue(
                al(
                        "Foco",
                        "Force stop?",
                        "If you force stop an app, it may misbehave.",
                        "Cancel",
                        "OK"));
        assertTrue(al("App info", "Foco", "Force stop app?", "OK"));
        // El mismo diálogo para otra app no se toca.
        assertFalse(al("WhatsApp", "¿Forzar detención?", "Cancelar", "Aceptar"));
        assertFalse(d("Instagram", "Force stop?", "OK"));
    }
}
