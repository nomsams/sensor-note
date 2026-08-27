package org.havenapp.main.sensors;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import android.content.Context;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.test.core.app.ApplicationProvider;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class MicrophoneMonitorTest {

    @Test
    public void testMicrophoneMonitorCreation() {
        Context context = ApplicationProvider.getApplicationContext();
        MicrophoneMonitor monitor = new MicrophoneMonitor(context);
        
        Assert.assertNotNull("Monitor should be created", monitor);
    }

    @Test
    public void testSensitivityLevels() {
        Context context = ApplicationProvider.getApplicationContext();
        org.havenapp.main.PreferenceManager prefs = new org.havenapp.main.PreferenceManager(context);
        
        // Test High sensitivity
        prefs.setMicrophoneSensitivity("High");
        MicrophoneMonitor monitor = new MicrophoneMonitor(context);
        double threshold = getPrivateFieldDouble(monitor, "mNoiseThreshold");
        Assert.assertEquals(40.0, threshold, 0.001);
        
        // Test Medium sensitivity
        prefs.setMicrophoneSensitivity("Medium");
        monitor = new MicrophoneMonitor(context);
        threshold = getPrivateFieldDouble(monitor, "mNoiseThreshold");
        Assert.assertEquals(60.0, threshold, 0.001);
        
        // Test custom numeric sensitivity
        prefs.setMicrophoneSensitivity("55");
        monitor = new MicrophoneMonitor(context);
        threshold = getPrivateFieldDouble(monitor, "mNoiseThreshold");
        Assert.assertEquals(55.0, threshold, 0.001);
    }

    @Test
    public void testStop() {
        Context context = ApplicationProvider.getApplicationContext();
        MicrophoneMonitor monitor = new MicrophoneMonitor(context);
        
        // Should not throw
        monitor.stop(context);
    }

    private double getPrivateFieldDouble(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
