package org.havenapp.main.utils;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Ignore;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class CryptoUtilsTest {

    @Test
    public void testConstantTimeEquals() {
        // Test equal strings
        Assert.assertTrue("Equal strings should match",
            CryptoUtils.constantTimeEquals("1234", "1234"));

        // Test different strings
        Assert.assertFalse("Different strings should not match",
            CryptoUtils.constantTimeEquals("1234", "1235"));

        // Test different lengths
        Assert.assertFalse("Different lengths should not match",
            CryptoUtils.constantTimeEquals("12345", "1234"));

        // Test empty strings
        Assert.assertTrue("Empty strings should match",
            CryptoUtils.constantTimeEquals("", ""));

        // Test null handling
        Assert.assertFalse("Null vs string should not match",
            CryptoUtils.constantTimeEquals(null, "1234"));
        Assert.assertFalse("String vs null should not match",
            CryptoUtils.constantTimeEquals("1234", null));
        Assert.assertFalse("Null vs null should not match (by design)",
            CryptoUtils.constantTimeEquals(null, null));

        // Test unicode
        Assert.assertTrue("Unicode strings should match",
            CryptoUtils.constantTimeEquals("🔒", "🔒"));
        Assert.assertFalse("Different unicode should not match",
            CryptoUtils.constantTimeEquals("🔒", "🔓"));
    }

    @Test
    public void testHashString() {
        String input = "test_pin_1234";
        String hash1 = CryptoUtils.hashString(input);
        String hash2 = CryptoUtils.hashString(input);

        Assert.assertNotNull("Hash should not be null", hash1);
        Assert.assertEquals("Same input should produce same hash", hash1, hash2);
        Assert.assertEquals("SHA-256 should produce 44 char base64", 44, hash1.length());

        // Different input should produce different hash
        String hash3 = CryptoUtils.hashString("different");
        Assert.assertNotEquals("Different inputs should produce different hashes", hash1, hash3);
    }

    @Test
    @Ignore("Android Keystore is unavailable in JVM Robolectric tests; covered on device.")
    public void testEncryptedPrefs() {
        Context context = ApplicationProvider.getApplicationContext();

        String testKey = "test_key_" + System.currentTimeMillis();
        String testValue = "sensitive_value_123";

        // Store encrypted
        CryptoUtils.putEncryptedString(context, testKey, testValue);

        // Retrieve encrypted
        String retrieved = CryptoUtils.getEncryptedString(context, testKey, "default");

        Assert.assertEquals("Retrieved value should match", testValue, retrieved);

        // Test default value for non-existent key
        String defaultVal = CryptoUtils.getEncryptedString(context, "nonexistent_key", "default_value");
        Assert.assertEquals("Should return default for non-existent key", "default_value", defaultVal);

        // Test removal
        CryptoUtils.removeEncrypted(context, testKey);
        String afterRemoval = CryptoUtils.getEncryptedString(context, testKey, "default");
        Assert.assertEquals("Should return default after removal", "default", afterRemoval);
    }
}
