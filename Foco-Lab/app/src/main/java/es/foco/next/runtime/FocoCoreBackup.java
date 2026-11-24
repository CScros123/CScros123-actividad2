package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Foco Core dentro de la copia de Foco completo: listas, plan semanal, eventos, lo marcado, el
 * presupuesto, la deuda y los gastos. Sin fotos (pesan y son privadas) y sin lo pasajero
 * (cronómetros en marcha, pausa activa, hora libre, avisos ya enviados).
 */
public final class FocoCoreBackup {
    private FocoCoreBackup() {}

    public static final String KEY = "focoCore";
    private static final String[] PREFS = {"foco-rutina", "foco-dinero"};
    private static final String[] SKIP = {"crono_", "cronoMs_", "castigoHasta", "castigoTarea", "libreHasta", "libreDia", "avisado_"};

    private static boolean skip(String k) {
        for (String s : SKIP) if (k.startsWith(s)) return true;
        return false;
    }

    public static JSONObject export(Context c) throws JSONException {
        JSONObject out = new JSONObject();
        for (String name : PREFS) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                if (skip(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject t = new JSONObject();
                if (v instanceof String) t.put("s", v);
                else if (v instanceof Long) t.put("l", (long) (Long) v);
                else if (v instanceof Integer) t.put("i", (int) (Integer) v);
                else if (v instanceof Boolean) t.put("b", (boolean) (Boolean) v);
                else if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                    t.put("set", a);
                } else continue;
                o.put(e.getKey(), t);
            }
            out.put(name, o);
        }
        return out;
    }

    /** Sustituye Foco Core por lo de la copia. Ignora lo que no entiende. */
    public static void restore(Context c, JSONObject in) throws JSONException {
        for (String name : PREFS) {
            JSONObject o = in.optJSONObject(name);
            if (o == null) continue;
            SharedPreferences.Editor e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (skip(k)) continue;
                JSONObject t = o.optJSONObject(k);
                if (t == null) continue;
                if (t.has("s")) e.putString(k, t.getString("s"));
                else if (t.has("l")) e.putLong(k, t.getLong("l"));
                else if (t.has("i")) e.putInt(k, t.getInt("i"));
                else if (t.has("b")) e.putBoolean(k, t.getBoolean("b"));
                else if (t.has("set")) {
                    JSONArray a = t.getJSONArray("set");
                    Set<String> s = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                    e.putStringSet(k, s);
                }
            }
            e.apply();
        }
        RoutineStore.scheduleNext(c);
    }
}
