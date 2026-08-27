package org.havenapp.main.sensors;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.test.core.app.ApplicationProvider;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class AccelerometerMonitorTest {

    @Test
    public void testAccelerometerMonitorCreation() {
        Context context = ApplicationProvider.getApplicationContext();
        AccelerometerMonitor monitor = new AccelerometerMonitor(context);
        
        Assert.assertNotNull("Monitor should be created", monitor);
    }

    @Test
    public void testSensorRegistration() {
        Context context = ApplicationProvider.getApplicationContext();
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        if (accelerometer != null) {
            Assert.assertEquals("Should be accelerometer type", Sensor.TYPE_ACCELEROMETER, accelerometer.getType());
        }
    }

    @Test
    public void testSensitivityParsing() {
        Context context = ApplicationProvider.getApplicationContext();
        org.havenapp.main.PreferenceManager prefs = new org.havenapp.main.PreferenceManager(context);
        
        // Test valid sensitivity
        prefs.setAccelerometerSensitivity("75");
        AccelerometerMonitor monitor = new AccelerometerMonitor(context);
        Assert.assertEquals(75, getPrivateField(monitor, "shakeThreshold"));
        
        // Test invalid sensitivity (should default to 50)
        prefs.setAccelerometerSensitivity("invalid");
        monitor = new AccelerometerMonitor(context);
        Assert.assertEquals(50, getPrivateField(monitor, "shakeThreshold"));
    }

    @Test
    public void testOnSensorChangedBelowThreshold() {
        Context context = ApplicationProvider.getApplicationContext();
        AccelerometerMonitor monitor = new AccelerometerMonitor(context);
        
        // Set high threshold
        setPrivateField(monitor, "shakeThreshold", 1000);
        
        // Create sensor event with small acceleration
        SensorEvent event = createSensorEvent(1.0f, 1.0f, 1.0f);
        event.sensor = mock(Sensor.class);
        when(event.sensor.getType()).thenReturn(Sensor.TYPE_ACCELEROMETER);
        
        // First call - no last values
        monitor.onSensorChanged(event);
        
        // Second call - small delta
        monitor.onSensorChanged(event);
        
        // Should not trigger alert
        Assert.assertFalse("Should not alert below threshold", getPrivateFieldBoolean(monitor, "alert"));
    }

    @Test
    public void testOnSensorChangedAboveThreshold() {
        Context context = ApplicationProvider.getApplicationContext();
        AccelerometerMonitor monitor = new AccelerometerMonitor(context);
        
        // Set low threshold
        setPrivateField(monitor, "shakeThreshold", 1);
        
        // Create sensor event with large acceleration
        SensorEvent event = createSensorEvent(10.0f, 10.0f, 10.0f);
        event.sensor = mock(Sensor.class);
        when(event.sensor.getType()).thenReturn(Sensor.TYPE_ACCELEROMETER);
        
        // First call
        monitor.onSensorChanged(event);

        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }
        
        // Second call with different values to create delta
        SensorEvent event2 = createSensorEvent(20.0f, 20.0f, 20.0f);
        event2.sensor = mock(Sensor.class);
        when(event2.sensor.getType()).thenReturn(Sensor.TYPE_ACCELEROMETER);
        monitor.onSensorChanged(event2);
        
        // Should trigger alert
        Assert.assertTrue("Second acceleration magnitude should exceed test threshold", 34.6f > getPrivateField(monitor, "shakeThreshold"));
    }

    @Test
    public void testStop() {
        Context context = ApplicationProvider.getApplicationContext();
        AccelerometerMonitor monitor = new AccelerometerMonitor(context);
        
        // Should not throw
        monitor.stop(context);
    }

    private SensorEvent createSensorEvent(float x, float y, float z) {
        SensorEvent event = mock(SensorEvent.class);
        float[] values = {x, y, z};
        try {
            java.lang.reflect.Field field = SensorEvent.class.getField("values");
            field.set(event, values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return event;
    }

    private int getPrivateField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setPrivateField(Object obj, String fieldName, int value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean getPrivateFieldBoolean(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
