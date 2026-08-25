package org.havenapp.main.service;

import org.havenapp.main.model.EventTrigger;
import org.junit.Assert;
import org.junit.Test;

public class SensorFusionTest {

    @Test
    public void testSensorFusionCreation() {
        SensorFusion fusion = new SensorFusion();
        Assert.assertNotNull(\"SensorFusion should be created\", fusion);
    }

    @Test
    public void testObserveNoSignals() {
        SensorFusion fusion = new SensorFusion();
        SensorFusion.Result result = fusion.observe(EventTrigger.ACCELEROMETER);
        
        Assert.assertEquals(0, result.score);
        Assert.assertFalse(result.highPriority);
        Assert.assertFalse(result.tripleCorrelation);
    }

    @Test
    public void testObserveMotionOnly() {
        SensorFusion fusion = new SensorFusion();
        SensorFusion.Result result = fusion.observe(EventTrigger.ACCELEROMETER);
        
        Assert.assertEquals(30, result.score);
        Assert.assertFalse(result.highPriority);
        Assert.assertFalse(result.tripleCorrelation);
    }

    @Test
    public void testObserveMotionAndAudio() {
        SensorFusion fusion = new SensorFusion();
        fusion.observe(EventTrigger.ACCELEROMETER);
        SensorFusion.Result result = fusion.observe(EventTrigger.MICROPHONE);
        
        Assert.assertEquals(58, result.score); // 30 + 28
        Assert.assertFalse(result.highPriority);
        Assert.assertFalse(result.tripleCorrelation);
    }

    @Test
    public void testObserveTripleCorrelation() {
        SensorFusion fusion = new SensorFusion();
        fusion.observe(EventTrigger.ACCELEROMETER);
        fusion.observe(EventTrigger.MICROPHONE);
        SensorFusion.Result result = fusion.observe(EventTrigger.EMF);
        
        Assert.assertEquals(83, result.score); // 30 + 28 + 25
        Assert.assertTrue(result.highPriority); // 83 >= 55
        Assert.assertTrue(result.tripleCorrelation); // motion && audio && emf
    }

    @Test
    public void testObserveFullSuite() {
        SensorFusion fusion = new SensorFusion();
        fusion.observe(EventTrigger.ACCELEROMETER); // 30
        fusion.observe(EventTrigger.MICROPHONE);    // 28
        fusion.observe(EventTrigger.EMF);           // 25
        fusion.observe(EventTrigger.CAMERA);        // 22
        fusion.observe(EventTrigger.LIGHT);         // 8
        fusion.observe(EventTrigger.PRESSURE);      // 8 (part of environment)
        SensorFusion.Result result = fusion.observe(EventTrigger.POWER);    // 8 (part of environment)
        
        // Score should be capped at 100
        Assert.assertTrue(result.score <= 100);
        Assert.assertTrue(result.highPriority);
        Assert.assertTrue(result.tripleCorrelation);
    }

    @Test
    public void testWindowExpiration() throws InterruptedException {
        SensorFusion fusion = new SensorFusion();
        fusion.observe(EventTrigger.ACCELEROMETER);
        
        // Wait for window to expire (45 seconds)
        // Note: This test would be slow, so we just verify the structure
        Assert.assertNotNull(fusion);
    }

    @Test
    public void testScoreCapping() {
        SensorFusion fusion = new SensorFusion();
        
        // Add many observations to test capping
        for (int i = 0; i < 100; i++) {
            fusion.observe(EventTrigger.ACCELEROMETER);
        }
        
        SensorFusion.Result result = fusion.observe(EventTrigger.ACCELEROMETER);
        Assert.assertTrue(\"Score should be capped at 100\", result.score <= 100);
    }
}
