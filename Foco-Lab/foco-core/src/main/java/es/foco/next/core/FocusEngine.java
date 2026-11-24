package es.foco.next.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Event-driven five-minute budget in a fixed rolling hour. No polling or Android dependency. */
public final class FocusEngine {
    public static final long BUDGET = 5 * 60_000L, HOUR = 60 * 60_000L, WARNING = 4 * 60_000L;
    public static final long CHECKPOINT = 60_000L;

    public static final class Input {
        public String pkg = "", day = "";
        public long wall, elapsed, millisToday;
        public int boot = -1;
        public boolean interactive, essential, inCall;

        /**
         * Puerta de Foco Core (v26; v32 sin restos del banco): con [gated] no se abre ninguna
         * ventana de ocio nueva (noBalance). El servicio la activa en Enfoque y en el Weekend Lock
         * (salvo Cuidado); la hora libre y los cupones pasan por fuera del motor.
         */
        public boolean gated;

        /**
         * Modo Normal: enfriamiento tras gastar los 5 minutos (0 = la ventana de 1 hora de siempre).
         * Al agotarse, la ventana termina como mucho en este tiempo. Solo acorta la espera.
         */
        public long cooldownMs;
    }

    /** Enfriamiento del modo Normal. */
    public static final long NORMAL_COOLDOWN = 30 * 60_000L;

    public static final class Decision {
        public final boolean blocked, warning;
        public final long left, remainingBudget, next;

        /** Cerrada por la puerta de Foco Core (sin hora libre ni cupón). */
        public final boolean noBalance;

        Decision(boolean blocked, boolean warning, long left, long budget, long next) {
            this(blocked, warning, left, budget, next, false);
        }

        Decision(boolean blocked, boolean warning, long left, long budget, long next, boolean noBalance) {
            this.blocked = blocked;
            this.warning = warning;
            this.left = left;
            this.remainingBudget = budget;
            this.next = next;
            this.noBalance = noBalance;
        }
    }

    public static boolean essential(AppConfig c, String pkg) {
        if (Categories.totalBlock(pkg) || AbsoluteRules.social(pkg))
            return false; // bloqueo total y Regla 1: nunca esenciales
        return pkg == null
                || pkg.isEmpty()
                || SensitiveApps.isSensitive(c, pkg)
                || pkg.equals("es.foco.next")
                || pkg.equals("com.android.settings")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.whatsapp")
                || pkg.equals("com.whatsapp.w4b")
                || pkg.equals("com.android.phone")
                || pkg.equals("com.android.server.telecom")
                || pkg.equals("com.android.emergency")
                || pkg.contains("dialer")
                || pkg.contains("incallui");
    }

    /** Con límite de 5 minutos: ocio o en observación (Foco 1.0, etapa 2). */
    public static boolean watched(AppConfig c, String pkg) {
        return AbsoluteRules.social(pkg)
                || (c.watchedApps.contains(pkg) || c.observedApps.contains(pkg))
                        && !essential(c, pkg)
                        && !pkg.equals(c.returnApp);
    }

    public static long remaining(FocusState s, long wall, long elapsed, int boot) {
        return s.window.remaining(wall, elapsed, boot, HOUR);
    }

    public static boolean locked(FocusState s, long wall, long elapsed, int boot) {
        return s.usedMillis >= BUDGET && remaining(s, wall, elapsed, boot) > 0;
    }

    public static void forget(FocusState s) {
        s.observedPackage = "";
        s.observedElapsed = -1;
        s.observedBoot = -1;
        s.observedAlternative = false;
    }

    public static void record(
            Map<String, Map<String, Long>> history, String day, String pkg, long delta) {
        if (delta <= 0 || day.isEmpty()) return;
        Map<String, Long> row = history.computeIfAbsent(day, k -> new LinkedHashMap<>());
        row.put(pkg, Math.min(24 * HOUR, row.getOrDefault(pkg, 0L) + delta));
    }

    public static void prune(FocusState s, String day) {
        LocalDate today = LocalDate.parse(day);
        String first = today.minusDays(6).toString();
        for (Map<String, ?> map :
                java.util.Arrays.asList(
                        s.weeklyOcio, s.weeklyAlternative, s.weeklyChoices, s.weeklyBlocks))
            map.keySet().removeIf(k -> k.compareTo(first) < 0 || k.compareTo(day) > 0);
    }

