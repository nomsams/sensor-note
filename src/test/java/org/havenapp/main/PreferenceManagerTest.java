package org.havenapp.main;

import org.junit.Assert;
import org.junit.Test;
import androidx.test.core.app.ApplicationProvider;

public class PreferenceManagerTest {

    @Test
    public void testPreferenceManagerCreation() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        Assert.assertNotNull(\"PreferenceManager should be created\", prefs);
        Assert.assertNotNull(\"SharedPreferences should not be null\", prefs.appSharedPrefs);
    }

    @Test
    public void testTelegramTokenEncryption() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        String testToken = \"test_bot_token_12345\";
        String testChatId = \"-123456789\";
        
        prefs.setTelegramBotToken(testToken);
        prefs.setTelegramChatId(testChatId);
        
        String retrievedToken = prefs.getTelegramBotToken();
        String retrievedChatId = prefs.getTelegramChatId();
        
        Assert.assertEquals(\"Token should be encrypted and retrievable\", testToken, retrievedToken);
        Assert.assertEquals(\"Chat ID should be encrypted and retrievable\", testChatId, retrievedChatId);
        
        Assert.assertTrue(\"Should be configured after setting both\", prefs.isTelegramConfigured());
    }

    @Test
    public void testDisarmCodeHashing() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        String testCode = \"mysecretcode123\";
        
        prefs.setDisarmCode(testCode);
        
        // Verify code works
        Assert.assertTrue(\"Correct code should verify\", prefs.verifyDisarmCode(testCode));
        Assert.assertFalse(\"Incorrect code should not verify\", prefs.verifyDisarmCode(\"wrongcode\"));
        Assert.assertFalse(\"Empty code should not verify\", prefs.verifyDisarmCode(\"\"));
        
        // Verify hasDisarmCode
        Assert.assertTrue(\"Should report has code after setting\", prefs.hasDisarmCode());
    }

    @Test
    public void testPanicCodeHashing() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        String testCode = \"panic123\";
        
        prefs.setPanicCode(testCode);
        
        Assert.assertTrue(\"Correct panic code should verify\", prefs.verifyPanicCode(testCode));
        Assert.assertFalse(\"Incorrect panic code should not verify\", prefs.verifyPanicCode(\"wrong\"));
        Assert.assertTrue(\"Should report has panic code\", prefs.hasPanicCode());
    }

    @Test
    public void testDefaultValues() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        Assert.assertEquals(\"Default accelerometer sensitivity\", PreferenceManager.HIGH, prefs.getAccelerometerSensitivity());
        Assert.assertEquals(\"Default microphone sensitivity\", PreferenceManager.MEDIUM, prefs.getMicrophoneSensitivity());
        Assert.assertEquals(\"Default EMF sensitivity\", \"25\", prefs.getEmfSensitivity());
        Assert.assertEquals(\"Default pressure sensitivity\", 0.20f, prefs.getPressureSensitivity(), 0.001f);
        Assert.assertEquals(\"Default camera\", PreferenceManager.FRONT, prefs.getCamera());
    }
}
