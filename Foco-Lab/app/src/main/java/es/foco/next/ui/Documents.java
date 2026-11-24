package es.foco.next.ui;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Auditoría: leer y escribir documentos elegidos por el usuario (copias, CSV). Antes, el mismo
 * bucle copiado en cada pantalla.
 */
final class Documents {
    private Documents() {}

    /** Tamaño máximo de una copia que se importa (plan o bloqueos). */
    static final int MAX_IMPORT = 1_000_000;

    /** Lee como UTF-8; lanza IOException si no se puede abrir o pasa de {@code max} bytes. */
    static String read(Context c, Uri uri, int max) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Sin acceso al archivo");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > max) throw new IOException("Archivo demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Sustituye el contenido del documento por {@code text} en UTF-8. */
    static void write(Context c, Uri uri, String text) throws IOException {
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Sin acceso al archivo");
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
