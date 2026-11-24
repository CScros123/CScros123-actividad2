package es.foco.next.core;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lote 5: compras dentro de apps (mejor esfuerzo). Reconoce la hoja de pago de Google Play a partir
 * del paquete en primer plano, la clase de la ventana y sus textos. No lee nada fuera de
 * com.android.vending.
 */
public final class PurchaseGuard {
    private PurchaseGuard() {}

    public static final String PLAY = "com.android.vending";
    private static final Set<String> ACTIONS =
            new HashSet<>(
                    Arrays.asList(
                            "comprar",
                            "suscribirse",
                            "suscribirme",
                            "suscribete",
                            "pagar",
                            "compra con un toque",
                            "comprar con un toque",
                            "buy",
                            "subscribe",
                            "1-tap buy",
                            "one-tap buy",
                            "buy now",
                            "comprar ahora",
                            "pay"));

    /**
     * Botones de la ficha de una app: si aparecen sin clase de facturación, no es una hoja de pago.
     */
    private static final Set<String> STORE =
            new HashSet<>(
                    Arrays.asList(
                            "instalar",
                            "install",
                            "abrir",
                            "open",
                            "actualizar",
                            "update",
                            "desinstalar",
                            "uninstall"));

    private static final Pattern PRICE =
            Pattern.compile(
                    "(?:[$€£]|\\b(?:mxn|usd|eur|ars|cop|clp|pen)\\b)\\s?\\d|\\d(?:[.,]\\d{1,2})?\\s?(?:[$€£]|\\b(?:mxn|usd|eur|ars|cop|clp|pen)\\b)");

    /**
     * La regla protege compras ahora: activada, sin pausa y (sin horario o dentro de su franja),
     * como el filtro web.
     */
    public static boolean active(BlockRule r, RuleState s, ZonedDateTime now) {
        if (r == null || !r.enabled || !r.blockPurchases) return false;
        RuleState.Entry e = s.of(r.id);
        RuleEngine.roll(r, e, RuleEngine.millis(now), now.toLocalDate().toString());
        if (RuleEngine.paused(e, RuleEngine.millis(now))) return false;
        return RuleEngine.inWindow(r, now);
    }

    /** Primera regla que bloquea compras ahora, o null. */
    public static BlockRule blocker(List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules) if (active(r, s, now)) return r;
        return null;
    }

    static boolean billingClass(String cls) {
        if (cls == null) return false;
        String c = cls.toLowerCase(Locale.ROOT);
        return c.contains("billing")
                || c.contains("purchase")
                || c.contains("iab")
                || c.contains("acquire");
    }

    /** La ventana parece la hoja de pago de Google Play. */
    public static boolean isPurchase(String pkg, String windowClass, Collection<String> texts) {
        if (!PLAY.equals(pkg) || texts == null) return false;
        boolean action = false, price = false, play = false, store = false;
        for (String raw : texts) {
            if (raw == null) continue;
            String t = WebFilter.fold(raw).trim();
            if (t.isEmpty()) continue;
            if (ACTIONS.contains(t)) action = true;
            if (STORE.contains(t)) store = true;
            if (t.contains("google play")) play = true;
            if (t.length() <= 40 && PRICE.matcher(t).find()) price = true;
        }
        boolean billing = billingClass(windowClass);
        if (store && !billing) return false;
        return action && price && (play || billing);
    }
}
