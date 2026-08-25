package org.havenapp.main.service;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.havenapp.main.PreferenceManager;

import static org.mockito.Mockito.*;

public class TelegramSenderTest {

    @Test
    public void testSendMessageDisabled() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        // Ensure telegram is disabled
        prefs.activateTelegram(false);
        
        // Should not throw, just return early
        TelegramSender.sendMessage(context, \"Test message\", null);
    }

    @Test
    public void testSendMessageNotConfigured() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager prefs = new PreferenceManager(context);
        
        prefs.activateTelegram(true);
        // But don't set token/chat ID
        
        // Should not throw, just return early
        TelegramSender.sendMessage(context, \"Test message\", null);
    }

    @Test
    public void testEscapeMethod() {
        // Test the private escape method via reflection
        try {
            java.lang.reflect.Method method = TelegramSender.class.getDeclaredMethod(\"escape\", String.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(null, \"Hello \\\"World\\\"\\n\");
            Assert.assertEquals(\"Should escape backslashes and quotes\", \"Hello \\\\\\\"World\\\\\\\"\\\\n\", result);
            
            String result2 = (String) method.invoke(null, \"Simple text\");
            Assert.assertEquals(\"Simple text should remain unchanged\", \"Simple text\", result2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCreateRequestText() {
        try {
            java.lang.reflect.Method method = TelegramSender.class.getDeclaredMethod(\"createRequest\", String.class, String.class, String.class, java.io.File.class);
            method.setAccessible(true);
            
            okhttp3.Request request = (okhttp3.Request) method.invoke(null, \"test_token\", \"12345\", \"Test message\", null);
            
            Assert.assertNotNull(\"Request should be created\", request);
            Assert.assertTrue(\"URL should contain token\", request.url().toString().contains(\"test_token\"));
            Assert.assertTrue(\"Should be sendMessage endpoint\", request.url().toString().contains(\"sendMessage\"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testCreateRequestWithAttachment() {
        try {
            java.lang.reflect.Method method = TelegramSender.class.getDeclaredMethod(\"createRequest\", String.class, String.class, String.class, java.io.File.class);
            method.setAccessible(true);
            
            java.io.File tempFile = java.io.File.createTempFile(\"test\", \".jpg\");
            tempFile.deleteOnExit();
            
            okhttp3.Request request = (okhttp3.Request) method.invoke(null, \"test_token\", \"12345\", \"Caption\", tempFile);
            
            Assert.assertNotNull(\"Request should be created\", request);
            Assert.assertTrue(\"Should be sendPhoto endpoint\", request.url().toString().contains(\"sendPhoto\"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
