package es.foco.next.data;

import android.content.Context;
import es.foco.next.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

/**
 * Lote 1: bloqueos con nombre en block-rules-v1.json (escritura atómica, como Repository).
 * focus-lite-v1.json no cambia: el Foco clásico sigue en Repository + FocusEngine.
 */
public final class RuleStore {
    private static RuleStore instance;

    public static synchronized RuleStore get(Context c) {
        if (instance == null) instance = new RuleStore(c.getApplicationContext());
        return instance;
    }

    private final AtomicJsonFile file;
    private final List<BlockRule> rules = new ArrayList<>();
    private RuleState state = new RuleState();
    private FocusSession session; // Lote 2: bloqueo rápido o Pomodoro en curso (null si no hay)
    private long lastSave;
    private boolean broken; // archivo ilegible: se copia aparte antes de la primera escritura
    private boolean
            unreadable; // Auditoría de producto: se avisa en la pantalla en vez de mostrar una

    // lista vacía sin explicación

    public synchronized boolean unreadable() {
        return unreadable;
    }

    private RuleStore(Context c) {
        file = new AtomicJsonFile(new File(c.getFilesDir(), "block-rules-v1.json"), "los bloqueos");
        try {
            if (file.exists())
                read(new JSONObject(new String(file.read(), StandardCharsets.UTF_8)));
        } catch (IOException | JSONException | RuntimeException e) {
            android.util.Log.e(
                    "Foco", "No se pudieron leer los bloqueos; se conservan en el archivo", e);
            rules.clear();
            state = new RuleState();
            broken = true;
            unreadable = true;
        }
    }

    public synchronized List<BlockRule> rules() {
        List<BlockRule> out = new ArrayList<>();
        for (BlockRule r : rules) out.add(r.copy());
        return out;
    }

    public synchronized BlockRule rule(String id) {
        for (BlockRule r : rules) if (r.id.equals(id)) return r.copy();
        return null;
    }

    public synchronized RuleState.Entry entry(String id) {
        long w = System.currentTimeMillis();
        BlockRule r = find(id);
        RuleState.Entry e = state.of(id);
        if (r != null) RuleEngine.roll(r, e, w, day(w));
        return e.copy();
    }

    public synchronized boolean active(String id) {
        return RuleEngine.active(find(id), state, now(System.currentTimeMillis()));
    }

    /**
     * Bloqueos con nombre activos, listas blancas en horario, sesión en enfoque (Lote 2) y filtros
     * con horario dentro de su franja (Lote 5): cuentan para el modo estricto.
     */
    public synchronized boolean anyActive() {
        long w = System.currentTimeMillis();
        return focusing(w) || RuleEngine.strictActive(rules, state, now(w));
    }

    // Lote 2: sesión (bloqueo rápido / Pomodoro). Al terminar se borra sola.
    /** Lote 3: primera coincidencia del filtro web, o null. */
    public synchronized WebFilter.Hit web(String url, Collection<String> texts) {
        return WebFilter.match(rules, state, now(System.currentTimeMillis()), url, texts);
    }

    public synchronized boolean webFilters() {
        return WebFilter.anyFilter(rules, state, now(System.currentTimeMillis()));
    }

    public synchronized boolean webNeedsText() {
        return WebFilter.needsText(rules, state, now(System.currentTimeMillis()));
    }

    public synchronized BlockRule unsupportedBlocker() {
        BlockRule r = WebFilter.unsupportedBlocker(rules, state, now(System.currentTimeMillis()));
        return r == null ? null : r.copy();
    }

    public synchronized FocusSession session() {
        long w = System.currentTimeMillis();
        if (session != null && session.phase(w).done) {
            session = null;
            try {
                writeLater();
            } catch (RuntimeException ignored) {
            }
        }
        return session == null ? null : session.copy();
    }

    public synchronized boolean focusing(long wall) {
        return session != null && session.focusing(wall);
    }

    public synchronized void start(FocusSession s) {
        s.clamp();
        session = s.copy();
        write();
    }

    public synchronized void endSession() {
        session = null;
        write();
    }

    /**
     * Apps que bloquea la sesión: las de todos los bloqueos (salvo listas blancas) y las del Foco
     * clásico.
     */
    public synchronized Set<String> sessionApps(AppConfig c) {
        Set<String> out = new HashSet<>();
        for (BlockRule r : rules) if (!r.allowlist) out.addAll(r.packages);
        for (String p : c.watchedApps) if (FocusEngine.watched(c, p)) out.add(p);
        for (String p : c.observedApps) if (FocusEngine.watched(c, p)) out.add(p);
        return out;
    }