    public Decision step(AppConfig c, FocusState s, Input in) {
        String previousDay = s.day;
        boolean newDay = !in.day.equals(s.day);
        if (newDay) {
            s.day = in.day;
            s.dailyPackageMillis.clear();
        }
        long delta =
                s.observedElapsed >= 0 && s.observedBoot == in.boot
                        ? in.elapsed - s.observedElapsed
                        : 0;
        // If callbacks were suspended for a long time, don't invent unseen foreground usage.
        if (delta < 0 || delta > CHECKPOINT + 5_000) delta = 0;
        if (delta > 0 && !s.observedPackage.isEmpty()) {
            long today = newDay ? Math.min(delta, Math.max(0, in.millisToday)) : delta;
            Map<String, Map<String, Long>> history =
                    s.observedAlternative ? s.weeklyAlternative : s.weeklyOcio;
            record(history, in.day, s.observedPackage, today);
            if (newDay && !previousDay.isEmpty())
                record(history, previousDay, s.observedPackage, delta - today);
            if (!s.observedAlternative) {
                long prior = s.dailyPackageMillis.getOrDefault(s.observedPackage, 0L);
                s.dailyPackageMillis.put(s.observedPackage, Math.min(24 * HOUR, prior + today));
            }
            long end =
                    s.window.boot >= 0 && s.window.boot == in.boot
                            ? s.window.elapsed
                            : s.window.wall;
            long now = s.window.boot >= 0 && s.window.boot == in.boot ? in.elapsed : in.wall;
            long within = Math.max(0, delta - Math.max(0, now - end));
            if (!s.observedAlternative) s.usedMillis = Math.min(BUDGET, s.usedMillis + within);
        }
        long left = remaining(s, in.wall, in.elapsed, in.boot);
        if (s.window.present() && left == 0) {
            s.window.clear();
            s.usedMillis = 0;
            s.warningSent = false;
            s.lockedApps.clear();
        }
        boolean eligible = in.interactive && !in.inCall && !in.essential && !essential(c, in.pkg);
        // Regla 1: las redes sociales cuentan aunque el plan esté desactivado.
        boolean observed =
                eligible && (c.enabled || AbsoluteRules.social(in.pkg)) && watched(c, in.pkg);
        // Puerta de Foco Core: con ella no se abre una ventana nueva.
        boolean noBalance = false;
        if (observed && !s.window.present()) {
            if (in.gated) noBalance = true;
            else {
                s.window.start(in.wall, in.elapsed, in.boot, HOUR);
                left = HOUR;
            }
        }
        if (s.usedMillis >= BUDGET && left > 0 && s.lockedApps.isEmpty()) {
            s.lockedApps.addAll(c.watchedApps);
            s.lockedApps.addAll(c.observedApps);
            s.lockedApps.addAll(AbsoluteRules.SOCIAL_PACKAGES);
            s.weeklyBlocks.put(in.day, s.weeklyBlocks.getOrDefault(in.day, 0L) + 1);
            // Enfriamiento: al agotar los 5 minutos, la pausa dura como mucho cooldownMs.
            if (in.cooldownMs > 0 && left > in.cooldownMs) {
                s.window.start(in.wall, in.elapsed, in.boot, in.cooldownMs);
                left = in.cooldownMs;
            }
        }
        prune(s, in.day);
        boolean blocked =
                noBalance
                        || (eligible
                                && left > 0
                                && s.usedMillis >= BUDGET
                                && s.lockedApps.contains(in.pkg));
        boolean warning = observed && !blocked && !s.warningSent && s.usedMillis >= WARNING;
        if (warning) s.warningSent = true;
        forget(s);
        long budget = Math.max(0, BUDGET - s.usedMillis), next = 0;
        if (blocked) next = left;
        else if (eligible && c.enabled && !c.returnApp.isEmpty() && in.pkg.equals(c.returnApp)) {
            s.observedPackage = in.pkg;
            s.observedElapsed = in.elapsed;
            s.observedBoot = in.boot;
            s.observedAlternative = true;
            next = CHECKPOINT;
        } else if (observed) {
            s.observedPackage = in.pkg;
            s.observedElapsed = in.elapsed;
            s.observedBoot = in.boot;
            next = Math.min(CHECKPOINT, Math.min(left, budget));
            if (!s.warningSent && s.usedMillis < WARNING)
                next = Math.min(next, WARNING - s.usedMillis);
        }
        return new Decision(blocked, warning, left, budget, next, noBalance);
    }
}
