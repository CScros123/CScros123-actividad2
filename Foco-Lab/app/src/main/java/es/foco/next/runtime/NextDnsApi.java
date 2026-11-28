package es.foco.next.runtime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * v47: llamadas a la API oficial de NextDNS (https://api.nextdns.io, cabecera X-Api-Key). Solo
 * habla con ese servidor. La clave nunca se escribe en el log ni en los mensajes de error.
 *
 * <p>Endpoints usados (documentación oficial, nextdns.github.io/api): analytics/status,
 * analytics/domains (status, root, limit, from, to), parentalControl/services y denylist (GET,
 * POST {id, active}, DELETE …/id). No se usa PATCH: HttpURLConnection no lo admite; para cambiar
 * "activo" se borra y se vuelve a añadir.
 */
public final class NextDnsApi {
    private NextDnsApi() {}

    public static final String BASE = "https://api.nextdns.io/profiles/";

    /** Respuesta distinta de 2xx (o 200 con "errors"). Sin la clave en el texto. */
    public static final class HttpError extends Exception {
        public final int code;

        HttpError(int code, String what) {
            super("NextDNS " + code + (what == null ? "" : " " + what));
            this.code = code;
        }
    }

    public static String get(String url, String key) throws Exception {
        return call("GET", url, key, null);
    }

    public static String post(String url, String key, String json) throws Exception {
        return call("POST", url, key, json);
    }

    public static String delete(String url, String key) throws Exception {
        return call("DELETE", url, key, null);
    }

    private static String call(String method, String url, String key, String body)
            throws Exception {
        if (!url.startsWith(BASE)) throw new IllegalArgumentException("solo api.nextdns.io");
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setRequestMethod(method);
            h.setConnectTimeout(10_000);
            h.setReadTimeout(15_000);
            h.setRequestProperty("X-Api-Key", key);
            h.setRequestProperty("Accept", "application/json");
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = h.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = h.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpError(code, method);
            String text;
            try (InputStream in = h.getInputStream()) {
                text = read(in);
            } catch (java.io.IOException empty) {
                text = ""; // 204 sin cuerpo
            }
            // Algunos errores llegan con 200 y una lista "errors" (p. ej. dominio no válido).
            if (text.startsWith("{\"errors\"") || text.contains("\"errors\":[{"))
                throw new HttpError(code, "errors");
            return text;
        } finally {
            h.disconnect();
        }
    }

    static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0 && out.size() < 1_000_000) out.write(b, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /** id → activo de una lista (services o denylist): {"data":[{"id":"x","active":true}]}. */
    public static java.util.Map<String, Boolean> actives(String json) throws org.json.JSONException {
        java.util.Map<String, Boolean> out = new java.util.HashMap<>();
        org.json.JSONArray a = new org.json.JSONObject(json).optJSONArray("data");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "");
            if (!id.isEmpty()) out.put(id.toLowerCase(java.util.Locale.ROOT), o.optBoolean("active", true));
        }
        return out;
    }

    /** Dominios de analytics/domains → (dominio raíz si viene, si no el dominio, consultas). */
    public static java.util.List<es.foco.next.core.backup.DayReport.Item> domains(String json)
            throws org.json.JSONException {
        java.util.List<es.foco.next.core.backup.DayReport.Item> out = new java.util.ArrayList<>();
        org.json.JSONArray a = new org.json.JSONObject(json).optJSONArray("data");
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String d = o.optString("root", "");
            if (d.isEmpty()) d = o.optString("domain", "");
            if (!d.isEmpty())
                out.add(new es.foco.next.core.backup.DayReport.Item(d, o.optLong("queries", 0)));
        }
        return out;
    }

    /** analytics/status → {total, bloqueadas}. */
    public static long[] status(String json) throws org.json.JSONException {
        long total = 0, blocked = 0;
        org.json.JSONArray a = new org.json.JSONObject(json).optJSONArray("data");
        if (a != null)
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                long q = o.optLong("queries", 0);
                total += q;
                if ("blocked".equals(o.optString("status"))) blocked += q;
            }
        return new long[] {total, blocked};
    }
}