    public synchronized void save(BlockRule r) {
        r.clamp();
        if (r.id == null || r.id.isEmpty()) r.id = UUID.randomUUID().toString();
        BlockRule copy = r.copy();
        boolean replaced = false;
        for (int i = 0; i < rules.size(); i++)
            if (rules.get(i).id.equals(copy.id)) {
                rules.set(i, copy);
                replaced = true;
            }
        if (!replaced) rules.add(copy);
        write();
    }

    public synchronized void delete(String id) {
        rules.removeIf(r -> r.id.equals(id));
        state.entries.remove(id);
        write();
    }

    public synchronized boolean pause(String id) {
        long w = System.currentTimeMillis();
        boolean ok = RuleEngine.pause(find(id), state, w, day(w));
        if (ok) write();
        return ok;
    }

    /**
     * Llamado desde el servicio. Guarda al cambiar el estado de bloqueo o como mucho cada minuto.
     */
    public synchronized RuleEngine.Decision decide(
            String pkg,
            long wall,
            long delta,
            long elapsed,
            java.util.function.Predicate<String> exempt) {
        ZonedDateTime local = now(wall);
        boolean before = RuleEngine.anyActive(rules, state, local);
        RuleEngine.Decision d = RuleEngine.decide(rules, state, pkg, local, exempt, delta);
        if (d.changed
                && (before != RuleEngine.anyActive(rules, state, local)
                        || elapsed - lastSave >= 60_000
                        || delta == 0)) {
            try {
                writeLater();
                lastSave = elapsed;
            } catch (RuntimeException ignored) {
            }
        }
        if (d.rule != null) d.rule = d.rule.copy();
        return d;
    }

    public synchronized List<String> addInstalled(String pkg) {
        List<String> names = RuleEngine.addInstalled(rules, pkg);
        if (!names.isEmpty()) write();
        return names;
    }

    public synchronized void flush() {
        try {
            write();
        } catch (RuntimeException ignored) {
        }
    }

    private BlockRule find(String id) {
        for (BlockRule r : rules) if (r.id.equals(id)) return r;
        return null;
    }

