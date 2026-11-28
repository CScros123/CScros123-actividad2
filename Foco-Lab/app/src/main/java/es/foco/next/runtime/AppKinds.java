package es.foco.next.runtime;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import es.foco.next.core.AbsoluteRules;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Corrección B: apps que no son ni ocio ni productivas.
 *
 * <ul>
 *   <li>Excluidas de todo registro y categoría: lanzadores (resuelven CATEGORY_HOME),
 *       com.android.systemui, teclados y Foco.
 *   <li>Neutrales: música y audio (ApplicationInfo.CATEGORY_AUDIO), que no ganan minutos.
 * </ul>
 *
 * Las redes sociales de la Regla 1 nunca son excluidas ni neutrales. Cachés baratas: se consultan
 * en cada evento. Nunca lanza.
 */
public final class AppKinds {
    private AppKinds() {}

    private static volatile Set<String> fixed = new HashSet<>();
    private static volatile long fixedAt = -1;
    private static final Map<String, Integer> categories = new ConcurrentHashMap<>();

    private static Set<String> fixed(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (fixedAt >= 0 && now - fixedAt < 10 * 60_000L) return fixed;
        Set<String> out = new HashSet<>();
        out.add(c.getPackageName());
        out.add("com.android.systemui");
        try {
            for (ResolveInfo r :
                    c.getPackageManager()
                            .queryIntentActivities(
                                    new Intent(Intent.ACTION_MAIN)
                                            .addCategory(Intent.CATEGORY_HOME),
                                    0))
                if (r.activityInfo != null) out.add(r.activityInfo.packageName);
        } catch (RuntimeException ignored) {
        }
        try {
            InputMethodManager imm = c.getSystemService(InputMethodManager.class);
            if (imm != null)
                for (InputMethodInfo i : imm.getInputMethodList()) out.add(i.getPackageName());
        } catch (RuntimeException ignored) {
        }
        fixed = out;
        fixedAt = now;
        return out;
    }

    /** Tras instalar o quitar apps: vuelve a leer lanzadores y teclados. */
    public static void refresh() {
        fixedAt = -1;
        categories.clear();
    }

    /** Categoría de Android (ApplicationInfo.category) o −1 si no se sabe. */
    public static int category(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return -1;
        Integer known = categories.get(pkg);
        if (known != null) return known;
        int cat;
        try {
            cat = c.getPackageManager().getApplicationInfo(pkg, 0).category;
        } catch (Exception e) {
            cat = ApplicationInfo.CATEGORY_UNDEFINED;
        }
        if (categories.size() > 500) categories.clear();
        categories.put(pkg, cat);
        return cat;
    }

    /** Lanzador, barra del sistema, teclado o Foco: fuera de todo registro y categoría. */
    public static boolean excluded(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty() || AbsoluteRules.social(pkg)) return false;
        try {
            return fixed(c.getApplicationContext()).contains(pkg);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Música y audio: ni ocio ni productivas. */
    public static boolean neutral(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty() || AbsoluteRules.social(pkg)) return false;
        return AbsoluteRules.neutral(category(c, pkg));
    }

    /** Excluida o neutral: no se registra como ocio ni como uso productivo. */
    public static boolean ignored(Context c, String pkg) {
        return excluded(c, pkg) || neutral(c, pkg);
    }

    /**
     * Hora automática del sistema (Settings.Global.AUTO_TIME). Si no se puede leer, se supone
     * encendida (sin falsos bloqueos).
     */
    public static boolean autoTime(Context c) {
        try {
            return android.provider.Settings.Global.getInt(
                            c.getContentResolver(), android.provider.Settings.Global.AUTO_TIME, 1)
                    != 0;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
