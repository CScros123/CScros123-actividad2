package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.AppConfig;
import es.foco.next.core.BlockRule;
import es.foco.next.core.FocusEngine;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * v48: todos tus ajustes en un archivo (Foco/Ajustes/foco-ajustes.json en tu Drive): el plan y sus
 * tiempos, Foco Core (tareas y dinero), los informes, los bloqueos con nombre y las preferencias
 * (noche, racha y cupones, categorías, inicio y tema, sonido, perfil de NextDNS). Sustituye a las
 * copias manuales.
 *
 * <p>Nunca guarda ni restaura lo de seguridad: candado del modo estricto, esperas de 24 h, modo y
 * Enfoque en curso, enfriamientos, cambios de DNS pendientes, ni la clave de NextDNS (va cifrada y
 * se escribe otra vez). El ánimo y las notas de «¿me aporta?» tampoco salen del teléfono.
 */
public final class SettingsSnapshot {
    private SettingsSnapshot() {}

    public static final int FORMAT = 1;
    public static final String FILE = "foco-ajustes.json";

    /** Preferencias que viajan: archivo → prefijos de clave permitidos ("" = todas). */
    static final String[][] PREFS = {
        {"foco-cuidado", "noche"},
        {"foco-premios", ""},
        {"foco-categorias", ""},
        {"foco-home", ""},
        {"foco-ambient", ""},
        {"foco-soundscape", ""},
        {"foco-nextdns", "perfil_api"},
        {"foco-respaldo", "animo", "id_hoja", "diseno"},
        {"foco-noche-avisos", "activo"},
    };

    static boolean allowed(String[] spec, String key) {
        for (int i = 1; i < spec.length; i++)
            if (spec[i].isEmpty() || key.startsWith(spec[i])) return true;
        return false;
    }

    /** La copia completa. Hilo de fondo. */
    public static String export(Context c) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("formato", FORMAT);
        out.put("creado", java.time.LocalDateTime.now().withNano(0).toString());
        Repository repo = Repository.get(c);
        out.put("plan", new JSONObject(repo.exportBackup()));
        out.put(FocoCoreBackup.KEY, FocoCoreBackup.export(c));
        out.put("informes", Inspector.backup(c));
        out.put("bloqueos", new JSONObject(RuleStore.exportJson(RuleStore.get(c).rules())));
        JSONObject prefs = new JSONObject();
        for (String[] spec : PREFS) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e :
                    c.getSharedPreferences(spec[0], Context.MODE_PRIVATE).getAll().entrySet()) {
                if (!allowed(spec, e.getKey())) continue;
                JSONObject t = typed(e.getValue());
                if (t != null) o.put(e.getKey(), t);
            }
            prefs.put(spec[0], o);
        }
        out.put("preferencias", prefs);
        return out.toString();
    }

    private static JSONObject typed(Object v) throws JSONException {
        JSONObject t = new JSONObject();
        if (v instanceof String) t.put("s", v);
        else if (v instanceof Long) t.put("l", (long) (Long) v);
        else if (v instanceof Integer) t.put("i", (int) (Integer) v);
        else if (v instanceof Boolean) t.put("b", (boolean) (Boolean) v);
        else if (v instanceof Float) t.put("f", (double) (Float) v);
        else if (v instanceof Set) {
            JSONArray a = new JSONArray();
            for (Object x : (Set<?>) v) a.put(String.valueOf(x));
            t.put("set", a);
        } else return null;
        return t;
    }

    /**
     * Restaura la copia. Con el candado del modo estricto no se restaura nada (podría aflojar el
     * plan). Devuelve el texto para la persona. Hilo de fondo.
     */
    public static String restore(Context c, String json) {
        if (StrictAdmin.lockActive(c))
            return "Modo estricto: con el candado puesto no se puede restaurar. Inténtalo cuando termine.";
        JSONObject in;
        try {
            in = new JSONObject(json);
        } catch (JSONException e) {
            return "La copia de ajustes está dañada.";
        }
        if (in.optInt("formato", 0) < 1) return "Esa no es una copia de ajustes de Foco.";
        int parts = 0;
        try {
            JSONObject plan = in.optJSONObject("plan");
            if (plan != null) {
                Repository.get(c).importBackup(plan.toString());
                parts++;
            }
        } catch (RuntimeException e) {
            return e.getMessage() == null ? "No se pudo restaurar el plan." : e.getMessage();
        }
        try {
            JSONObject core = in.optJSONObject(FocoCoreBackup.KEY);
            if (core != null) {
                FocoCoreBackup.restore(c, core);
                parts++;
            }
            JSONObject reports = in.optJSONObject("informes");
            if (reports != null) {
                Inspector.restore(c, reports);
                parts++;
            }
            JSONObject rules = in.optJSONObject("bloqueos");
            if (rules != null) {
                List<BlockRule> list = RuleStore.parseBackup(rules.toString());
                AppConfig config = Repository.get(c).config();
                for (BlockRule r : list) r.packages.removeIf(p -> FocusEngine.essential(config, p));
                RuleStore.get(c).replaceAll(list);
                FocusAccessibilityService.rulesChanged();
                parts++;
            }
            JSONObject prefs = in.optJSONObject("preferencias");
            if (prefs != null) {
                for (String[] spec : PREFS) {
                    JSONObject o = prefs.optJSONObject(spec[0]);
                    if (o == null) continue;
                    SharedPreferences.Editor e =
                            c.getSharedPreferences(spec[0], Context.MODE_PRIVATE).edit();
                    Iterator<String> it = o.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (!allowed(spec, k)) continue;
                        JSONObject t = o.optJSONObject(k);
                        if (t == null) continue;
                        if (t.has("s")) e.putString(k, t.getString("s"));
                        else if (t.has("l")) e.putLong(k, t.getLong("l"));
                        else if (t.has("i")) e.putInt(k, t.getInt("i"));
                        else if (t.has("b")) e.putBoolean(k, t.getBoolean("b"));
                        else if (t.has("f")) e.putFloat(k, (float) t.getDouble("f"));
                        else if (t.has("set")) {
                            JSONArray a = t.getJSONArray("set");
                            Set<String> s = new HashSet<>();
                            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                            e.putStringSet(k, s);
                        }
                    }
                    e.apply();
                }
                parts++;
            }
        } catch (JSONException | RuntimeException e) {
            FocusAccessibilityService.checkpoint();
            return "Restauré una parte de tus ajustes; el resto de la copia no se pudo leer.";
        }
        CareStore.night(c);
        FocusAccessibilityService.checkpoint();
        return parts == 0 ? "La copia estaba vacía." : "Listo: tus ajustes están de vuelta.";
    }
}
