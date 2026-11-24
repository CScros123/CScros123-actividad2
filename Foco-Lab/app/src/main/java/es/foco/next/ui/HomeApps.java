package es.foco.next.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import es.foco.next.core.AppConfig;
import es.foco.next.core.home.HomeSections;
import es.foco.next.core.home.HomeSections.Section;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Etapa 2.2: apps del cajón, leídas con LauncherApps (fuera del hilo principal). */
public final class HomeApps {
    public static final class App {
        public final String pkg, label;
        public final ComponentName component;
        public final UserHandle user;
        public final Drawable icon;
        public Section section;

        App(String pkg, String label, ComponentName component, UserHandle user, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
            this.user = user;
            this.icon = icon;
        }
    }

    private HomeApps() {}

    /** Bloqueante: llamar desde un hilo de fondo. Nunca lanza. */
    public static List<App> load(Context c, AppConfig config) {
        List<App> out = new ArrayList<>();
        try {
            LauncherApps la = c.getSystemService(LauncherApps.class);
            UserHandle me = Process.myUserHandle();
            for (LauncherActivityInfo i : la.getActivityList(null, me)) {
                String pkg = i.getComponentName().getPackageName();
                Drawable icon;
                try {
                    icon = i.getBadgedIcon(0);
                } catch (RuntimeException e) {
                    icon = null;
                }
                App a = new App(pkg, String.valueOf(i.getLabel()), i.getComponentName(), me, icon);
                a.section = HomeSections.of(config, pkg);
                out.add(a);
            }
        } catch (RuntimeException ignored) {
        }
        Collections.sort(
                out,
                (a, b) -> {
                    int s = HomeSections.order(a.section) - HomeSections.order(b.section);
                    return s != 0 ? s : HomeSections.fold(a.label).compareTo(HomeSections.fold(b.label));
                });
        return out;
    }

    /** Abre la app como lo haría el lanzador del sistema. Ocio: toque normal; el servicio aplica la regla de 5 min. */
    public static boolean open(Context c, App a) {
        try {
            c.getSystemService(LauncherApps.class).startMainActivity(a.component, a.user, null, null);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Filtra por la consulta y ordena por relevancia (estable dentro de cada nivel). */
    public static List<App> search(List<App> all, String query) {
        List<App> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return new ArrayList<>(all);
        for (int level = 3; level >= 1; level--)
            for (App a : all) if (HomeSections.score(a.label, query) == level) out.add(a);
        return out;
    }
}
