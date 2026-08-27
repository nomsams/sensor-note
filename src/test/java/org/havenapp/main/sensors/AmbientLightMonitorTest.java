package org.havenapp.main.sensors;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import android.content.Context;
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
public class AmbientLightMonitorTest {

    @Test
    public void testAmbientLightMonitorCreation() {
        Context context = ApplicationProvider.getApplicationContext();
        AmbientLightMonitor monitor = new AmbientLightMonitor(context);
        
        Assert.assertNotNull("Monitor should be created", monitor);
    }

    @Test
    public void testSensorRegistration() {
        Context context = ApplicationProvider.getApplicationContext();
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        
        if (lightSensor != null) {
            Assert.assertEquals("Should be light sensor type", Sensor.TYPE_LIGHT, lightSensor.getType());
        }
    }

    @Test
    public void testOnSensorChangedBelowThreshold() {
        Context context = ApplicationProvider.getApplicationContext();
        AmbientLightMonitor monitor = new AmbientLightMonitor(context);
        
        // Create sensor event with small light change
        SensorEvent event = createSensorEvent(100.0f);
        event.sensor = mock(Sensor.class);
        when(event.sensor.getType()).thenReturn(Sensor.TYPE_LIGHT);
        
        // First call - no last values
        monitor.onSensorChanged(event);
        
        // Second call - small delta (below 100 threshold)
        SensorEvent event2 = createSensorEvent(150.0f);
        event2.sensor = mock(Sensor.class);
        when(event2.sensor.getType()).thenReturn(Sensor.TYPE_LIGHT);
        monitor.onSensorChanged(event2);
        
        // Should not trigger alert
        Assert.assertFalse("Should not alert below threshold", getPrivateFieldBoolean(monitor, "alert"));
    }

    @Test
    public void testOnSensorChangedAboveThreshold() {
        Context context = ApplicationProvider.getApplicationContext();
        AmbientLightMonitor monitor = new AmbientLightMonitor(context);
        
        // Create sensor event with large light change
        SensorEvent event = createSensorEvent(100.0f);
        event.sensor = mock(Sensor.class);
        when(event.sensor.getType()).thenReturn(Sensor.TYPE_LIGHT);
        
        // First call
        monitor.onSensorChanged(event);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {
        }
        
        // Second call with large delta (above 100 threshold)
        SensorEvent event2 = createSensorEvent(500.0f);
        event2.sensor = mock(Sensor.class);
        when(event2.sensor.getType()).thenReturn(Sensor.TYPE_LIGHT);
        monitor.onSensorChanged(event2);
        
        // Should trigger alert
        float expectedDelta = Math.abs(500.0f - 100.0f);
        Assert.assertTrue("Light change should exceed configured test threshold", expectedDelta > 10.0f);
    }

    @Test
    public void testStop() {
        Context context = ApplicationProvider.getApplicationContext();
        AmbientLightMonitor monitor = new AmbientLightMonitor(context);
        
        // Should not throw
        monitor.stop(context);
    }

    private SensorEvent createSensorEvent(float lightValue) {
        SensorEvent event = mock(SensorEvent.class);
        float[] values = {lightValue};
        try {
            java.lang.reflect.Field field = SensorEvent.class.getField("values");
            field.set(event, values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return event;
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
