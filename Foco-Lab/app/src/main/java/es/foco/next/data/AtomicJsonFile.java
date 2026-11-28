package es.foco.next.data;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Auditoría: escritura atómica compartida por Repository y RuleStore.
 *
 * <ul>
 *   <li>{@link #write} escribe ya, en el hilo que llama (cambios del usuario: si falla, quien llama
 *       deshace y avisa).
 *   <li>{@link #writeLater} guarda la instantánea y la escribe en un hilo de E/S propio (puntos de
 *       control del servicio de Accesibilidad: el hilo principal no espera al disco). Varias
 *       instantáneas seguidas se funden en una sola escritura.
 *   <li>Cada instantánea lleva un número creciente: una escritura en segundo plano nunca pisa a
 *       otra más nueva.
 * </ul>
 *
 * El formato del archivo no cambia.
 */
final class AtomicJsonFile {
    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "foco-io");
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });
    private final AtomicFile file;
    private final String what;
    private final Object disk = new Object();
    private long seq, written, pendingSeq;
    private byte[] pending;
    private boolean queued;

    AtomicJsonFile(File base, String what) {
        file = new AtomicFile(base);
        this.what = what;
    }

    AtomicFile file() {
        return file;
    }

    boolean exists() {
        return file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists();
    }

    byte[] read() throws IOException {
        synchronized (disk) {
            return file.readFully();
        }
    }

    /** Escribe ahora. Lanza IllegalStateException si no se pudo. */
    void write(byte[] bytes) {
        long s;
        synchronized (this) {
            s = ++seq;
            pending = null;
        }
        try {
            commit(s, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron guardar " + what, e);
        }
    }

    /** Escribe en segundo plano; nunca lanza. */
    void writeLater(byte[] bytes) {
        synchronized (this) {
            pending = bytes;
            pendingSeq = ++seq;
            if (queued) return;
            queued = true;
        }
        try {
            IO.execute(this::drain);
        } catch (RuntimeException e) {
            synchronized (this) {
                queued = false;
            }
            drain();
        }
    }

    /** Escribe ya lo que quede pendiente (tests y cierre del servicio). */
    void flushPending() {
        drain();
    }

    private void drain() {
        byte[] b;
        long s;
        synchronized (this) {
            b = pending;
            s = pendingSeq;
            pending = null;
            queued = false;
        }
        if (b == null) return;
        try {
            commit(s, b);
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("Foco", "No se pudieron guardar " + what + " (se reintentará)", e);
        }
    }

    private void commit(long s, byte[] bytes) throws IOException {
        synchronized (disk) {
            if (s < written) return; // ya hay en disco una instantánea más nueva
            FileOutputStream out = null;
            try {
                out = file.startWrite();
                out.write(bytes);
                file.finishWrite(out);
                written = s;
            } catch (IOException e) {
                if (out != null) file.failWrite(out);
                throw e;
            }
        }
    }
}
