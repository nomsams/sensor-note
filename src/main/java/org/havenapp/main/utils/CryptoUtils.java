package org.havenapp.main.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class CryptoUtils {
    private static final String TAG = "CryptoUtils";
    private static final String MASTER_KEY_ALIAS = "haven_master_key";
    private static final String ENCRYPTED_PREFS_NAME = "haven_encrypted_prefs";
    
    private static MasterKey masterKey = null;
    private static SharedPreferences encryptedPrefs = null;

    /**
     * Initialize the master key for encryption
     */
    public static synchronized MasterKey getMasterKey(Context context) {
        if (masterKey == null) {
            try {
                masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Failed to create master key", e);
                throw new RuntimeException("Failed to initialize encryption", e);
            }
        }
        return masterKey;
    }

    /**
     * Get encrypted shared preferences instance
     */
    public static synchronized SharedPreferences getEncryptedPrefs(Context context) {
        if (encryptedPrefs == null) {
            try {
                encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_PREFS_NAME,
                        getMasterKey(context),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Failed to create encrypted prefs", e);
                throw new RuntimeException("Failed to initialize encrypted preferences", e);
            }
        }
        return encryptedPrefs;
    }

    static void resetEncryptedPrefsForTest() {
        masterKey = null;
        encryptedPrefs = null;
    }

    /**
     * Store a sensitive string value encrypted
     */
    public static void putEncryptedString(Context context, String key, String value) {
            SharedPreferences prefs = getEncryptedPrefs(context);
            prefs.edit().putString(key, value).commit();
    }

    /**
     * Retrieve a sensitive string value encrypted
     */
    public static String getEncryptedString(Context context, String key, String defaultValue) {
            SharedPreferences prefs = getEncryptedPrefs(context);
            String value = prefs.getString(key, null);
            return value != null ? value : defaultValue;
    }

    /**
     * Remove an encrypted value
     */
    public static void removeEncrypted(Context context, String key) {
        try {
            SharedPreferences prefs = getEncryptedPrefs(context);
            prefs.edit().remove(key).commit();
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove encrypted value: " + key, e);
        }
    }

    /**
     * Constant-time comparison for sensitive strings (PINs, tokens)
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        int result = expectedBytes.length ^ actualBytes.length;
        for (int i = 0; i < Math.min(expectedBytes.length, actualBytes.length); i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    /**
     * Hash a string for secure storage (e.g., for verification without storing plaintext)
     */
    public static String hashString(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (java.security.NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available", e);
            return "";
        }
    }
}
