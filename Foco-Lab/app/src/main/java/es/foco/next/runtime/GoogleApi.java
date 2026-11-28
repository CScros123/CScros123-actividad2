package es.foco.next.runtime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v47: Drive v3 y Sheets v4 por REST con HttpURLConnection (sin librerías nuevas). Solo habla con
 * www.googleapis.com y sheets.googleapis.com. El token va en la cabecera y nunca en un texto
 * guardado.
 */
public final class GoogleApi {
    private GoogleApi() {}

    static final String DRIVE = "https://www.googleapis.com/drive/v3/",
            UPLOAD = "https://www.googleapis.com/upload/drive/v3/files",
            SHEETS = "https://sheets.googleapis.com/v4/spreadsheets";
    static final String FOLDER = "application/vnd.google-apps.folder";

    public static final class HttpError extends Exception {
        public final int code;

        HttpError(int code, String method) {
            super("Google " + code + " " + method);
            this.code = code;
        }
    }

    // ---------------- HTTP ----------------

    static String call(String method, String url, String token, String type, byte[] body)
            throws Exception {
        if (!url.startsWith("https://www.googleapis.com/")
                && !url.startsWith("https://sheets.googleapis.com/"))
            throw new IllegalArgumentException("solo Google");
        String real = method;
        if ("PATCH".equals(method)) real = "POST"; // HttpURLConnection no admite PATCH
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setRequestMethod(real);
            if (!real.equals(method)) h.setRequestProperty("X-HTTP-Method-Override", method);
            h.setConnectTimeout(15_000);
            h.setReadTimeout(30_000);
            h.setRequestProperty("Authorization", "Bearer " + token);
            h.setRequestProperty("Accept", "application/json");
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", type);
                h.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = h.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = h.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpError(code, method);
            try (InputStream in = h.getInputStream()) {
                return new String(read(in), StandardCharsets.UTF_8);
            } catch (java.io.IOException empty) {
                return "";
            }
        } finally {
            h.disconnect();
        }
    }

    static byte[] read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0 && out.size() < 8_000_000) out.write(b, 0, n);
        return out.toByteArray();
    }

    static String json(String method, String url, String token, String body) throws Exception {
        return call(
                method,
                url,
                token,
                "application/json; charset=UTF-8",
                body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    // ---------------- Drive ----------------

    /** Correo de la cuenta (solo para mostrarlo en Ajustes; no sale del teléfono). */
    static String accountEmail(String token) throws Exception {
        JSONObject o =
                new JSONObject(json("GET", DRIVE + "about?fields=user(emailAddress)", token, null));
        return o.optJSONObject("user") == null
                ? ""
                : o.optJSONObject("user").optString("emailAddress", "");
    }

    /** Archivos (id → nombre) que cumplen la consulta. Solo ve los creados por Foco. */
    static Map<String, String> list(String token, String q) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        String page = null;
        do {
            String url =
                    DRIVE
                            + "files?spaces=drive&pageSize=200&fields=nextPageToken,files(id,name)&q="
                            + enc(q)
                            + (page == null ? "" : "&pageToken=" + enc(page));
            JSONObject o = new JSONObject(json("GET", url, token, null));
            JSONArray a = o.optJSONArray("files");
            if (a != null)
                for (int i = 0; i < a.length(); i++) {
                    JSONObject f = a.getJSONObject(i);
                    out.put(f.getString("id"), f.optString("name", ""));
                }
            page = o.optString("nextPageToken", null);
            if (page != null && page.isEmpty()) page = null;
        } while (page != null && out.size() < 2000);
        return out;
    }

    static String q(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Carpeta con ese nombre dentro de {@code parent} ("root" = Mi unidad); la crea si falta. */
    static String folder(String token, String name, String parent) throws Exception {
        Map<String, String> found =
                list(
                        token,
                        "mimeType='"
                                + FOLDER
                                + "' and name="
                                + q(name)
                                + " and "
                                + q(parent)
                                + " in parents and trashed=false");
        if (!found.isEmpty()) return found.keySet().iterator().next();
        JSONObject meta =
                new JSONObject()
                        .put("name", name)
                        .put("mimeType", FOLDER)
                        .put("parents", new JSONArray().put(parent));
        return new JSONObject(json("POST", DRIVE + "files?fields=id", token, meta.toString()))
                .getString("id");
    }

    /** Archivos de una carpeta (id → nombre). */
    static Map<String, String> children(String token, String folder) throws Exception {
        return list(
                token, q(folder) + " in parents and trashed=false and mimeType!='" + FOLDER + "'");
    }

    static Map<String, String> subfolders(String token, String folder) throws Exception {
        return list(
                token, q(folder) + " in parents and trashed=false and mimeType='" + FOLDER + "'");
    }

    /**
     * Sube un archivo. Si ya hay uno con ese nombre en la carpeta, reemplaza su contenido (y borra
     * duplicados de un fallo anterior). Devuelve el id.
     */
    static String put(String token, String folder, String name, String mime, byte[] data)
            throws Exception {
        String[] same =
                es.foco.next.core.backup.SheetRows.replaceTarget(children(token, folder), name);
        for (int i = 1; i < same.length; i++) delete(token, same[i]);
        if (same.length > 0) {
            try {
                call(
                        "PATCH",
                        UPLOAD + "/" + same[0] + "?uploadType=media&fields=id",
                        token,
                        mime,
                        data);
                return same[0];
            } catch (HttpError e) {
                if (e.code == 401) throw e;
                delete(token, same[0]); // sin PATCH: se borra y se crea de nuevo
            }
        }
        String boundary = "foco" + System.nanoTime();
        JSONObject meta =
                new JSONObject()
                        .put("name", name)
                        .put("mimeType", mime)
                        .put("parents", new JSONArray().put(folder));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head =
                "--"
                        + boundary
                        + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                        + meta
                        + "\r\n--"
                        + boundary
                        + "\r\nContent-Type: "
                        + mime
                        + "\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        String r =
                call(
                        "POST",
                        UPLOAD + "?uploadType=multipart&fields=id",
                        token,
                        "multipart/related; boundary=" + boundary,
                        body.toByteArray());
        return new JSONObject(r).getString("id");
    }

    static void delete(String token, String id) throws Exception {
        try {
            json("DELETE", DRIVE + "files/" + enc(id), token, null);
        } catch (HttpError e) {
            if (e.code != 404) throw e;
        }
    }

    static byte[] download(String token, String id) throws Exception {
        HttpURLConnection h =
                (HttpURLConnection)
                        new URL(DRIVE + "files/" + enc(id) + "?alt=media").openConnection();
        try {
            h.setConnectTimeout(15_000);
            h.setReadTimeout(30_000);
            h.setRequestProperty("Authorization", "Bearer " + token);
            int code = h.getResponseCode();
            if (code != 200) throw new HttpError(code, "GET");
            try (InputStream in = h.getInputStream()) {
                return read(in);
            }
        } finally {
            h.disconnect();
        }
    }

    // ---------------- Sheets ----------------

    static String createSheet(String token, String body) throws Exception {
        return new JSONObject(json("POST", SHEETS + "?fields=spreadsheetId", token, body))
                .getString("spreadsheetId");
    }

    /** Títulos de las pestañas. 404 si la hoja ya no existe (se borró). */
    static Set<String> tabs(String token, String id) throws Exception {
        Set<String> out = new HashSet<>();
        JSONObject o =
                new JSONObject(
                        json(
                                "GET",
                                SHEETS + "/" + enc(id) + "?fields=sheets.properties.title",
                                token,
                                null));
        JSONArray a = o.optJSONArray("sheets");
        if (a != null)
            for (int i = 0; i < a.length(); i++)
                out.add(a.getJSONObject(i).getJSONObject("properties").optString("title"));
        return out;
    }

    static void batchUpdate(String token, String id, String body) throws Exception {
        json("POST", SHEETS + "/" + enc(id) + ":batchUpdate", token, body);
    }

    static void valuesBatchUpdate(String token, String id, String body) throws Exception {
        json("POST", SHEETS + "/" + enc(id) + "/values:batchUpdate", token, body);
    }

    static void append(String token, String id, String range, String body) throws Exception {
        json(
                "POST",
                SHEETS
                        + "/"
                        + enc(id)
                        + "/values/"
                        + enc(range)
                        + ":append?valueInputOption=USER_ENTERED&insertDataOption=OVERWRITE",
                token,
                body);
    }

    /**
     * v47b: gráficos del Panel → {id, título, número de series}. Google quita las series de un
     * gráfico creado sin datos; así se detectan para rehacerlos.
     */
    static List<Object[]> charts(String token, String id, int sheetId) throws Exception {
        JSONObject o =
                new JSONObject(
                        json(
                                "GET",
                                SHEETS
                                        + "/"
                                        + enc(id)
                                        + "?fields="
                                        + enc(
                                                "sheets(properties.sheetId,charts(chartId,spec(title,basicChart.series)))"),
                                token,
                                null));
        List<Object[]> out = new ArrayList<>();
        JSONArray sheets = o.optJSONArray("sheets");
        if (sheets == null) return out;
        for (int i = 0; i < sheets.length(); i++) {
            JSONObject sh = sheets.getJSONObject(i);
            JSONObject props = sh.optJSONObject("properties");
            if (props == null || props.optInt("sheetId", -1) != sheetId) continue;
            JSONArray charts = sh.optJSONArray("charts");
            if (charts == null) continue;
            for (int j = 0; j < charts.length(); j++) {
                JSONObject ch = charts.getJSONObject(j);
                JSONObject spec = ch.optJSONObject("spec");
                String title = spec == null ? "" : spec.optString("title", "");
                JSONObject basic = spec == null ? null : spec.optJSONObject("basicChart");
                JSONArray series = basic == null ? null : basic.optJSONArray("series");
                out.add(
                        new Object[] {
                            ch.optInt("chartId"), title, series == null ? 0 : series.length()
                        });
            }
        }
        return out;
    }

    /** Columna A tal como se ve (la fila 0 es el encabezado). */
    static List<String> columnA(String token, String id, String tab) throws Exception {
        String range = es.foco.next.core.backup.SheetLayout.a1(tab, "A:A");
        JSONObject o =
                new JSONObject(
                        json(
                                "GET",
                                SHEETS
                                        + "/"
                                        + enc(id)
                                        + "/values/"
                                        + enc(range)
                                        + "?majorDimension=ROWS&valueRenderOption=FORMATTED_VALUE",
                                token,
                                null));
        List<String> out = new ArrayList<>();
        JSONArray rows = o.optJSONArray("values");
        if (rows != null)
            for (int i = 0; i < rows.length(); i++) {
                JSONArray r = rows.optJSONArray(i);
                out.add(r == null || r.length() == 0 ? "" : r.optString(0, ""));
            }
        return out;
    }
}
