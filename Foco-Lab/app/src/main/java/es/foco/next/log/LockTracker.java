package es.foco.next.log;

import java.time.ZoneId;

/**
 * Fase 4: turns the service's consecutive engine decisions into log events, without touching
 * FocusEngine.
 *
 * <ul>
 *   <li>{@code BUDGET_LOCK}: the remaining budget goes from &gt;0 to 0 inside a window. App = the
 *       app that consumed the last minutes (the one observed in the previous decision).
 *   <li>{@code ATTEMPT_WHILE_LOCKED}: the pause is shown for a locked app and it was not already
 *       shown for that same app (minute refreshes are not attempts).
 * </ul>
 *
 * Pure Java. Not thread-safe: the service uses it only from the main thread.
 */
public final class LockTracker {
    private long lastBudget = -1;
    private String lastPkg = "", blockedPkg = "";

    /**
     * @param pkg app evaluated now (foreground)
     * @param blocked the engine decided to block it now
     * @param remainingBudget engine's remaining budget (ms)
     * @param left remaining window (ms)
     * @param pauseShownBefore the full pause (not the warning) was visible before this decision
     * @param budget total budget (ms), to compute the used part
     * @return the event to log, or null
     */
    public BlockEvent next(
            String pkg,
            boolean blocked,
            long remainingBudget,
            long left,
            boolean pauseShownBefore,
            long budget,
            long wall,
            ZoneId zone) {
        String now = pkg == null ? "" : pkg;
        BlockEvent out = null;
        boolean crossed = lastBudget > 0 && remainingBudget <= 0 && left > 0;
        if (crossed) {
            String consumer = lastPkg.isEmpty() ? now : lastPkg;
            out =
                    new BlockEvent(BlockEvent.Type.BUDGET_LOCK, wall, zone)
                            .pkg(consumer)
                            .budgetUsed(budget - Math.max(0, remainingBudget))
                            .windowLeft(left)
                            .lock(left);
        } else if (blocked && (!pauseShownBefore || !now.equals(blockedPkg))) {
            out =
                    new BlockEvent(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, wall, zone)
                            .pkg(now)
                            .budgetUsed(budget - Math.max(0, remainingBudget))
                            .windowLeft(left)
                            .lock(left);
        }
        lastBudget = remainingBudget;
        blockedPkg = blocked ? now : "";
        lastPkg = now;
        return out;
    }
}
