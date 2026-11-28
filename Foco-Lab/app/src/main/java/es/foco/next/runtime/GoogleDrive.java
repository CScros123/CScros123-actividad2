package es.foco.next.runtime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * v47: permiso de Google Drive con Google Identity Services (AuthorizationClient). Solo se pide
 * {@code drive.file}: Foco ve únicamente los archivos que él mismo crea (la carpeta Foco y la
 * hoja). No hay claves ni secretos de Google en el APK: Google reconoce la app por su paquete y la
 * huella SHA-1 de la llave de firma (se registran una vez en Google Cloud). El token nunca se
 * guarda ni se escribe en el log: se pide cada vez y lo cachea Google Play Services.
 */
public final class GoogleDrive {
    private GoogleDrive() {}

    public static final String SCOPE = "https://www.googleapis.com/auth/drive.file";

    /** Request code de la pantalla de permiso de Google (MainActivity.onActivityResult). */
    public static final int REQUEST_CODE = 4701;

    /** El permiso caducó o se retiró: hay que volver a conectar desde Ajustes. */
    public static final class NeedsReconnect extends Exception {
        NeedsReconnect() {
            super("reconectar");
        }
    }

    static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)))
                .build();
    }

    /** Botón «Conectar Google Drive». {@code done} recibe el texto para la persona. */
    public static void connect(Activity a, Consumer<String> done) {
        try {
            Identity.getAuthorizationClient(a)
                    .authorize(request())
                    .addOnSuccessListener(
                            r -> {
                                if (r.hasResolution() && r.getPendingIntent() != null) {
                                    try {
                                        a.startIntentSenderForResult(
                                                r.getPendingIntent().getIntentSender(),
                                                REQUEST_CODE,
                                                null,
                                                0,
                                                0,
                                                0,
                                                null);
                                    } catch (Exception e) {
                                        done.accept("No se pudo abrir el permiso de Google.");
                                    }
                                } else authorized(a, r, done);
                            })
                    .addOnFailureListener(
                            e ->
                                    done.accept(
                                            "No se pudo conectar con Google. Revisa que el teléfono tenga Google Play y la app esté registrada (ver README)."));
        } catch (RuntimeException e) {
            done.accept("Google Play Services no está disponible en este teléfono.");
        }
    }

    /** Vuelta de la pantalla de permiso de Google. */
    public static void onResult(Activity a, Intent data, Consumer<String> done) {
        try {
            AuthorizationResult r =
                    Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data);
            authorized(a, r, done);
        } catch (Exception e) {
            done.accept("No se concedió el permiso de Drive.");
        }
    }

    private static void authorized(Context c, AuthorizationResult r, Consumer<String> done) {
        if (r == null || r.getAccessToken() == null) {
            done.accept("No se concedió el permiso de Drive.");
            return;
        }
        BackupStore.markConnected(c);
        String token = r.getAccessToken();
        Context app = c.getApplicationContext();
        new Thread(
                        () -> {
                            try {
                                BackupStore.setAccount(app, GoogleApi.accountEmail(token));
                            } catch (Exception ignored) {
                            }
                            BackupStore.schedule(app);
                        },
                        "foco-drive-cuenta")
                .start();
        done.accept("Google Drive conectado. Cada noche Foco guardará tu día ahí.");
    }

    /**
     * Token para trabajar en segundo plano. Si Google necesita que la persona vuelva a aceptar,
     * lanza {@link NeedsReconnect}. Hilo de fondo.
     */
    static String token(Context c) throws Exception {
        AuthorizationResult r =
                Tasks.await(
                        Identity.getAuthorizationClient(c.getApplicationContext())
                                .authorize(request()),
                        30,
                        TimeUnit.SECONDS);
        if (r.hasResolution() || r.getAccessToken() == null) throw new NeedsReconnect();
        return r.getAccessToken();
    }

    /** Un token que Google rechazó (401): se descarta para que el siguiente sea nuevo. */
    static void forget(Context c, String token) {
        try {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(c.getApplicationContext(), token);
        } catch (Exception ignored) {
        }
    }
}
