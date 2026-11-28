package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * v47: secretos cifrados con una llave AES del Android Keystore (no sale del chip y no se puede
 * copiar). Sin dependencias nuevas: es lo que usa por dentro EncryptedSharedPreferences, que está
 * en desuso. Solo guarda la clave de API de NextDNS. Nunca se escribe en el log.
 */
public final class SecretBox {
    private SecretBox() {}

    private static final String STORE = "AndroidKeyStore",
            ALIAS = "foco-secretos",
            PREFS = "foco-secretos";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        g.init(
                new KeyGenParameterSpec.Builder(
                                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
        return g.generateKey();
    }

    /** Guarda (o borra con null/vacío) un secreto. false si el Keystore falló. */
    public static synchronized boolean put(Context c, String name, String value) {
        if (value == null || value.isEmpty()) {
            prefs(c).edit().remove(name).apply();
            return true;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String packed =
                    Base64.encodeToString(iv, Base64.NO_WRAP)
                            + ":"
                            + Base64.encodeToString(data, Base64.NO_WRAP);
            prefs(c).edit().putString(name, packed).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** El secreto, o "" si no hay o no se pudo descifrar (p. ej. tras restaurar en otro teléfono). */
    public static synchronized String get(Context c, String name) {
        String packed = prefs(c).getString(name, "");
        int i = packed.indexOf(':');
        if (i <= 0) return "";
        try {
            byte[] iv = Base64.decode(packed.substring(0, i), Base64.NO_WRAP);
            byte[] data = Base64.decode(packed.substring(i + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean has(Context c, String name) {
        return !get(c, name).isEmpty();
    }
}
