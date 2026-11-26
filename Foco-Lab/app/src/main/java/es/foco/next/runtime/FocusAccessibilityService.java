package es.foco.next.runtime;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import es.foco.next.log.BlockEvent;
import es.foco.next.log.BlockLog;
import es.foco.next.log.LockTracker;
import es.foco.next.log.ObservedSnapshot;
import es.foco.next.ui.FocusOverlay;
import java.time.*;
import java.util.*;

/**
 * Only window events and one upcoming boundary. Content events only for strict mode, web filter,
 * purchases and in-app sections. No polling, animation or VPN.
 */
public final class FocusAccessibilityService extends AccessibilityService
        implements Repository.Listener {
    private static FocusAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Repository repository;
    private BankAppGuard bank;
    private CallMonitor calls;
    private FocusOverlay overlay;
    private String foreground = "";
    private final Set<String> homes = new HashSet<>();
    private AudioManager.OnModeChangedListener audioListener;
    private boolean registered, closed;
    /** Cronómetro de las apps vigiladas (píldora con los minutos que quedan). */
    private es.foco.next.ui.LeisurePill pill;
    private final Runnable boundary =
            () -> {
                if (WebFilter.supported(foreground)) webCheck(foreground, true);
                if (InAppFilter.supported(foreground)) sectionCheck(foreground, true);
                evaluate(false);
            };
    // Fase 3: DNS privado. Vigilancia y decisión fuera del motor (DnsMonitor + DnsGuard);
    // FocusEngine no cambia.
    private DnsMonitor dns;
    private DnsAuto dnsAuto; // Foco Home, etapa 5.4
    private final Runnable dnsRecheck = () -> evaluate(false);
    private final LockTracker lockLog = new LockTracker(); // Fase 4: registro de bloqueos
    // Modo estricto: solo lee textos dentro de Ajustes/instalador, con estricto activado y durante
    // un bloqueo. No guarda nada.
    private boolean dnsBlocked;
    private long strictLast;
    // Lote 1: bloqueos con nombre. Solo cuenta el tiempo de la app en primer plano con pantalla
    // encendida.
    private RuleStore rules;
    private String rulePkg = "";
    private long ruleSince = -1;
    private String ruleLogged = "";
    private boolean installsRegistered;
    // Lote 2: lista blanca. Nunca bloquea esenciales, Inicio, teléfono, Ajustes, Foco ni apps sin
    // icono de lanzador.
    private final DeviceState.Launchable launchable = new DeviceState.Launchable();

    private boolean exempt(AppConfig c, String pkg) {
        return pkg.equals(getPackageName())
                || StrictGuard.guardedPackage(pkg)
                || essential(c, pkg)
                || !launchable.has(this, pkg);
    }

    private final Runnable strictHome =
            () -> {
                try {
                    if (!closed) performGlobalAction(GLOBAL_ACTION_HOME);
                } catch (RuntimeException ignored) {
                }
            };
    // Auditoría: un mismo aviso como mucho cada 3 s (antes, un Toast por cada cierre, cada 300 ms,
    // mientras Ajustes insistía).
    private static final long TOAST_COOLDOWN = 3_000;
    private long toastAt;
    private String toastText = "";

    private void toast(String text) {
        long now = SystemClock.elapsedRealtime();
        if (text.equals(toastText) && now - toastAt < TOAST_COOLDOWN) return;
        toastText = text;
        toastAt = now;
        try {
            android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ignored) {
        }
    }

    private boolean rulesActive() {
        try {
            return rules != null && rules.anyActive();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final int STRICT_MAX_NODES = 400, STRICT_MAX_DEPTH = 25;

    private boolean strictCheck(String pkg, boolean stateChange) {
        try {
            if (!StrictGuard.guardedPackage(pkg) || !StrictAdmin.enabled(this)) return false;
            long now = SystemClock.elapsedRealtime();
            if (!stateChange && now - strictLast < 300) return false;
            boolean block =
                    repository.locked()
                            || dnsBlocked
                            || rulesActive(); // Lote 1: un bloqueo con nombre también cuenta
            // Modo estricto reforzado: con el candado de tiempo, las pantallas propias de Foco se
            // cierran siempre.
            if (!block && !StrictAdmin.lockActive(this)) return false;
            strictLast = now;
            List<String> texts = new ArrayList<>();
            // Los diálogos de One UI ("¿Desea desactivar Foco?") pueden ser una ventana aparte: se
            // leen todas las de Ajustes.
            boolean any = false;
            try {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) continue;
                    try {
                        CharSequence wp = r.getPackageName();
                        if (wp != null && StrictGuard.guardedPackage(wp.toString())) {
                            collect(r, texts, 0);
                            any = true;
                        }
                    } finally {
                        r.recycle();
                    }
                }
            } catch (RuntimeException ignored) {
            }
            if (!any) {
                android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return false;
                try {
                    collect(root, texts, 0);
                } finally {
                    root.recycle();
                }
            }
            if (!(block ? StrictGuard.dangerous(texts) : StrictGuard.dangerousAlways(texts)))
                return false;
            performGlobalAction(GLOBAL_ACTION_BACK);
            main.removeCallbacks(strictHome);
            main.postDelayed(strictHome, 150);
            toast(
                    block
                            ? "Modo estricto: disponible al terminar el bloqueo"
                            : "Modo estricto: disponible al terminar el candado");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ---- Foco Home, etapa 5.8: vigilancia de Ajustes ----
    private String settingsLast = "";
    private long settingsAt, settingsHomeAt;

    /**
     * Solo en Ajustes. Clasifica la pantalla (SettingsWatch, puro) con la clase y hasta 200 textos;
     * no guarda nada de lo leído, solo el tipo de pantalla. Wi-Fi, Bluetooth, batería, sonido y VPN:
     * registro. Fecha y hora: aviso + registro. DNS privado (si ya está bien puesto), Accesibilidad >
     * Foco e Info de Foco: volver al inicio + incidente.
     */
    private boolean settingsWatch(String pkg, CharSequence cls) {
        if (!es.foco.next.core.home.SettingsWatch.SETTINGS_PKG.equals(pkg)) return false;
        try {
            List<String> texts = new ArrayList<>();
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null)
                try {
                    collect(root, texts, 0, 200);
                } finally {
                    root.recycle();
                }
            es.foco.next.core.home.SettingsWatch.Screen s =
                    es.foco.next.core.home.SettingsWatch.classify(
                            cls == null ? "" : cls.toString(), texts, getString(es.foco.next.R.string.app_name));
            if (s.action == null) return false;
            long now = SystemClock.elapsedRealtime();
            boolean fresh = !s.name().equals(settingsLast) || now - settingsAt > 60_000;
            settingsLast = s.name();
            if (fresh) settingsAt = now;
            String day = java.time.LocalDate.now().toString();
            // El DNS privado solo se protege si YA está bien puesto (si no, hay que poder arreglarlo).
            boolean dnsOk = DnsAuto.dnsCompliant(this);
            // Cuidado: proteger Ajustes es endurecer. Solo en ENFOQUE o con el candado del modo
            // estricto (los dos los elige el usuario); en el resto, solo se registra.
            boolean guard =
                    CareStore.mode(this) == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                            || StrictAdmin.lockActive(this);
            if (guard && es.foco.next.core.home.SettingsWatch.enforce(s, dnsOk)) {
                if (now - settingsHomeAt < 1_500) return true;
                settingsHomeAt = now;
                performGlobalAction(GLOBAL_ACTION_HOME);
                toast("Foco protege esta pantalla de Ajustes.");
                es.foco.next.log.InspectorLog.event(
                        this, es.foco.next.log.InspectorLog.INCIDENTE, "", 0,
                        "ajustes_protegidos:" + s.name().toLowerCase(),
                        "inc|ajustes|" + s.name() + "|" + System.currentTimeMillis() / 60_000);
                return true;
            }
            if (!fresh) return false;
            if (s.action == es.foco.next.core.home.SettingsWatch.Action.WARN)
                toast("Fecha y hora: con la hora automática apagada, Foco trata el día como noche.");
            es.foco.next.log.InspectorLog.event(
                    this, es.foco.next.log.InspectorLog.AJUSTES, "", 0,
                    s.name().toLowerCase(), "aj|" + day + "|" + s.name() + "|" + System.currentTimeMillis() / 60_000);
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    // Lote 5: compras dentro de apps. Solo lee textos de Google Play y solo si una regla bloquea
    // compras ahora.
    private long purchaseLast;
    private String playClass = "";

    private boolean purchaseCheck(String pkg, CharSequence cls, boolean stateChange) {
        try {
            if (!PurchaseGuard.PLAY.equals(pkg) || rules == null) return false;
            if (stateChange && cls != null) playClass = cls.toString();
            long now = SystemClock.elapsedRealtime();
            if (!stateChange && now - purchaseLast < 300)
                return false; // Auditoría: el filtro barato antes de recorrer los bloqueos
            BlockRule r = rules.purchaseBlocker();
            if (r == null) return false;
            purchaseLast = now;
            List<String> texts = new ArrayList<>();
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            try {
                collect(root, texts, 0);
            } finally {
                root.recycle();
            }
            if (!PurchaseGuard.isPurchase(pkg, playClass, texts)) return false;
            performGlobalAction(GLOBAL_ACTION_BACK);
            toast("Compras bloqueadas por «" + r.name + "»");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void collect(
            android.view.accessibility.AccessibilityNodeInfo n, List<String> out, int depth) {
        collect(n, out, depth, STRICT_MAX_NODES);
    }

    private void collect(
            android.view.accessibility.AccessibilityNodeInfo n,
            List<String> out,
            int depth,
            int max) {
        if (n == null || out.size() >= max || depth > STRICT_MAX_DEPTH) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0 && t.length() < 200) out.add(t.toString());
        CharSequence cd = n.getContentDescription();
        if (cd != null && cd.length() > 0 && cd.length() < 200) out.add(cd.toString());
        int k = n.getChildCount();
        for (int i = 0; i < k && out.size() < max; i++) {
            android.view.accessibility.AccessibilityNodeInfo ch = n.getChild(i);
            if (ch == null) continue;
            try {
                collect(ch, out, depth + 1, max);
            } finally {
                ch.recycle();
            }
        }
    }

    // Lote 3: filtro web. Solo en navegadores compatibles y solo si algún bloqueo aplica sitios o
    // palabras clave ahora.
    // Lee la barra de direcciones; con palabras clave, también hasta 300 textos visibles. Nada se
    // guarda.
    private static final int WEB_MAX_NODES = 300;
    private static final long WEB_THROTTLE = 500;
    private long webLast;
    private String webPkg = "", webKey = "", browserLogged = "";
    private FocusOverlay.RuleBlock webHit;
    private Set<String> browsers;
    private final Runnable webRecheck =
            () -> {
                if (WebFilter.supported(foreground)) webCheck(foreground, true);
            };

    private Set<String> browsers() {
        if (browsers == null) {
            Set<String> out = new HashSet<>();
            try {
                for (ResolveInfo r :
                        getPackageManager()
                                .queryIntentActivities(
                                        new Intent(
                                                Intent.ACTION_VIEW,
                                                android.net.Uri.parse(
                                                        "http://foco-check.invalid/")),
                                        android.content.pm.PackageManager.MATCH_ALL))
                    if (r.activityInfo != null) out.add(r.activityInfo.packageName);
            } catch (RuntimeException ignored) {
            }
            browsers = out;
        }
        return browsers;
    }

    // Foco 1.0, etapa 2: bloqueo total (Telegram). La app se manda a Inicio; sus dominios, atrás e
    // Inicio. Cada intento va al log.
    private long totalAt, totalWebAt;
    private String totalKey = "";

    private void totalBlocked(String pkg, String how) {
        blockedAlways(
                pkg,
                how,
                es.foco.next.log.InspectorLog.INTENTO_TOTAL,
                "Telegram: bloqueo total, sin excepciones");
    }

    /** v37: la app o la web de NextDNS: sus reglas se cambian solo desde otro dispositivo. */
    private void dnsPanelBlocked(String pkg, String how) {
        blockedAlways(
                pkg,
                how,
                es.foco.next.log.InspectorLog.INTENTO_NEXTDNS,
                "NextDNS se cambia solo desde otro dispositivo");
    }

    /**
     * Bloqueo sin ventana ni saldo (Telegram y la web de las redes sociales de la Regla 1): atrás e
     * Inicio, y al log.
     */
    private void blockedAlways(String pkg, String how, String type, String text) {
        try {
            long now = SystemClock.elapsedRealtime();
            String key = pkg + "|" + how + "|" + type;
            if (key.equals(totalKey) && now - totalAt < 1500) return;
            totalKey = key;
            totalAt = now;
            es.foco.next.log.InspectorLog.event(
                    this, type, pkg, System.currentTimeMillis(), 0, 1, 0, how, null);
            toast(text);
            if ("web".equals(how)) performGlobalAction(GLOBAL_ACTION_BACK);
            main.removeCallbacks(strictHome);
            main.postDelayed(strictHome, 150);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Lee solo la barra de direcciones del navegador compatible, como mucho cada 700 ms. Nada se
     * guarda salvo el intento.
     */
    private void totalWebCheck(String pkg) {
        try {
            long now = SystemClock.elapsedRealtime();
            if (now - totalWebAt < 700) return;
            totalWebAt = now;
            android.view.accessibility.AccessibilityNodeInfo root = browserRoot(pkg);
            if (root == null) return;
            String url;
            try {
                url = urlBar(root, pkg);
            } finally {
                root.recycle();
            }
            if (Categories.totalDomain(url)) totalBlocked(pkg, "web");
            else if (Categories.dnsPanelDomain(url)) dnsPanelBlocked(pkg, "web"); // v37
            // Regla 1: la web de las redes sociales, siempre (sin ventana de 5 minutos ni saldo en
            // el navegador).
            else if (AbsoluteRules.socialDomain(url))
                blockedAlways(
                        pkg,
                        "web",
                        es.foco.next.log.InspectorLog.WEB_SOCIAL,
                        "Redes sociales: la web está bloqueada siempre (regla absoluta)");
        } catch (RuntimeException ignored) {
        }
    }

    private void webCheck(String pkg, boolean force) {
        if (!closed && WebFilter.supported(pkg)) totalWebCheck(pkg);
        try {
            if (closed
                    || rules == null
                    || !WebFilter.supported(pkg)
                    || !rules.webFilters()
                    || !focusMode()) { // Cuidado: filtros con nombre solo en ENFOQUE
                if (webHit != null) {
                    webHit = null;
                    webKey = "";
                    evaluate(true);
                }
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (!force && now - webLast < WEB_THROTTLE) {
                main.removeCallbacks(webRecheck);
                main.postDelayed(webRecheck, WEB_THROTTLE - (now - webLast));
                return;
            }
            webLast = now;
            android.view.accessibility.AccessibilityNodeInfo root = browserRoot(pkg);
            if (root == null) return;
            String url;
            List<String> texts = null;
            try {
                url = urlBar(root, pkg);
                if (rules.webNeedsText()) {
                    texts = new ArrayList<>();
                    collect(root, texts, 0, WEB_MAX_NODES);
                }
            } finally {
                root.recycle();
            }
            WebFilter.Hit h = rules.web(url, texts);
            if (h == null) {
                if (webHit != null) {
                    webHit = null;
                    webKey = "";
                    evaluate(true);
                }
                return;
            }
            String key = h.rule.id + "|" + h.what;
            boolean fresh = !key.equals(webKey) || !pkg.equals(webPkg) || webHit == null;
            webPkg = pkg;
            webKey = key;
            webHit = webBlock(h.rule, h.what, false);
            if (fresh) {
                BlockLog.record(this, BlockEvent.Type.RULE_LOCK, pkg, "web");
                performGlobalAction(
                        GLOBAL_ACTION_BACK); // una vez por sitio; la pausa sigue encima hasta que
                // la dirección cambie
                main.removeCallbacks(webRecheck);
                main.postDelayed(webRecheck, 800);
            }
            evaluate(true);
        } catch (RuntimeException ignored) {
        }
    }

    /** Raíz de la ventana del navegador (aunque la pausa de Foco esté encima). */
    private android.view.accessibility.AccessibilityNodeInfo browserRoot(String pkg) {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                if (w.getType()
                        != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION)
                    continue;
                android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                if (r != null
                        && r.getPackageName() != null
                        && pkg.equals(r.getPackageName().toString())) return r;
                if (r != null) r.recycle();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            android.view.accessibility.AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r != null
                    && r.getPackageName() != null
                    && pkg.equals(r.getPackageName().toString())) return r;
            if (r != null) r.recycle();
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Texto de la barra de direcciones por ID conocido; si ninguno existe, el primer campo de texto
     * que parezca una dirección.
     */
    private String urlBar(android.view.accessibility.AccessibilityNodeInfo root, String pkg) {
        List<String> ids = WebFilter.BROWSERS.get(pkg);
        if (ids != null)
            for (String id : ids) {
                List<android.view.accessibility.AccessibilityNodeInfo> found =
                        root.findAccessibilityNodeInfosByViewId(id);
                if (found == null) continue;
                String text = null;
                for (android.view.accessibility.AccessibilityNodeInfo n : found) {
                    CharSequence t = n.getText();
                    if (text == null && t != null && t.length() > 0) text = t.toString();
                    n.recycle();
                }
                if (text != null) return text;
            }
        return editUrl(root, 0, new int[] {0});
    }

    private String editUrl(
            android.view.accessibility.AccessibilityNodeInfo n, int depth, int[] seen) {
        if (n == null || depth > STRICT_MAX_DEPTH || ++seen[0] > WEB_MAX_NODES) return null;
        CharSequence cls = n.getClassName(), t = n.getText();
        if (cls != null
                && cls.toString().endsWith("EditText")
                && t != null
                && WebFilter.normalize(t.toString()) != null) return t.toString();
        for (int i = 0; i < n.getChildCount() && seen[0] <= WEB_MAX_NODES; i++) {
            android.view.accessibility.AccessibilityNodeInfo ch = n.getChild(i);
            if (ch == null) continue;
            try {
                String u = editUrl(ch, depth + 1, seen);
                if (u != null) return u;
            } finally {
                ch.recycle();
            }
        }
        return null;
    }

    private FocusOverlay.RuleBlock webBlock(BlockRule r, String what, boolean browser) {
        FocusOverlay.RuleBlock b = new FocusOverlay.RuleBlock();
        b.id = r.id;
        b.name = r.name;
        b.web = what;
        b.browser = browser;
        ZonedDateTime local = ZonedDateTime.now();
        b.schedule = r.scheduled();
        b.resetsIn = b.schedule ? RuleEngine.windowEndsIn(r, local) : 0;
        RuleState.Entry e = rules.entry(r.id);
        b.pausesLeft = RuleEngine.pausesLeft(r, e);
        b.pauseMinutes = r.pauseMinutes;
        String id = r.id;
        b.pause =
                () -> {
                    try {
                        if (rules.pause(id)) toast("Pausa de " + b.pauseMinutes + " min");
                    } catch (RuntimeException ignored) {
                    }
                    webHit = null;
                    webKey = "";
                    evaluate(true);
                };
        return b;
    }

    // Secciones dentro de apps (Shorts, Reels, Spotlight). Solo en las apps del catálogo y solo si
    // un bloqueo activo tiene
    // secciones para esa app. Lee identificadores de vista y descripciones (hasta 400 elementos);
    // no guarda nada.
    // Nunca bloquea la app entera: vuelve atrás (hasta 3 veces, cada 1,5 s) y solo después muestra
    // la pausa con «Volver».
    private static final int SECTION_MAX_NODES = 400, SECTION_MAX_BACKS = 3;
    private static final long SECTION_THROTTLE = 400, SECTION_RETRY = 1_500;
    private long sectionLast, sectionBackAt;
    private int sectionBacks;
    private String sectionPkg = "", sectionId;
    private FocusOverlay.RuleBlock sectionHit;
    private final Runnable sectionRecheck =
            () -> {
                if (InAppFilter.supported(foreground)) sectionCheck(foreground, true);
            };

    private void sectionReset() {
        boolean shown = sectionHit != null;
        sectionHit = null;
        sectionId = null;
        sectionBacks = 0;
        main.removeCallbacks(sectionRecheck);
        if (shown) evaluate(true);
    }

    /** De control a cuidado: los bloqueos que no son reglas fijas solo actúan en ENFOQUE. */
    private boolean focusMode() {
        return CareStore.mode(this) == es.foco.next.core.care.CareMode.Modo.ENFOQUE;
    }

    private void sectionCheck(String pkg, boolean force) {
        try {
            if (closed || rules == null || !InAppFilter.supported(pkg) || !focusMode()) {
                if (sectionId != null || sectionHit != null) sectionReset();
                return;
            }
            // 1.9: WhatsApp es esencial (nunca se bloquea entero) pero sus Estados y canales sí;
            // nunca durante una llamada.
            if (InAppFilter.sectionsInEssential(pkg) && calls != null && calls.active(pkg)) {
                if (sectionId != null || sectionHit != null) sectionReset();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (!force && now - sectionLast < SECTION_THROTTLE) {
                main.removeCallbacks(sectionRecheck);
                main.postDelayed(sectionRecheck, SECTION_THROTTLE - (now - sectionLast));
                return;
            }
            Set<String> want = rules.sections(pkg);
            if (want.isEmpty()) {
                if (sectionId != null || sectionHit != null) sectionReset();
                return;
            }
            sectionLast = now;
            android.view.accessibility.AccessibilityNodeInfo root =
                    browserRoot(pkg); // la ventana de la app, aunque la pausa esté encima
            if (root == null) return;
            List<String> ids = new ArrayList<>(),
                    descs = new ArrayList<>(),
                    selected = new ArrayList<>();
            try {
                signals(root, ids, descs, selected, 0, new int[] {0});
            } finally {
                root.recycle();
            }
            String id = InAppFilter.detect(pkg, ids, descs, selected, want);
            if (id == null) {
                if (sectionId != null || sectionHit != null) sectionReset();
                return;
            }
            BlockRule r = rules.sectionBlocker(id);
            if (r == null) return;
            boolean fresh = !id.equals(sectionId) || !pkg.equals(sectionPkg);
            if (fresh) {
                sectionPkg = pkg;
                sectionId = id;
                sectionBacks = 0;
                sectionHit = null;
                BlockLog.record(this, BlockEvent.Type.RULE_LOCK, pkg, "section:" + id);
                toast(InAppFilter.noun(id) + " bloqueados por «" + r.name + "»");
            }
            if (sectionHit != null)
                return; // la pausa ya está encima; se quita al salir de la sección
            if (sectionBacks > 0 && now - sectionBackAt < SECTION_RETRY) {
                main.removeCallbacks(sectionRecheck);
                main.postDelayed(sectionRecheck, SECTION_RETRY - (now - sectionBackAt));
                return;
            }
            if (sectionBacks < SECTION_MAX_BACKS) {
                sectionBacks++;
                sectionBackAt = now;
                performGlobalAction(GLOBAL_ACTION_BACK);
                main.removeCallbacks(sectionRecheck);
                main.postDelayed(sectionRecheck, SECTION_RETRY + 50);
                return;
            }
            sectionHit = sectionBlock(r, id);
            evaluate(true);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Identificadores, descripciones y descripciones de elementos seleccionados (pestaña activa).
     * Recicla cada nodo hijo.
     */
    private void signals(
            android.view.accessibility.AccessibilityNodeInfo n,
            List<String> ids,
            List<String> descs,
            List<String> selected,
            int depth,
            int[] seen) {
        if (n == null || depth > STRICT_MAX_DEPTH || ++seen[0] > SECTION_MAX_NODES) return;
        if (n.isVisibleToUser()) {
            String vid = n.getViewIdResourceName();
            if (vid != null && vid.length() < 120) ids.add(vid);
            CharSequence cd = n.getContentDescription();
            if (cd != null && cd.length() > 0 && cd.length() < 120) {
                descs.add(cd.toString());
                if (n.isSelected()) selected.add(cd.toString());
            } else if (n.isSelected()) {
                CharSequence t = n.getText();
                if (t != null && t.length() > 0 && t.length() < 40) selected.add(t.toString());
            } // pestaña seleccionada sin descripción
        }
        int k = n.getChildCount();
        for (int i = 0; i < k && seen[0] <= SECTION_MAX_NODES; i++) {
            android.view.accessibility.AccessibilityNodeInfo ch = n.getChild(i);
            if (ch == null) continue;
            try {
                signals(ch, ids, descs, selected, depth + 1, seen);
            } finally {
                ch.recycle();
            }
        }
    }

    private FocusOverlay.RuleBlock sectionBlock(BlockRule r, String id) {
        FocusOverlay.RuleBlock b = webBlock(r, null, false);
        b.section = InAppFilter.label(id);
        b.pause =
                () -> {
                    try {
                        if (rules.pause(r.id)) toast("Pausa de " + b.pauseMinutes + " min");
                    } catch (RuntimeException ignored) {
                    }
                    sectionHit = null;
                    sectionId = null;
                    sectionBacks = 0;
                    evaluate(true);
                };
        b.back =
                () -> {
                    sectionHit = null;
                    sectionBacks = SECTION_MAX_BACKS - 1;
                    sectionBackAt = SystemClock.elapsedRealtime();
                    evaluate(true);
                    try {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    } catch (RuntimeException ignored) {
                    }
                    main.removeCallbacks(sectionRecheck);
                    main.postDelayed(sectionRecheck, SECTION_RETRY + 50);
                };
        return b;
    }

    public static boolean connected() {
        return instance != null && !instance.closed;
    }

    /** Foco Home: gestos del inicio (cortina, bloquear pantalla). false si no hay servicio. */
    public static boolean globalAction(int action) {
        return connected() && instance.performGlobalAction(action);
    }

    /** Foco Home: tras conceder la ubicación o cambiar perfiles, vuelve a vigilar la red. */

    public static void checkpoint() {
        if (connected()) instance.evaluate(true);
    }

    public static void appOpened() {
        if (connected()) instance.observe(instance.getPackageName());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        closed = false;
        CareStore.install(this); // v32 (R13)
        try {
            repository = Repository.get(this);
            bank = new BankAppGuard(this);
            calls = new CallMonitor(this);
            overlay = new FocusOverlay(this);
            pill = new es.foco.next.ui.LeisurePill(this);
            repository.addListener(this);
            instance = this;
            homes.addAll(DeviceState.homes(this));
            // El búho cuenta con calma si estuvo apagado (y programa su comprobación periódica).
            OwlWatch.serviceOn(this);
            OwlWatch.ensure(this);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, f);
            registered = true;
            rules = RuleStore.get(this);
            // Lote 1: desde Android 8 PACKAGE_ADDED no llega a receptores del manifiesto; se
            // registra aquí.
            try {
                IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
                pf.addDataScheme("package");
                if (Build.VERSION.SDK_INT >= 33)
                    registerReceiver(installs, pf, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(installs, pf);
                installsRegistered = true;
            } catch (RuntimeException ignored) {
            }
            dns = new DnsMonitor(this, () -> evaluate(true));
            dns.start(); // Fase 3
            dnsAuto = new DnsAuto(this);
            dnsAuto.start(); // Foco Home, etapa 5.4
            try {
                if (Build.VERSION.SDK_INT >= 31 && calls.audio != null) {
                    audioListener = mode -> evaluate(true);
                    calls.audio.addOnModeChangedListener(getMainExecutor(), audioListener);
                }
            } catch (RuntimeException e) {
                audioListener = null;
            }
            observe(ForegroundWindows.current(this, false));
            GuardService.ensure(this); // en espera mientras Accesibilidad esté conectada
        } catch (RuntimeException e) {
            android.util.Log.e("Foco", "No se pudo iniciar la medición", e);
            if (overlay != null) overlay.dismiss();
            closed = true;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (closed || repository == null || e == null) return;
        int type = e.getEventType();
        String pkg = e.getPackageName() == null ? "" : e.getPackageName().toString();
        // v35: deslizar al siguiente video (solo interesa en la sesión de ocio en curso).
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            awScroll(pkg, e);
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            repository.touch(pkg, SystemClock.elapsedRealtime());
            if (purchaseCheck(pkg, null, false)) return;
            if (!strictCheck(pkg, false)) {
                if (WebFilter.supported(pkg)) webCheck(pkg, false);
                else if (InAppFilter.supported(pkg)) sectionCheck(pkg, false);
            }
            return;
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if (strictCheck(pkg, true)) return;
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && settingsWatch(pkg, e.getClassName())) return; // Foco Home, etapa 5.8
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && purchaseCheck(pkg, e.getClassName(), true)) return;
        // Hide first on a bank event, before even querying focused-window metadata.
        if (bank.hide(repository.config(), pkg)) {
            overlay.dismiss();
            observe(pkg);
            return;
        }
        String focusedPkg = ForegroundWindows.current(this, overlay.visible());
        // Una app vigilada en PiP o pantalla dividida también cuenta (ver ForegroundWindows).
        final AppConfig watchCfg = repository.config();
        String current =
                focusedPkg.isEmpty()
                        ? focusedPkg
                        : ForegroundWindows.current(this, overlay.visible(), p -> FocusEngine.watched(watchCfg, p));
        if (current.isEmpty()) {
            // Own overlay events aren't evidence that the underlying app has changed.
            if (pkg.equals(getPackageName()) && overlay.visible()) return;
            observe("");
            return;
        }
        if (!current.equals(foreground)) {
            verifiedHint = current;
            observe(current);
        }
        if (WebFilter.supported(focusedPkg)) webCheck(focusedPkg, true);
        else if (InAppFilter.supported(focusedPkg)) sectionCheck(focusedPkg, false);
    }

    /**
     * Auditoría: app delante ya consultada en este mismo evento; evaluate() la usa en lugar de
     * repetir getWindows().
     */
    private String verifiedHint;

    private void observe(String pkg) {
        // Corrección A: las apps en observación libre no se limitan, pero sus aperturas cuentan
        // para la regla de endurecer.
        try {
            if (!pkg.isEmpty()
                    && !pkg.equals(foreground)
                    && CategoryInspector.watchlist(this).contains(pkg))
                es.foco.next.log.InspectorLog.usage(
                        this,
                        es.foco.next.log.InspectorLog.SIN_CLASIFICAR,
                        pkg,
                        System.currentTimeMillis(),
                        0,
                        1);
        } catch (RuntimeException ignored) {
        }
        // Inquietud: cada vez que se entra en una app de ocio (aunque Foco la cierre y se reintente).
        try {
            if (!pkg.isEmpty()
                    && !pkg.equals(foreground)
                    && repository != null
                    && FocusEngine.watched(repository.config(), pkg))
                OwlWatch.leisureOpened(this, pkg);
        } catch (RuntimeException ignored) {
        }
        foreground = pkg;
        if (!pkg.equals(webPkg)) {
            webHit = null;
            webKey = "";
        }
        if (!pkg.equals(sectionPkg)) {
            sectionHit = null;
            sectionId = null;
            sectionBacks = 0;
        }
        evaluate(true);
    }

    private boolean essential(AppConfig c, String pkg) {
        if (AbsoluteRules.social(pkg)) return false;
        return FocusEngine.essential(c, pkg)
                || homes.contains(pkg)
                || calls.packages.contains(pkg)
                || bank.hide(c, pkg)
                || PauseMusic.allowed(SoundSettings.pauseMusic(this), pkg);
    }

    private void evaluate(boolean force) {
        String hint = verifiedHint;
        verifiedHint = null;
        if (closed || repository == null || overlay == null) return;
        OwlWatch.alive(this);
        main.removeCallbacks(boundary);
        main.removeCallbacks(dnsRecheck);
        try {
            AppConfig c = repository.config();
            String verified =
                    hint != null
                            ? hint
                            : ForegroundWindows.current(this, overlay.visible(), p -> FocusEngine.watched(c, p));
            // A timer can never extend a stale foreground observation onto another app.
            if (!verified.equals(foreground) && !bank.hide(c, foreground)) foreground = verified;
            long wall = WallClock.now();
            ZonedDateTime local = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault());
            if (Categories.totalBlock(foreground) && DeviceState.awake(this))
                totalBlocked(
                        foreground, "app"); // Foco 1.0, etapa 2: siempre, con el plan activado o no
            else if (Categories.dnsPanelApp(foreground) && DeviceState.awake(this))
                dnsPanelBlocked(foreground, "app"); // v37: NextDNS solo desde otro dispositivo
            FocusEngine.Input in = new FocusEngine.Input();
            in.pkg = foreground;
            in.wall = wall;
            in.elapsed = SystemClock.elapsedRealtime();
            in.boot = Repository.boot(this);
            in.day = local.toLocalDate().toString();
            in.millisToday =
                    wall
                            - local.toLocalDate()
                                    .atStartOfDay(local.getZone())
                                    .toInstant()
                                    .toEpochMilli();
            in.interactive = DeviceState.awake(this) && !foreground.isEmpty();
            in.essential = essential(c, foreground);
            in.inCall = calls.active(foreground);
            // Cronómetro: ¿es una app de ocio que cuenta? (antes de que la noche o el modo cambien
            // in.interactive).
            boolean leisureApp =
                    in.interactive
                            && !in.essential
                            && !in.inCall
                            && (c.enabled || AbsoluteRules.social(foreground))
                            && FocusEngine.watched(c, foreground);
            // Foco 1.0, etapas 3 y 4: cada bloque se paga; el gratis solo existe de lunes a viernes
            // y fuera del Weekend Lock.
            // Reglas absolutas, Regla 2: el bloque gratis solo existe de 07:00 a 21:00 y con la
            // hora automática encendida.
            boolean autoTime = AppKinds.autoTime(this);
            LocalDateTime clock = local.toLocalDateTime();
            if (!autoTime)
                es.foco.next.log.InspectorLog.event(
                        this,
                        es.foco.next.log.InspectorLog.INCIDENTE,
                        "",
                        0,
                        "hora_automatica_apagada",
                        "inc|autotime|" + in.day);
            // De control a cuidado: el modo decide si el ocio cuenta, si se bloquea y si se paga.
            // La noche, Telegram y los dominios de la Regla 1 siguen en todos los modos.
            CareStore.night(this); // horario de la noche de hoy (aflojar = desde mañana)
            es.foco.next.core.care.CareMode.Modo modo = CareStore.mode(this);
            boolean strict = es.foco.next.core.care.CareMode.blocksAtLimit(modo);
            Inspector.noteDay(this, modo.name().toLowerCase(java.util.Locale.ROOT), Weekend.active(this)); // v32 (R9)
            // v26: en Enfoque, y en el Weekend Lock salvo en Cuidado, el ocio solo se abre con la hora
            // libre de Foco Core. Sin ella, cada ventana nueva queda sin saldo (noBalance).
            // v32 (R7): el Weekend Lock también cierra en Normal (no en Cuidado).
            in.gated = es.foco.next.core.LeisurePolicy.gated(modo, Weekend.active(this));
            // Normal: 5 min y después 30 min de enfriamiento (desde el día siguiente a instalar).
            in.cooldownMs =
                    modo == es.foco.next.core.care.CareMode.Modo.NORMAL
                            ? CareStore.normalCooldownMs(this)
                            : 0;
            // v26: sin banco ni bloque gratis. En Enfoque el ocio se abre solo con la hora libre de
            // Foco Core (freeHour, más abajo), que deja de contar el ocio.
            // Regla 2: noche absoluta (21:00 a 07:00), o siempre si la hora automática está
            // apagada. Todo el ocio cerrado, sin
            // ventana, bloque gratis, saldo ni pausa. La decisión es pura (sin estado): tras un
            // reinicio sigue igual.
            String night = null;
            if (in.interactive
                    && !in.essential
                    && !in.inCall
                    && (c.enabled || AbsoluteRules.social(foreground))
                    && FocusEngine.watched(c, foreground)
                    && AbsoluteRules.night(clock, autoTime)) {
                night =
                        autoTime && es.foco.next.core.home.OwlCare.earlyMorning(clock, AbsoluteRules.nightEnd())
                                // Despertar: de mañana, el búho saluda en vez de decir "noche".
                                ? es.foco.next.core.home.OwlCare.earlyMorning(AbsoluteRules.nightEnd())
                                : autoTime
                                ? "Es de noche: de "
                                        + AbsoluteRules.nightStart()
                                        + ":00 a "
                                        + AbsoluteRules.nightEnd()
                                        + ":00 el ocio descansa, para que tú descanses. Vuelve a las "
                                        + AbsoluteRules.nightEnd()
                                        + ":00."
                                : "La hora automática del teléfono está apagada. Foco no puede fiarse de la hora y cierra el ocio como de noche. Enciéndela en Ajustes > Fecha y hora.";
                in.interactive = false; // no cuenta, no abre ventana y no gasta saldo
                LocalDate nightOf =
                        clock.getHour() < AbsoluteRules.nightEnd()
                                ? clock.toLocalDate().minusDays(1)
                                : clock.toLocalDate();
                es.foco.next.log.InspectorLog.event(
                        this,
                        es.foco.next.log.InspectorLog.NOCHE,
                        foreground,
                        System.currentTimeMillis(),
                        0,
                        1,
                        0,
                        autoTime ? "21-07" : "hora_automatica_apagada",
                        "noche|" + nightOf + "|" + foreground + "|" + autoTime);
            }
            // CUIDADO: el ocio no se cuenta ni se limita (la noche ya se decidió arriba).
            // Foco Core: con el 100 % hecho, 1 h de ocio sin el límite de 5 min/h (las reglas fijas
            // y la noche siguen: con noche no se llega aquí con ocio interactivo).
            RoutineStore.sweep(this); // horas límite: aviso y, pasado el margen, 1 h de pausa
            // v32 (R5): la excepción que vale ahora y qué capas salta salen de LeisurePolicy (la misma
            // tabla que usa GuardService). Hora libre: solo sin límite. Cupón: además tope, rutina,
            // Aduana y bloqueos con nombre. Semana perfecta: todo salvo la noche. Cuidado: todo salvo
            // la rutina.
            boolean watchedLeisure =
                    in.interactive && !in.essential && !in.inCall && FocusEngine.watched(c, foreground);
            boolean grant = watchedLeisure && RewardStore.grantActive(this, foreground);
            es.foco.next.core.LeisurePolicy.Pass pass =
                    !watchedLeisure
                            ? es.foco.next.core.LeisurePolicy.Pass.NONE
                            : es.foco.next.core.LeisurePolicy.pass(
                                    night != null,
                                    es.foco.next.core.care.CareMode.leisure(modo, night != null, false)
                                            == es.foco.next.core.care.CareMode.Leisure.NOT_COUNTED,
                                    grant,
                                    grant && RewardStore.grantWeek(this),
                                    RoutineStore.freeActive(this));
            boolean coupon =
                    pass == es.foco.next.core.LeisurePolicy.Pass.COUPON
                            || pass == es.foco.next.core.LeisurePolicy.Pass.WEEK_COUPON;
            // Con un pase el motor de 5 min ni cuenta ni limita esa app (la medición de R6 sí).
            boolean careLeisure = pass != es.foco.next.core.LeisurePolicy.Pass.NONE;
            // "open": la app se está usando y las capas siguientes la miran, salvo las que salta el pase.
            boolean open = in.interactive;
            if (careLeisure) in.interactive = false;
            // Supervisor: tope diario propio (TikTok) y franja estricta. Con cualquiera de los dos
            // la app no cuenta ni gasta saldo. Cuidado: solo en ENFOQUE.
            String stop =
                    strict
                            && open
                                    && !in.essential
                                    && !in.inCall
                                    && FocusEngine.watched(c, foreground)
                                    && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.SUPERVISOR)
                            ? Supervisor.stop(this, repository, foreground, local.getHour())
                            : null;
            if (stop != null) {
                in.interactive = false;
                open = false;
            }
            // Fase 3: el DNS solo añade una pausa sobre ocio y navegadores, nunca sobre esenciales.
            // Si ya hay bloqueo de
            // presupuesto para esa app, manda el del motor. Bajo la pausa DNS la app no cuenta como
            // visible: no gasta presupuesto.
            DnsGuard.Decision net = dns == null ? null : dns.decide();
            dnsBlocked =
                    net != null && net.blocked(); // Modo estricto: el DNS caído también cuenta como
            // bloqueo
            if (!DnsMonitor.target(c, foreground)) net = null;
            boolean dnsPause =
                    strict // Cuidado: la pausa por DNS solo en ENFOQUE (fuera, la barra lo avisa)
                            && net != null
                            && net.blocked()
                            && open
                            && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.DNS)
                            && !in.essential
                            && !in.inCall
                            && !(repository.locked() && c.watchedApps.contains(foreground));
            if (dnsPause) {
                in.interactive = false;
                open = false;
            }
            // Lote 3: sitio bloqueado o navegador no compatible. No gasta presupuesto ni minutos de
            // los bloqueos.
            FocusOverlay.RuleBlock browserBlock = null;
            boolean webEligible =
                    strict // Cuidado: bloqueos con nombre, webs y secciones solo en ENFOQUE
                            && rules != null
                            && open
                            && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.WEB)
                            && !in.essential && !in.inCall && !dnsPause;
            if (webEligible
                    && !WebFilter.supported(foreground)
                    && !foreground.equals(getPackageName())
                    && !StrictGuard.guardedPackage(foreground)
                    && !homes.contains(foreground)
                    && browsers().contains(foreground)) {
                BlockRule ub = rules.unsupportedBlocker();
                if (ub != null) browserBlock = webBlock(ub, null, true);
            }
            // 1.9: las secciones también se aplican en esenciales con secciones (WhatsApp); el
            // resto del bloqueo las sigue saltando.
            boolean sectionEligible =
                    webEligible
                            || strict
                                    && rules != null
                                    && open
                                    && in.essential
                                    && !in.inCall
                                    && !dnsPause
                                    && InAppFilter.sectionsInEssential(foreground)
                                    && !bank.hide(c, foreground);
            FocusOverlay.RuleBlock webShown =
                    browserBlock != null
                            ? browserBlock
                            : webEligible && webHit != null && foreground.equals(webPkg)
                                    ? webHit
                                    : sectionEligible
                                                    && sectionHit != null
                                                    && foreground.equals(sectionPkg)
                                            ? sectionHit
                                            : null; // secciones: misma pausa, sin gastar minutos
            if (webShown != null) {
                in.interactive = false;
                open = false;
            }
            FocusEngine.Decision d = repository.sample(in, force);
            // Lote 1: después de la decisión del Foco clásico y solo si no bloquea él.
            RuleEngine.Decision rd = null;
            // Lote 2: bloqueo rápido / Pomodoro. En enfoque bloquea las apps de todos los bloqueos
            // y del Foco clásico, sin pausas.
            FocusSession ses = rules == null ? null : rules.session();
            boolean sesBlock = false;
            if (ses != null
                    && ses.focusing(wall)
                    && open
                    && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.POMODORO)
                    && !in.essential
                    && !in.inCall
                    && !dnsPause
                    && !(d.blocked && strict)) // NORMAL: el Pomodoro manda aunque se pasaran los 5 min
                sesBlock = rules.sessionApps(c).contains(foreground);
            if (rules != null) {
                boolean eligible =
                        strict
                                && open
                                && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.NAMED_RULES)
                                && !in.essential
                                && !in.inCall
                                && !dnsPause
                                && !d.blocked
                                && !sesBlock;
                java.util.function.Predicate<String> ex = p -> exempt(c, p);
                // El intervalo desde la última evaluación se atribuye a la app que estaba delante
                // entonces.
                long delta = !rulePkg.isEmpty() && ruleSince >= 0 ? in.elapsed - ruleSince : 0;
                if (delta > 0) rules.decide(rulePkg, wall, delta, in.elapsed, ex);
                rulePkg = eligible ? foreground : "";
                ruleSince = in.elapsed;
                rd = rules.decide(eligible ? foreground : "", wall, 0, in.elapsed, ex);
            }
            String routineGate =
                    leisureApp && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.ROUTINE)
                            ? RoutineStore.gate(this)
                            : null;
            RoutineStore.sync(this); // aviso fijo mientras falte la rutina de ahora
            boolean pauseBefore =
                    overlay.visible() && !overlay.warningVisible(); // Fase 4: registro
            boolean dnsBefore =
                    overlay.dnsVisible(); // Fase 3+4: registrar la pausa DNS una vez al aparecer
            boolean remindOver = false;
            if (night != null) overlay.block(c, 0, messageBlock("noche", night));
            else if (dnsPause) overlay.dnsBlock(dns.host());
            else if (webShown != null) overlay.block(c, 0, webShown);
            else if (stop != null)
                overlay.block(
                        c, 0, messageBlock("tope", stop + " " + Supervisor.proposal(this, c)));
            // Rutina obligatoria de mañana o de noche: el ocio espera, en cualquier modo.
            else if (routineGate != null) overlay.block(c, 0, messageBlock("rutina", routineGate));
            // v32 (R5): con hora libre o cupón normal, el Pomodoro y los bloqueos con nombre siguen.
            else if (careLeisure && sesBlock) overlay.block(c, 0, sessionBlock(ses, wall));
            else if (careLeisure && rd != null && rd.blocked) overlay.block(c, 0, ruleBlock(rd, local));
            else if (!in.interactive || in.essential || in.inCall) overlay.dismiss();
            // Cuidado, modo NORMAL: pasar de los 5 min no bloquea. HomeHub manda el recordatorio
            // con propuesta (máximo 3 por hora) al medir ese ocio. v32 (R7): la puerta de Foco Core
            // (noBalance, p. ej. el Weekend Lock) sí cierra también en Normal.
            else if ((remindOver = d.blocked
                    && !d.noBalance
                    && es.foco.next.core.care.CareMode.leisure(modo, false, true)
                            == es.foco.next.core.care.CareMode.Leisure.REMIND)) {
                if (!overlay.warningVisible()) overlay.dismiss();
                HomeHub.overLimit(this, foreground);
            }
            // Foco Home, Aduana: se cumplió el tiempo que elegiste (1, 3 o 5 min). Solo endurece.
            else if (strict
                    && !es.foco.next.core.LeisurePolicy.skips(pass, es.foco.next.core.LeisurePolicy.Layer.ADUANA)
                    && AduanaStore.current(this).expired(foreground, wall))
                overlay.block(
                        c,
                        0,
                        messageBlock(
                                "aduana",
                                "Elegiste "
                                        + AduanaStore.chosenMinutes(this)
                                        + " min y ya se cumplieron. Buen momento para seguir con lo tuyo."));
            else if (d.noBalance)
                overlay.block(
                        c,
                        0,
                        messageBlock(
                                "saldo",
                                coreGateText()));
            else if (d.blocked) overlay.block(c, d.left);
            else if (sesBlock) overlay.block(c, 0, sessionBlock(ses, wall));
            else if (rd != null && rd.blocked) overlay.block(c, 0, ruleBlock(rd, local));
            else if (d.warning) overlay.warn();
            else if (!overlay.warningVisible()) overlay.dismiss();
            // Cronómetro: solo sobre la app de ocio que cuenta (no en Cuidado, de noche ni con una
            // pausa delante). Minutos, nunca segundos.
            boolean over = d.blocked || d.noBalance;
            OwlWatch.foreground(this, foreground, leisureApp, over); // el búho recuerda de dónde vuelves
            boolean pauseShown = overlay.visible() && !overlay.warningVisible();
            // v32 (R6): medir siempre el ocio real, también con premios, en Cuidado y al pasarse en
            // Normal (el motor de 5 min solo mide el ocio "dentro del límite").
            meter(
                    leisureApp && night == null && !pauseShown
                            ? (pass == es.foco.next.core.LeisurePolicy.Pass.CARE
                                    ? es.foco.next.log.InspectorLog.OCIO_CUIDADO
                                    : es.foco.next.core.LeisurePolicy.reward(pass)
                                            ? es.foco.next.log.InspectorLog.OCIO_PREMIO
                                            : remindOver ? es.foco.next.log.InspectorLog.OCIO_FUERA : null)
                            : null,
                    wall,
                    in.elapsed);
            // v33: «¿Me aporta?» a los 15 min seguidos en una app de ocio (y cada 5 min tras un «sí»).
            // v34: antes cuanto más ocio llevas hoy (10 min desde 1 h; 5 min y «sí» de 3 desde 2 h).
            long awareDue =
                    awareness(
                            leisureApp && night == null && !pauseShown && DeviceState.awake(this),
                            in.elapsed);
            if (pill != null)
                pill.show(
                        leisureApp && night == null && !careLeisure && stop == null && !dnsPause
                                        && webShown == null && !pauseShown
                                ? es.foco.next.core.care.LeisureTimer.text(d.remainingBudget, d.left, over)
                                : null,
                        es.foco.next.core.care.LeisureTimer.lastMinute(d.remainingBudget, over));
            long next = d.next;
            if (!strict && (d.blocked || d.noBalance)) next = 60_000; // NORMAL: revisar cada minuto
            if (careLeisure) next = 60_000; // CUIDADO: medir el rato para la sugerencia suave
            if (coupon) { // al acabar el cupón, la app vuelve a sus reglas en ese momento
                long cl = RewardStore.grantLeftMs(this) + 500;
                next = next > 0 ? Math.min(next, cl) : cl;
            }
            if (awareDue > 0) next = next > 0 ? Math.min(next, awareDue) : awareDue;
            long aduana = strict ? AduanaStore.current(this).msLeft(foreground, wall) : 0; // Foco Home
            if (aduana > 0) next = next > 0 ? Math.min(next, aduana) : aduana;
            if (stop != null || d.noBalance || night != null)
                next = next > 0 ? Math.min(next, 60_000) : 60_000;
            if (d.blocked && strict)
                next =
                        Math.min(
                                es.foco.next.ui.SceneTheme.untilBoundary(),
                                Math.min(
                                        next,
                                        Math.max(
                                                1,
                                                d.left % 60_000 == 0 ? 60_000 : d.left % 60_000)));
            if (rd != null) {
                long rn = rd.blocked ? Math.min(60_000, rd.next > 0 ? rd.next : 60_000) : rd.next;
                if (rn > 0) next = next > 0 ? Math.min(next, rn) : rn;
            }
            if (ses != null) {
                FocusSession.Phase ph = ses.phase(wall);
                if (!ph.done) {
                    long sn = Math.max(1, ph.endsAt - wall);
                    if (sesBlock) sn = Math.min(sn, 60_000);
                    next = next > 0 ? Math.min(next, sn) : sn;
                }
            }
            if (webShown != null) {
                long wn = webShown.resetsIn > 0 ? Math.min(60_000, webShown.resetsIn) : 60_000;
                next = next > 0 ? Math.min(next, wn) : wn;
            }
            if (next > 0) main.postDelayed(boundary, next);
            if (net != null && net.recheck > 0 && in.interactive && !in.essential)
                main.postDelayed(dnsRecheck, net.recheck);
            // Fase 4: registro de mejor esfuerzo, después de mostrar u ocultar la pausa. Nunca
            // lanza; si falla, el bloqueo sigue.
            if (rd != null && rd.blocked && !d.blocked && !sesBlock) {
                RuleState.Entry e = rules.entry(rd.rule.id);
                String key = rd.rule.id + "|" + e.usageDay + "|" + e.windowStart;
                if (!key.equals(ruleLogged))
                    BlockLog.record(this, BlockEvent.Type.RULE_LOCK, foreground, "regla");
                ruleLogged = key;
            }
            if (browserBlock != null) {
                String key = browserBlock.id + "|" + foreground + "|" + in.day;
                if (!key.equals(browserLogged))
                    BlockLog.record(this, BlockEvent.Type.RULE_LOCK, foreground, "web");
                browserLogged = key;
            }
            if (dnsPause && !dnsBefore) {
                BlockLog.record(this, BlockEvent.Type.DNS_OFF_LOCK, foreground, null);
                // Protección: el incidente dice la causa (VPN activa, DNS privado cambiado o
                // apagado, u otra). La VPN no se bloquea.
                es.foco.next.log.InspectorLog.event(
                        this,
                        es.foco.next.log.InspectorLog.INCIDENTE,
                        foreground,
                        0,
                        "dns_apagado;causa=" + DnsMonitor.cause(this),
                        "inc|dns|" + in.day + "|" + (in.elapsed / 600_000));
            }
            if (pass == es.foco.next.core.LeisurePolicy.Pass.CARE) HomeHub.careLeisure(this, foreground);
            BlockLog.afterDecision(
                    this,
                    lockLog,
                    foreground,
                    d.blocked && strict, // NORMAL: pasarse no es un bloqueo
                    d.remainingBudget,
                    d.left,
                    pauseBefore,
                    FocusEngine.BUDGET,
                    () -> ObservedSnapshot.of(repository.state(), repository.config()));
        } catch (RuntimeException e) {
            android.util.Log.e("Foco", "No se pudo actualizar la medición", e);
            overlay.dismiss();
        }
    }

    // v33: sesión seguida en una app de ocio para «¿Me aporta?» (reloj de arranque: no lo mueve
    // cambiar la hora).
    private String awPkg = "";
    private long awStart = -1, awLast = -1;
    private long awDayBefore; // v34: ocio de hoy antes de esta sesión (adelanta la pregunta)
    private boolean awAsked, awReopen;
    // v35: corte entre videos. awDueSince = desde cuándo ya tocaba preguntar (-1 = aún no).
    private int awIndex = -1, awVideos;
    private long awSwipeAt = -1, awDueSince = -1;
    private final Runnable awCut = () -> evaluate(false);

    /** v35: un cambio de video en la app de la sesión (deslizar). Barato: sale enseguida si no toca. */
    private void awScroll(String pkg, AccessibilityEvent e) {
        try {
            if (awPkg.isEmpty() || !pkg.equals(awPkg)) return;
            int idx = e.getFromIndex();
            if (idx < 0 || idx == awIndex) return;
            boolean first = awIndex < 0;
            awIndex = idx;
            if (first) return; // la primera posición vista no es un cambio
            long now = SystemClock.elapsedRealtime();
            if (awSwipeAt >= 0 && now - awSwipeAt < es.foco.next.core.care.Awareness.SWIPE_GAP_MS) return;
            awSwipeAt = now;
            awVideos++;
            if (awDueSince >= 0) { // ya tocaba: este es el corte
                main.removeCallbacks(awCut);
                main.post(awCut);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Lleva la sesión de ocio y abre la ventana «¿Me aporta?» cuando toca. Devuelve en cuánto
     * volver a mirar (0 = no hace falta).
     */
    private long awareness(boolean on, long now) {
        try {
            String pkg = foreground;
            // Al salir de una sesión (otra app, pantalla apagada…): «¿Valió la pena?» si fue larga y
            // no hubo pregunta. Volver en menos de 10 min sigue siendo la misma sesión.
            if (!awPkg.isEmpty() && (!on || !pkg.equals(awPkg))) {
                if (!awAsked && awLast >= 0 && awStart >= 0) {
                    AwarenessStore.askWorth(this, awPkg, awLast - awStart);
                    awAsked = true; // una vez por sesión
                }
                if (!on && awLast >= 0 && now - awLast > es.foco.next.core.care.Awareness.SESSION_GAP_MS) awPkg = "";
            }
            if (!on) return 0;
            if (!pkg.equals(awPkg) || awLast < 0 || now - awLast > es.foco.next.core.care.Awareness.SESSION_GAP_MS) {
                awPkg = pkg;
                awStart = now;
                awAsked = false;
                awReopen = AwarenessStore.reopenedAfterNo(this, pkg);
                awDayBefore = AwarenessStore.leisureTodayMs(this, repository.config());
                awIndex = -1;
                awVideos = 0;
                awSwipeAt = -1;
                awDueSince = -1;
                AwarenessStore.newSession(this, pkg);
            }
            awLast = now;
            long session = now - awStart;
            long due =
                    es.foco.next.core.care.Awareness.dueIn(
                            session,
                            AwarenessStore.extendedUntil(this, pkg),
                            System.currentTimeMillis(),
                            awReopen,
                            awDayBefore);
            if (due > 0) {
                awDueSince = -1;
                return due;
            }
            // v35: ya toca, pero se espera al siguiente video (o se agota la espera).
            if (awDueSince < 0) awDueSince = now;
            long grace = es.foco.next.core.care.Awareness.cutGraceMs(pkg, session, awVideos);
            if (!es.foco.next.core.care.Awareness.cutNow(awSwipeAt >= awDueSince, awReopen, now - awDueSince, grace))
                return Math.max(1_000, grace - (now - awDueSince));
            if (!AwarenessStore.canOpen()) return 20_000;
            if (es.foco.next.ui.AwarenessActivity.open(this, pkg, session, awDayBefore + session, awVideos)) {
                awAsked = true;
                awReopen = false;
                awDueSince = -1;
            }
            return 60_000;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // v32 (R6): tramo de ocio que el motor de 5 min no mide (premio, Cuidado, pasarse en Normal).
    private String meterPkg = "", meterType = "";
    private long meterSince = -1;

    /**
     * Cierra el tramo anterior (si siguió delante sin huecos de más de 65 s, como el motor) y abre
     * uno nuevo si {@code type} no es null. Una apertura cuenta al empezar un tramo de otra app.
     */
    private void meter(String type, long wall, long elapsed) {
        try {
            if (!meterPkg.isEmpty() && meterSince >= 0) {
                long ms = elapsed - meterSince;
                if (ms > 0 && ms <= FocusEngine.CHECKPOINT + 5_000)
                    es.foco.next.log.InspectorLog.usage(this, meterType, meterPkg, wall, ms, 0);
            }
            if (type == null || foreground.isEmpty()) {
                meterPkg = "";
                meterType = "";
                meterSince = -1;
                return;
            }
            if (!foreground.equals(meterPkg) || !type.equals(meterType))
                es.foco.next.log.InspectorLog.usage(this, type, foreground, wall, 0, 1);
            meterPkg = foreground;
            meterType = type;
            meterSince = elapsed;
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Bloqueo con mensaje propio (sin saldo, tope diario o franja estricta) y la propuesta de qué
     * hacer en su lugar.
     */
    private FocusOverlay.RuleBlock messageBlock(String id, String text) {
        FocusOverlay.RuleBlock b = new FocusOverlay.RuleBlock();
        b.id = id;
        b.name = text;
        b.session = text;
        b.resetsIn = 60_000;
        return b;
    }

    private FocusOverlay.RuleBlock sessionBlock(FocusSession ses, long wall) {
        FocusOverlay.RuleBlock b = new FocusOverlay.RuleBlock();
        b.id = "session";
        b.name = ses.title(wall);
        b.session = b.name;
        b.resetsIn = Math.max(1, ses.phase(wall).endsAt - wall);
        return b;
    }

    private FocusOverlay.RuleBlock ruleBlock(RuleEngine.Decision rd, ZonedDateTime local) {
        FocusOverlay.RuleBlock b = new FocusOverlay.RuleBlock();
        b.id = rd.rule.id;
        b.name = rd.rule.name;
        b.resetsIn = rd.resetsIn;
        b.pausesLeft = rd.pausesLeft;
        b.pauseMinutes = rd.rule.pauseMinutes;
        b.schedule =
                rd.rule.scheduled()
                        && (rd.rule.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW
                                || (rd.rule.limitMinutes == 0
                                        && RuleEngine.inWindow(rd.rule, local)));
        String id = rd.rule.id;
        b.pause =
                () -> {
                    try {
                        if (rules.pause(id)) toast("Pausa de " + b.pauseMinutes + " min");
                    } catch (RuntimeException ignored) {
                    }
                    evaluate(true);
                };
        return b;
    }

    public static void rulesChanged() {
        if (connected()) instance.evaluate(true);
    }

    /** Enfoque y Weekend Lock (v26): el ocio se abre con la hora libre de Foco Core. */
    private String coreGateText() {
        try {
            int[] t = RoutineStore.totals(this, RoutineStore.today());
            String who = Weekend.active(this) ? "Weekend Lock" : "Enfoque";
            if (RoutineStore.freeUsedToday(this))
                return who + ": la hora libre de hoy ya se usó. Mañana, con tus tareas al 100 %, vuelve.";
            if (t[0] > 0 && t[1] >= t[0])
                return "¡Tareas al 100 %! Activa tu hora de ocio libre desde el inicio.";
            return who + ": el ocio se abre con tu hora libre de Foco Core. Llevas "
                    + t[1]
                    + " de "
                    + t[0]
                    + " tareas de hoy.";
        } catch (RuntimeException e) {
            return "Enfoque: el ocio se abre con tu hora libre de Foco Core.";
        }
    }

    /**
     * Lote 1: añade apps nuevas de lanzador a los bloqueos que lo piden. Nunca esenciales, bancos
     * ni mensajería.
     */
    private final BroadcastReceiver installs =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent i) {
                    try {
                        if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                                || i.getData() == null
                                || rules == null) return;
                        browsers = null; // Lote 3: puede ser un navegador nuevo
                        homes.addAll(
                                DeviceState.homes(
                                        FocusAccessibilityService
                                                .this)); // Auditoría: o un lanzador nuevo
                        AppKinds.refresh(); // corrección B: lanzadores y teclados nuevos
                        String pkg = i.getData().getSchemeSpecificPart();
                        AppConfig c = repository.config();
                        if (pkg != null) launchable.forget(pkg);
                        if (pkg == null
                                || pkg.equals(getPackageName())
                                || AlternativeCatalog.neverMark(c, pkg)
                                || essential(c, pkg)) return;
                        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) return;
                        // Foco 1.0, etapa 2: una app nueva entra en observación (mismo límite que
                        // el ocio); Telegram y sus clientes ya están en bloqueo total.
                        // Regla 1: una red social instalada ya está en ocio (lo está siempre,
                        // aunque no estuviera instalada).
                        if (AbsoluteRules.social(pkg)) {
                            es.foco.next.log.InspectorLog.event(
                                    FocusAccessibilityService.this,
                                    es.foco.next.log.InspectorLog.CATEGORIA,
                                    pkg,
                                    0,
                                    "nueva>ocio;regla_absoluta_1",
                                    "cat|nueva|" + pkg);
                            android.widget.Toast.makeText(
                                            FocusAccessibilityService.this,
                                            "Red social instalada: está en ocio por regla absoluta.",
                                            android.widget.Toast.LENGTH_LONG)
                                    .show();
                        }
                        // Corrección A: solo juegos, vídeo y redes sociales (según Android) se
                        // limitan desde el primer día; el resto se
                        // registra sin límite y pasa a ocio si cumple la regla de endurecer
                        // (CategoryInspector.daily).
                        else if (!Categories.totalBlock(pkg)
                                && !AppKinds.ignored(FocusAccessibilityService.this, pkg)
                                && !c.watchedApps.contains(pkg)
                                && !c.observedApps.contains(pkg)
                                && !c.productiveApps.contains(pkg)
                                && !pkg.equals(c.returnApp)) {
                            try {
                                if (AbsoluteRules.limitInObservation(
                                        AppKinds.category(FocusAccessibilityService.this, pkg))) {
                                    repository.harden(
                                            "app_nueva;categoria_android",
                                            n -> n.observedApps.add(pkg));
                                    android.widget.Toast.makeText(
                                                    FocusAccessibilityService.this,
                                                    "App nueva de juego, vídeo o red social: en observación con 5 minutos, como el ocio.",
                                                    android.widget.Toast.LENGTH_LONG)
                                            .show();
                                } else {
                                    CategoryInspector.watch(
                                            FocusAccessibilityService.this, pkg, true);
                                    es.foco.next.log.InspectorLog.event(
                                            FocusAccessibilityService.this,
                                            es.foco.next.log.InspectorLog.CATEGORIA,
                                            pkg,
                                            0,
                                            "nueva>observacion_libre;sin_limite",
                                            "cat|nueva|" + pkg);
                                    android.widget.Toast.makeText(
                                                    FocusAccessibilityService.this,
                                                    "App nueva: Foco la registra, sin limitarla.",
                                                    android.widget.Toast.LENGTH_LONG)
                                            .show();
                                }
                            } catch (RuntimeException ignored) {
                            }
                        }
                        List<String> names = rules.addInstalled(pkg);
                        if (!names.isEmpty())
                            android.widget.Toast.makeText(
                                            FocusAccessibilityService.this,
                                            "Foco añadió la app nueva a: "
                                                    + String.join(", ", names),
                                            android.widget.Toast.LENGTH_LONG)
                                    .show();
                    } catch (RuntimeException ignored) {
                    }
                }
            };

    @Override
    public void changed() {
        evaluate(true);
    }

    private final BroadcastReceiver screen =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    // Protección: cambio de hora con la hora automática apagada = cambio manual.
                    if (i != null
                            && Intent.ACTION_TIME_CHANGED.equals(i.getAction())
                            && !AppKinds.autoTime(c))
                        es.foco.next.log.InspectorLog.event(
                                c,
                                es.foco.next.log.InspectorLog.INCIDENTE,
                                "",
                                0,
                                "cambio_manual_hora",
                                "inc|horamanual|" + System.currentTimeMillis() / 60_000);
                    // Foco Home: primer desbloqueo (DESPERTAR) y cambio de inicio predeterminado.
                    if (i != null && Intent.ACTION_SCREEN_OFF.equals(i.getAction()))
                        HomeHub.screenOff(c); // descanso real (premio visual, sin minutos)
                    if (i != null && Intent.ACTION_USER_PRESENT.equals(i.getAction())) {
                        HomeHub.unlocked(c);
                        HomeRole.check(c);
                        OwlWatch.unlocked(c); // despertar con el búho (una vez al día)
                    }
                    if (repository != null)
                        repository
                                .applyPending(); // Regla 3: salidas de ocio que ya cumplieron 24 h
                    evaluate(true);
                    Inspector.maybeWrite(c, false);
                    CategoryInspector.daily(c);
                    Supervisor.daily(c);
                    Supervisor.vpn(c);
                } // Foco Inspector: resumen diario opcional
            };

    @Override
    public void onInterrupt() {
        if (!closed) evaluate(true);
    }

    @Override
    public void onDestroy() {
        OwlWatch.serviceOff(this);
        if (!closed) evaluate(true);
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (dns != null) {
            dns.stop();
            dns = null;
        }
        if (dnsAuto != null) {
            dnsAuto.stop();
            dnsAuto = null;
        }
        // Incidente de protección: el servicio se detiene. v32 (R3): aquí no se sabe si lo apagaste tú
        // o lo paró el sistema (memoria, actualización de Foco): se apunta sin castigo. OwlWatch.check
        // apunta la evasión solo si Foco aparece desactivado en los Ajustes de Android.
        try {
            es.foco.next.log.InspectorLog.event(
                    this,
                    es.foco.next.log.InspectorLog.INCIDENTE,
                    "",
                    0,
                    "accesibilidad_detenida",
                    "inc|acc|" + System.currentTimeMillis() / 60_000);
        } catch (RuntimeException ignored) {
        }
        if (repository != null) {
            repository.removeListener(this);
            try {
                repository.forget();
            } catch (RuntimeException ignored) {
            }
        }
        if (overlay != null) overlay.dismiss();
        if (pill != null) pill.hide();
        if (registered)
            try {
                unregisterReceiver(screen);
            } catch (RuntimeException ignored) {
            }
        if (installsRegistered)
            try {
                unregisterReceiver(installs);
            } catch (RuntimeException ignored) {
            }
        if (rules != null) rules.flush();
        if (Build.VERSION.SDK_INT >= 31
                && audioListener != null
                && calls != null
                && calls.audio != null)
            try {
                calls.audio.removeOnModeChangedListener(audioListener);
            } catch (RuntimeException ignored) {
            }
        if (instance == this) instance = null;
        super.onDestroy();
        GuardService.ensure(this); // Modo estricto reforzado: el respaldo toma el relevo
    }

    @Override
    public boolean onUnbind(Intent i) {
        OwlWatch.serviceOff(this);
        if (pill != null) pill.hide();
        closed = true;
        if (instance == this) instance = null;
        // Auditoría: quitar la pausa y los temporizadores antes de que el respaldo ponga la suya
        // (sin dos pantallas a la vez).
        main.removeCallbacksAndMessages(null);
        if (overlay != null)
            try {
                overlay.dismiss();
            } catch (RuntimeException ignored) {
            }
        if (rules != null) rules.flush();
        GuardService.ensure(this);
        return super.onUnbind(i);
    }
}
