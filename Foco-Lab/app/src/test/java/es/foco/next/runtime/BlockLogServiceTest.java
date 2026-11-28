package es.foco.next.runtime;

import static org.junit.Assert.*;

import android.app.KeyguardManager;
import android.os.*;
import android.provider.Settings;
import android.view.accessibility.*;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.log.*;
import es.foco.next.ui.FocusOverlay;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/**
 * Fase 4: the block log is best effort. With the log forced to fail the block still appears, and
 * with a working log each lock and attempt is recorded with time and app. Same setup as
 * LiteRuntimeTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class BlockLogServiceTest {
    private FocusAccessibilityService s;

    private void window(String pkg) {
        AccessibilityWindowInfo w = AccessibilityWindowInfo.obtain();
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName(pkg);
        Shadows.shadowOf(w).setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        Shadows.shadowOf(w).setFocused(true);
        Shadows.shadowOf(w).setRoot(root);
        Shadows.shadowOf(s).setWindows(Collections.singletonList(w));
    }

    private void event(String pkg) {
        AccessibilityEvent e =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);
        s.onAccessibilityEvent(e);
    }

    private FocusOverlay overlay() {
        return ReflectionHelpers.getField(s, "overlay");
    }

    /**
     * Reglas absolutas, Regla 2: de 21:00 a 07:00, o con la hora automática apagada, todo el ocio
     * está cerrado. These tests are about the daytime 5-minute block, so they run on a weekday at
     * 10:00 with automatic time on, whatever the real clock says.
     */
    static void daytime() {
        Settings.Global.putInt(
                RuntimeEnvironment.getApplication().getContentResolver(),
                Settings.Global.AUTO_TIME,
                1);
        WallClock.fixForTests(
                java.time.LocalDateTime.of(2026, 11, 18, 10, 0)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()); // miércoles
    }

    private void start() {
        ReflectionHelpers.callStaticMethod(CalmAudio.class, "resetForTests");
        daytime();
        s = Robolectric.buildService(FocusAccessibilityService.class).create().get();
        s.getSharedPreferences("foco-soundscape", 0).edit().clear().commit();
        ReflectionHelpers.callStaticMethod(Repository.class, "resetForTests");
        s.deleteFile("focus-lite-v1.json");
        s.deleteFile("focus-state-v1.json");
        Settings.Global.putInt(s.getContentResolver(), Settings.Global.BOOT_COUNT, 3);
        Shadows.shadowOf(s.getSystemService(PowerManager.class)).setIsInteractive(true);
        Shadows.shadowOf(s.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
        Repository r = Repository.get(s);
        AppConfig c = r.config();
        c.watchedApps.add("feed.app");
        r.saveConfig(c);
        window("feed.app");
        ReflectionHelpers.callInstanceMethod(s, "onServiceConnected");
    }

    private void consume() {
        assertTrue(FocusAccessibilityService.connected());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5).plusMillis(500));
        assertTrue(Repository.get(s).locked());
    }

    private void leaveAndReturn() {
        window("com.android.settings");
        event("com.android.settings");
        assertFalse(overlay().visible());
        window("feed.app");
        event("feed.app");
    }

    @After
    public void stop() {
        WallClock.resetForTests();
        if (s != null) s.onDestroy();
        BlockLog.resetForTests();
    }

    @Test
    public void blockStillAppearsWhenEveryLogWriteThrows() {
        BlockLog.installForTests(
                Runnable::run,
                e -> {
                    throw new IllegalStateException("foco-log.db no disponible");
                });
        start();
        consume();
        assertTrue("la pausa aparece aunque el registro falle", overlay().visible());
        leaveAndReturn();
        assertTrue("y vuelve al reabrir la app bloqueada", overlay().visible());
        assertTrue("el registro sí falló", BlockLog.failuresForTests() >= 2);
    }

    @Test
    public void blockStillAppearsWhenTheLogThreadRejectsWork() {
        BlockLog.installForTests(
                r -> {
                    throw new RejectedExecutionException("cola llena");
                },
                e -> fail("no debería escribirse"));
        start();
        consume();
        assertTrue(overlay().visible());
        leaveAndReturn();
        assertTrue(overlay().visible());
    }

    @Test
    public void lockAndAttemptAreRecordedWithTimeAndApp() {
        List<BlockEvent> seen = new ArrayList<>();
        BlockLog.installForTests(Runnable::run, seen::add);
        long before = System.currentTimeMillis();
        start();
        consume();
        List<BlockEvent> locks = new ArrayList<>();
        for (BlockEvent e : seen) if (e.type == BlockEvent.Type.BUDGET_LOCK) locks.add(e);
        assertEquals(1, locks.size());
        assertEquals("feed.app", locks.get(0).pkg);
        assertTrue(locks.get(0).timestamp >= before);
        assertFalse(locks.get(0).day.isEmpty());
        assertEquals(FocusEngine.BUDGET, locks.get(0).budgetUsedMs);
        assertTrue(locks.get(0).windowLeftMs > 0);
        int attempts = 0;
        for (BlockEvent e : seen) if (e.type == BlockEvent.Type.ATTEMPT_WHILE_LOCKED) attempts++;
        assertEquals("el bloqueo inicial no cuenta como intento", 0, attempts);
        leaveAndReturn();
        assertTrue(overlay().visible());
        BlockEvent last = seen.get(seen.size() - 1);
        assertEquals(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, last.type);
        assertEquals("feed.app", last.pkg);
    }
}
