package es.foco.next.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import es.foco.next.R;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class AppPicker {
    static String label(Context c, String pkg) {
        try {
            return c.getPackageManager()
                    .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }

    private static List<String> packages(MainActivity a) {
        Map<String, String> unique = new LinkedHashMap<>();
        es.foco.next.core.AppConfig config = a.repository.config();
        es.foco.next.runtime.BankAppGuard banks = new es.foco.next.runtime.BankAppGuard(a);
        for (ResolveInfo r :
                a.getPackageManager()
                        .queryIntentActivities(
                                new Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER),
                                0)) {
            String pkg = r.activityInfo.packageName;
            if (es.foco.next.core.FocusEngine.essential(config, pkg)
                    || banks.hide(config, pkg)
                    || pkg.equals(a.getPackageName())
                    || pkg.equals("com.android.settings")
                    || pkg.equals("com.android.systemui")
                    || pkg.contains("dialer")
                    || pkg.equals("com.android.phone")) continue;
            unique.put(pkg, label(a, pkg));
        }
        List<String> list = new ArrayList<>(unique.keySet());
        list.sort(Comparator.comparing(unique::get, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Apps del lanzador que el selector puede mostrar. */
    static List<String> installed(MainActivity a) {
        return packages(a);
    }

    static void many(MainActivity a, Set<String> original, Consumer<Set<String>> done) {
        many(a, original, null, null, done);
    }

    /**
     * Reglas absolutas: [locked] salen marcadas con candado y no se pueden desmarcar (solo las
     * instaladas se listan); [hidden] no se listan. Los dos pueden ser null.
     */
    static void many(
            MainActivity a,
            Set<String> original,
            Set<String> locked,
            Set<String> hidden,
            Consumer<Set<String>> done) {
        List<String> pkgs = packages(a);
        for (String p : original) if (!pkgs.contains(p)) pkgs.add(p);
        if (locked != null) pkgs.removeIf(p -> locked.contains(p) && !installedOrKnown(a, p));
        if (hidden != null) pkgs.removeIf(hidden::contains);
        String[] labels = new String[pkgs.size()];
        boolean[] checked = new boolean[pkgs.size()];
        Set<String> chosen = new HashSet<>(original);
        if (locked != null) chosen.addAll(locked);
        for (int i = 0; i < pkgs.size(); i++) {
            boolean lock = locked != null && locked.contains(pkgs.get(i));
            labels[i] = (lock ? "🔒 " : "") + label(a, pkgs.get(i));
            checked[i] = lock || chosen.contains(pkgs.get(i));
        }
        new AlertDialog.Builder(a)
                .setTitle(R.string.choose_apps)
                .setMultiChoiceItems(
                        labels,
                        checked,
                        (d, i, on) -> {
                            if (locked != null && locked.contains(pkgs.get(i))) {
                                // Candado: no se puede desmarcar.
                                if (!on) ((AlertDialog) d).getListView().setItemChecked(i, true);
                                return;
                            }
                            if (on) chosen.add(pkgs.get(i));
                            else chosen.remove(pkgs.get(i));
                        })
                .setPositiveButton(R.string.save, (d, n) -> done.accept(chosen))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean installedOrKnown(Context c, String pkg) {
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void one(MainActivity a, Consumer<String> done) {
        List<String> pkgs = packages(a);
        pkgs.removeIf(
                p ->
                        a.repository.config().watchedApps.contains(p)
                                || es.foco.next.core.AlternativeCatalog.social(p));
        String[] labels = pkgs.stream().map(p -> label(a, p)).toArray(String[]::new);
        new AlertDialog.Builder(a)
                .setTitle(R.string.choose_one_app)
                .setItems(labels, (d, i) -> done.accept(pkgs.get(i)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