    private static ZonedDateTime now(long wall) {
        return Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault());
    }

    private static String day(long wall) {
        return Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private void read(JSONObject j) throws JSONException {
        rules.addAll(parseRules(j.optJSONArray("rules")));
        JSONObject s = j.optJSONObject("state");
        if (s != null)
            for (Iterator<String> it = s.keys(); it.hasNext(); ) {
                String id = it.next();
                JSONObject o = s.getJSONObject(id);
                RuleState.Entry e = state.of(id);
                e.windowStart = o.optLong("windowStart", -1);
                e.usageDay = o.optString("usageDay", "");
                e.usedMillis = Math.max(0, o.optLong("usedMillis", 0));
                e.pausesDay = o.optString("pausesDay", "");
                e.pausesUsed = Math.max(0, o.optInt("pausesUsed", 0));
                e.pausedUntil = Math.max(0, o.optLong("pausedUntil", 0));
            }
        List<String> ids = new ArrayList<>();
        for (BlockRule r : rules) ids.add(r.id);
        state.retain(ids);
        JSONObject q = j.optJSONObject("session");
        if (q != null) {
            FocusSession f = new FocusSession();
            f.kind =
                    "POMODORO".equals(q.optString("kind"))
                            ? FocusSession.Kind.POMODORO
                            : FocusSession.Kind.QUICK;
            f.start = q.optLong("start", 0);
            f.until = q.optLong("until", 0);
            f.focusMinutes = q.optInt("focusMinutes", 25);
            f.breakMinutes = q.optInt("breakMinutes", 5);
            f.cycles = q.optInt("cycles", 4);
            f.clamp();
            if (f.kind == FocusSession.Kind.QUICK && f.until - f.start > 24 * 60 * 60_000L)
                f.until = f.start + 24 * 60 * 60_000L;
            session = f;
        }
    }

    /**
     * Lote 5: lee una lista de bloqueos (archivo o copia de seguridad). Ignora entradas sin id y
     * normaliza con clamp().
     */
    public static List<BlockRule> parseRules(JSONArray a) throws JSONException {
        List<BlockRule> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (a != null)
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                BlockRule r = new BlockRule();
                r.id = o.optString("id", "");
                if (r.id.isEmpty() || !seen.add(r.id)) continue;
                r.name = o.optString("name", "Bloqueo");
                r.enabled = o.optBoolean("enabled", true);
                r.autoAddNew = o.optBoolean("autoAddNew", false);
                JSONArray p = o.optJSONArray("packages");
                if (p != null) for (int k = 0; k < p.length(); k++) r.packages.add(p.getString(k));
                r.limitMinutes = o.optInt("limitMinutes", 15);
                r.period =
                        "WINDOW".equals(o.optString("period"))
                                ? BlockRule.Period.WINDOW
                                : BlockRule.Period.DAILY;
                r.windowMinutes = o.optInt("windowMinutes", 60);
                r.maxPauses = o.optInt("maxPauses", 1);
                r.pauseMinutes = o.optInt("pauseMinutes", 5);
                // Lote 2: campos opcionales; si faltan, comportamiento del Lote 1.
                r.allowlist = o.optBoolean("allowlist", false);
                try {
                    r.scheduleMode =
                            BlockRule.ScheduleMode.valueOf(
                                    o.optString("scheduleMode", "ALWAYS_LIMIT"));
                } catch (IllegalArgumentException e) {
                    r.scheduleMode = BlockRule.ScheduleMode.ALWAYS_LIMIT;
                }
                JSONArray w = o.optJSONArray("schedules");
                if (w != null)
                    for (int k = 0; k < w.length(); k++) {
                        JSONObject x = w.getJSONObject(k);
                        r.schedules.add(
                                Schedule.of(
                                        x.optInt("days", Schedule.ALL_DAYS),
                                        x.optInt("start", 22 * 60),
                                        x.optInt("end", 7 * 60)));
                    }
                // Lote 3: filtro web, opcional.
                JSONArray ws = o.optJSONArray("sites");
                if (ws != null)
                    for (int k = 0; k < ws.length(); k++) r.sites.add(ws.optString(k, ""));
                JSONArray wk = o.optJSONArray("keywords");
                if (wk != null)
                    for (int k = 0; k < wk.length(); k++) r.keywords.add(wk.optString(k, ""));
                r.blockUnsupportedBrowsers = o.optBoolean("blockUnsupportedBrowsers", false);
                r.blockPurchases = o.optBoolean("blockPurchases", false); // Lote 5
                JSONArray sec = o.optJSONArray("sections");
                if (sec != null)
                    for (int k = 0; k < sec.length(); k++)
                        r.sections.add(sec.optString(k, "")); // secciones dentro de apps, opcional
                r.clamp();
                out.add(r);
            }
        return out;
    }

    public static JSONArray rulesJson(List<BlockRule> rules) throws JSONException {
        JSONArray a = new JSONArray();
        for (BlockRule r : rules) {
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("name", r.name);
            o.put("enabled", r.enabled);
            o.put("autoAddNew", r.autoAddNew);
            o.put("packages", new JSONArray(r.packages));
            o.put("limitMinutes", r.limitMinutes);
            o.put("period", r.period.name());
            o.put("windowMinutes", r.windowMinutes);
            o.put("maxPauses", r.maxPauses);
            o.put("pauseMinutes", r.pauseMinutes);
            o.put("allowlist", r.allowlist);
            o.put("scheduleMode", r.scheduleMode.name());
            JSONArray w = new JSONArray();
            for (Schedule x : r.schedules) {
                JSONObject y = new JSONObject();
                y.put("days", x.days);
                y.put("start", x.startMinute);
                y.put("end", x.endMinute);
                w.put(y);
            }
            o.put("schedules", w);
            o.put("sites", new JSONArray(r.sites));
            o.put("keywords", new JSONArray(r.keywords));
            o.put("blockUnsupportedBrowsers", r.blockUnsupportedBrowsers);
            o.put("blockPurchases", r.blockPurchases);
            if (!r.sections.isEmpty()) o.put("sections", new JSONArray(r.sections));
            a.put(o);
        }
        return a;
    }

    /** Lote 5: copia de seguridad: solo los bloqueos (sin estado de uso, pausas ni sesión). */
    public static String exportJson(List<BlockRule> rules) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("format", 1);
        j.put("kind", "foco-bloqueos");
        j.put("rules", rulesJson(rules));
        return j.toString(2);
    }

    /** Lote 5: valida una copia. Lanza JSONException si no es una copia de bloqueos de Foco. */
    public static List<BlockRule> parseBackup(String text) throws JSONException {
        if (text == null || text.length() > 1_000_000)
            throw new JSONException("Archivo demasiado grande");
        JSONObject j = new JSONObject(text);
        JSONArray a = j.optJSONArray("rules");
        if (a == null || j.optInt("format", 0) != 1)
            throw new JSONException("No es una copia de bloqueos de Foco");
        List<BlockRule> out = parseRules(a);
        if (out.size() > 50) out = new ArrayList<>(out.subList(0, 50));
        return out;
    }

    /**
     * Lote 5: sustituye los bloqueos por los de la copia. Se niega si hay un bloqueo activo con
     * estricto (lo decide quien llama).
     */
    public synchronized void replaceAll(List<BlockRule> in) {
        rules.clear();
        for (BlockRule r : in) {
            BlockRule c = r.copy();
            c.clamp();
            rules.add(c);
        }
        List<String> ids = new ArrayList<>();
        for (BlockRule r : rules) ids.add(r.id);
        state.retain(ids);
        write();
    }

    /** Lote 5: pausas usadas hoy sumando todos los bloqueos. */
    public synchronized int pausesToday() {
        long w = System.currentTimeMillis();
        String d = day(w);
        int n = 0;
        for (BlockRule r : rules) {
            RuleState.Entry e = state.of(r.id);
            RuleEngine.roll(r, e, w, d);
            if (d.equals(e.pausesDay)) n += e.pausesUsed;
        }
        return n;
    }

    public synchronized BlockRule purchaseBlocker() {
        BlockRule r = PurchaseGuard.blocker(rules, state, now(System.currentTimeMillis()));
        return r == null ? null : r.copy();
    }

    /** Secciones de esta app bloqueadas ahora (vacío: no hace falta leer la pantalla). */
    public synchronized Set<String> sections(String pkg) {
        return InAppFilter.wanted(rules, state, now(System.currentTimeMillis()), pkg);
    }

    public synchronized BlockRule sectionBlocker(String sectionId) {
        BlockRule r = InAppFilter.blocker(rules, state, now(System.currentTimeMillis()), sectionId);
        return r == null ? null : r.copy();
    }

    /** Regla del interruptor rápido «Bloquear Shorts y Reels», o null. */
    public synchronized BlockRule quickSections() {
        for (BlockRule r : rules)
            if (InAppFilter.QUICK_RULE_NAME.equals(r.name) && r.packages.isEmpty() && !r.allowlist)
                return r.copy();
        return null;
    }

    /** Interruptor «Bloquear TikTok por completo». */
    public synchronized BlockRule quickTikTok() {
        for (BlockRule r : rules)
            if (es.foco.next.core.TikTokBlock.RULE_NAME.equals(r.name) && !r.allowlist)
                return r.copy();
        return null;
    }

    /** 1.9: atajo «Bloquear Estados y canales de WhatsApp». */
    public synchronized BlockRule quickWhatsApp() {
        for (BlockRule r : rules)
            if (InAppFilter.QUICK_WHATSAPP_NAME.equals(r.name)
                    && r.packages.isEmpty()
                    && !r.allowlist) return r.copy();
        return null;
    }

    private void write() {
        file.write(encode());
    }

    /**
     * Auditoría: escrituras del servicio (cada minuto o al cambiar el estado) fuera del hilo
     * principal.
     */
    private void writeLater() {
        file.writeLater(encode());
    }

    private byte[] encode() {
        if (broken) {
            try {
                File b = file.file().getBaseFile();
                if (b.exists())
                    b.renameTo(new File(b.getParentFile(), "block-rules-v1.corrupt.json"));
            } catch (RuntimeException ignored) {
            }
            broken = false;
        }
        try {
            JSONObject j = new JSONObject();
            j.put("format", 1);
            JSONArray a = rulesJson(rules);
            j.put("rules", a);
            JSONObject s = new JSONObject();
            for (Map.Entry<String, RuleState.Entry> m : state.entries.entrySet()) {
                RuleState.Entry e = m.getValue();
                JSONObject o = new JSONObject();
                o.put("windowStart", e.windowStart);
                o.put("usageDay", e.usageDay);
                o.put("usedMillis", e.usedMillis);
                o.put("pausesDay", e.pausesDay);
                o.put("pausesUsed", e.pausesUsed);
                o.put("pausedUntil", e.pausedUntil);
                s.put(m.getKey(), o);
            }
            j.put("state", s);
            if (session != null) {
                JSONObject q = new JSONObject();
                q.put("kind", session.kind.name());
                q.put("start", session.start);
                q.put("until", session.until);
                q.put("focusMinutes", session.focusMinutes);
                q.put("breakMinutes", session.breakMinutes);
                q.put("cycles", session.cycles);
                j.put("session", q);
            }
            return j.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
