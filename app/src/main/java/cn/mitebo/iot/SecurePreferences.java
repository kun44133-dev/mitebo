package cn.mitebo.iot;

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

final class SecurePreferences {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "cn.mitebo.iot.preferences.v1";
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private SecurePreferences() {
    }

    static synchronized String get(Context context, String prefsName, String key, String fallback) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        String stored = prefs.getString(key, null);
        if (stored == null) {
            return fallback;
        }
        if (!stored.startsWith(PREFIX)) {
            // Migrate legacy plaintext immediately. If Keystore is unavailable, remove the
            // persisted secret and keep it only for this process invocation.
            if (!put(context, prefsName, key, stored)) {
                prefs.edit().remove(key).apply();
            }
            return stored;
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed encrypted preference");
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(prefsName, key));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            prefs.edit().remove(key).apply();
            return fallback;
        }
    }

    static synchronized boolean put(Context context, String prefsName, String key, String value) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        if (value == null) {
            prefs.edit().remove(key).apply();
            return true;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(aad(prefsName, key));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String encoded = PREFIX
                    + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString(key, encoded).apply();
            return true;
        } catch (Exception ignored) {
            prefs.edit().remove(key).apply();
            return false;
        }
    }

    static void remove(Context context, String prefsName, String key) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    private static byte[] aad(String prefsName, String key) {
        return (prefsName + ":" + key).getBytes(StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
