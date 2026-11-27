package es.foco.next.runtime;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** Resolve the focused app, excluding accessibility overlays and keyboards. */
final class ForegroundWindows {
    /**
     * Punto de fuga cerrado: una app vigilada en imagen en imagen (PiP) o en la otra mitad de la
     * pantalla dividida no tiene el foco, así que antes no contaba. Si la app enfocada no está
     * vigilada y otra ventana de app visible sí, se mide esa.
     */
    static String current(
            AccessibilityService service,
            boolean overlayVisible,
            java.util.function.Predicate<String> watched) {
        String focused = current(service, overlayVisible);
        if (watched == null || (!focused.isEmpty() && watched.test(focused))) return focused;
        try {
            for (AccessibilityWindowInfo w : service.getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || w.isFocused())
                    continue;
                AccessibilityNodeInfo root = w.getRoot();
                try {
                    if (root == null || root.getPackageName() == null) continue;
                    String pkg = root.getPackageName().toString();
                    if (!pkg.equals(service.getPackageName()) && watched.test(pkg)) return pkg;
                } finally {
                    if (root != null) root.recycle();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return focused;
    }

    static String current(AccessibilityService service, boolean overlayVisible) {
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !w.isFocused())
                    continue;
                AccessibilityNodeInfo root = w.getRoot();
                try {
                    if (root != null && root.getPackageName() != null)
                        return root.getPackageName().toString();
                } finally {
                    if (root != null) root.recycle();
                }
            }
        } catch (RuntimeException ignored) {
        }
        AccessibilityNodeInfo root = null;
        try {
            root = service.getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) return "";
            String pkg = root.getPackageName().toString();
            return overlayVisible && pkg.equals(service.getPackageName()) ? "" : pkg;
        } catch (RuntimeException ignored) {
            return "";
        } finally {
            if (root != null) root.recycle();
        }
    }
}
