package es.foco.next.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/**
 * Estados y canales de WhatsApp: el visor o «Novedades» con su lista bloquean; chats, llamadas y
 * comunidades nunca.
 */
public class WhatsAppSectionsTest {
    static final String WA = InAppFilter.WHATSAPP, W4B = InAppFilter.WHATSAPP_BUSINESS;

    static List<String> l(String... s) {
        return Arrays.asList(s);
    }

    static final List<String> NONE = Collections.emptyList();
    static final List<String> TABS = l("Chats", "Novedades", "Comunidades", "Llamadas");

    static String all(String pkg, List<String> ids, List<String> descs, List<String> selected) {
        return InAppFilter.detect(pkg, ids, descs, selected, null);
    }

    @Test
    public void statusViewerBlocksInBothApps() {
        assertEquals(
                "whatsapp_status",
                all(
                        WA,
                        l(
                                "com.whatsapp:id/status_playback_container",
                                "com.whatsapp:id/progress_bar"),
                        NONE,
                        NONE));
        assertEquals(
                "whatsapp_status", all(W4B, l("com.whatsapp.w4b:id/status_playback"), NONE, NONE));
        assertEquals("whatsapp_status", all(WA, l("status_viewer"), NONE, NONE));
        assertTrue(InAppFilter.supported(WA));
        assertTrue(InAppFilter.supported(W4B));
    }

    @Test
    public void updatesTabWithStatusListBlocks() {
        assertEquals(
                "whatsapp_status",
                all(
                        WA,
                        l("com.whatsapp:id/status_list", "com.whatsapp:id/my_status"),
                        TABS,
                        l("Novedades")));
        assertEquals(
                "whatsapp_status",
                all(
                        WA,
                        l("com.whatsapp:id/status_row"),
                        l("Chats", "Updates", "Calls"),
                        l("Updates, tab 2 of 4")));
    }

    @Test
    public void channelsBlock() {
        assertEquals(
                "whatsapp_channels", all(WA, l("com.whatsapp:id/newsletter_header"), NONE, NONE));
        assertEquals(
                "whatsapp_channels",
                all(W4B, l("com.whatsapp.w4b:id/newsletter_directory"), NONE, NONE));
        // «Novedades» con filas de canal y solo los canales pedidos.
        assertEquals(
                "whatsapp_channels",
                InAppFilter.detect(
                        WA,
                        l("com.whatsapp:id/newsletter_row"),
                        TABS,
                        l("Novedades"),
                        l("whatsapp_channels")));
        assertNull(
                InAppFilter.detect(
                        WA,
                        l("com.whatsapp:id/status_playback"),
                        NONE,
                        NONE,
                        l("whatsapp_channels")));
    }

    @Test
    public void chatsListNeverBlocks() {
        List<String> ids =
                l(
                        "com.whatsapp:id/conversations_row_contact_name",
                        "com.whatsapp:id/contact_photo",
                        "com.whatsapp:id/progress_bar",
                        "com.whatsapp:id/fab");
        // La etiqueta «Novedades» está siempre en la barra; sin seleccionar no cuenta.
        assertNull(all(WA, ids, TABS, l("Chats")));
        assertNull(
                all(
                        WA,
                        ids,
                        l("Chats", "Novedades", "Estado de Ana", "Canales"),
                        l("Chats, pestaña 1 de 4")));
        assertNull(all(W4B, ids, TABS, l("Chats")));
    }

    @Test
    public void conversationNeverBlocks() {
        // Dentro de un chat: descarga en curso (progress_bar) y un estado citado no son el visor.
        List<String> ids =
                l(
                        "com.whatsapp:id/conversation_layout",
                        "com.whatsapp:id/entry",
                        "com.whatsapp:id/progress_bar",
                        "com.whatsapp:id/quoted_status");
        assertNull(all(WA, ids, l("Estado", "Status", "Canales"), NONE));
        assertNull(all(WA, l("com.whatsapp:id/progress_bar"), NONE, NONE));
    }

    @Test
    public void callsAndCommunitiesNeverBlock() {
        assertNull(
                all(
                        WA,
                        l("com.whatsapp:id/call_row", "com.whatsapp:id/progress_bar"),
                        TABS,
                        l("Llamadas")));
        assertNull(
                all(
                        WA,
                        l("com.whatsapp:id/community_row", "com.whatsapp:id/progress_bar"),
                        TABS,
                        l("Comunidades")));
        assertNull(
                all(
                        WA,
                        l("com.whatsapp:id/voip_call_screen", "com.whatsapp:id/progress_bar"),
                        l("Llamada de voz"),
                        NONE));
    }

    @Test
    public void selectedUpdatesWithoutAnyListDoesNotBlock() {
        // Conservador: la pestaña sola, sin lista de estados o canales, no basta.
        assertNull(all(WA, l("com.whatsapp:id/toolbar"), TABS, l("Novedades")));
    }

    @Test
    public void quickWhatsAppToggle() {
        BlockRule on = InAppFilter.quickWhatsApp(null, true, "w");
        assertEquals(InAppFilter.QUICK_WHATSAPP_NAME, on.name);
        assertTrue(on.packages.isEmpty());
        assertFalse(on.scheduled());
        assertTrue(on.sections.containsAll(InAppFilter.WHATSAPP_SECTIONS));
        assertTrue(InAppFilter.quickOn(on, InAppFilter.WHATSAPP_SECTIONS));
        assertFalse("no enciende Shorts", InAppFilter.quickOn(on));
        BlockRule off = InAppFilter.quickWhatsApp(on, false, "x");
        assertFalse(off.enabled);
        assertFalse(RuleChange.isStricter(on, off));
        assertTrue(RuleChange.isStricter(null, on));
        assertEquals("Estados", InAppFilter.noun("whatsapp_status"));
        assertEquals("Canales", InAppFilter.noun("whatsapp_channels"));
    }

    @Test
    public void essentialAppsKeepSections() {
        assertTrue(FocusEngine.essential(new AppConfig(), WA));
        assertTrue(FocusEngine.essential(new AppConfig(), W4B));
        assertTrue(InAppFilter.sectionsInEssential(WA));
        assertTrue(InAppFilter.sectionsInEssential(W4B));
        assertFalse(InAppFilter.sectionsInEssential("com.android.dialer"));
        assertFalse(InAppFilter.sectionsInEssential(null));
        // La regla rápida sin apps: el bloqueo normal nunca toca WhatsApp, solo sus secciones.
        BlockRule r = InAppFilter.quickWhatsApp(null, true, "w");
        RuleState s = new RuleState();
        java.time.ZonedDateTime now =
                java.time.ZonedDateTime.of(2026, 11, 6, 21, 0, 0, 0, ScheduleTest.MAD);
        assertEquals(
                new LinkedHashSet<>(InAppFilter.WHATSAPP_SECTIONS),
                InAppFilter.wanted(Collections.singletonList(r), s, now, WA));
        assertEquals(
                new LinkedHashSet<>(InAppFilter.WHATSAPP_SECTIONS),
                InAppFilter.wanted(Collections.singletonList(r), s, now, W4B));
        assertTrue(
                InAppFilter.wanted(Collections.singletonList(r), s, now, InAppFilter.YOUTUBE)
                        .isEmpty());
    }
}
