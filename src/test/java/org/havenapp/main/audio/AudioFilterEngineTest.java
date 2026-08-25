package org.havenapp.main.audio;

import org.junit.Assert;
import org.junit.Test;

public class AudioFilterEngineTest {

    @Test
    public void testFilterProfileDefaults() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        
        Assert.assertFalse(\"High pass should be disabled by default\", profile.highPassEnabled);
        Assert.assertEquals(100, profile.highPassHz, 0.001);
        Assert.assertFalse(\"Low pass should be disabled by default\", profile.lowPassEnabled);
        Assert.assertEquals(8000, profile.lowPassHz, 0.001);
        Assert.assertFalse(\"Notch should be disabled by default\", profile.notchEnabled);
        Assert.assertEquals(50, profile.notchHz, 0.001);
        Assert.assertFalse(\"Band pass should be disabled by default\", profile.bandPassEnabled);
        Assert.assertEquals(300, profile.bandLowHz, 0.001);
        Assert.assertEquals(3400, profile.bandHighHz, 0.001);
        Assert.assertEquals(2, profile.order);
    }

    @Test
    public void testFilterEmptyInput() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        short[] input = new short[0];
        short[] output = AudioFilterEngine.filter(input, profile);
        
        Assert.assertEquals(0, output.length);
    }

    @Test
    public void testFilterNullInput() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        short[] output = AudioFilterEngine.filter(null, profile);
        
        Assert.assertEquals(0, output.length);
    }

    @Test
    public void testHighPassFilter() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        profile.highPassEnabled = true;
        profile.highPassHz = 100;
        profile.order = 1;
        
        // Create a signal with DC offset + AC component
        short[] input = new short[1000];
        for (int i = 0; i < input.length; i++) {
            // DC offset of 1000 + 1000Hz sine wave
            input[i] = (short) (1000 + 5000 * Math.sin(2 * Math.PI * 1000 * i / 44100.0));
        }
        
        short[] output = AudioFilterEngine.filter(input, profile);
        
        // High pass should remove DC offset
        double mean = 0;
        for (short s : output) mean += s;
        mean /= output.length;
        
        Assert.assertTrue(\"High pass should remove DC component\", Math.abs(mean) < 100);
    }

    @Test
    public void testLowPassFilter() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        profile.lowPassEnabled = true;
        profile.lowPassHz = 1000;
        profile.order = 1;
        
        // Create a signal with high frequency component
        short[] input = new short[1000];
        for (int i = 0; i < input.length; i++) {
            // 100Hz + 5000Hz components
            input[i] = (short) (5000 * Math.sin(2 * Math.PI * 100 * i / 44100.0) +
                               5000 * Math.sin(2 * Math.PI * 5000 * i / 44100.0));
        }
        
        short[] output = AudioFilterEngine.filter(input, profile);
        
        // Low pass should attenuate 5000Hz component
        double power = 0;
        for (short s : output) power += s * s;
        power /= output.length;
        
        // Compare with original
        double inputPower = 0;
        for (short s : input) inputPower += s * s;
        inputPower /= input.length;
        
        Assert.assertTrue(\"Low pass should reduce power\", power < inputPower * 0.8);
    }

    @Test
    public void testNotchFilter() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        profile.notchEnabled = true;
        profile.notchHz = 50; // 50Hz mains hum
        profile.order = 1;
        
        // Create 50Hz signal
        short[] input = new short[44100]; // 1 second
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (10000 * Math.sin(2 * Math.PI * 50 * i / 44100.0));
        }
        
        short[] output = AudioFilterEngine.filter(input, profile);
        
        // Notch should significantly reduce 50Hz
        double inputPower = 0;
        for (short s : input) inputPower += s * s;
        inputPower /= input.length;
        
        double outputPower = 0;
        for (short s : output) outputPower += s * s;
        outputPower /= output.length;
        
        Assert.assertTrue(\"Notch filter should attenuate 50Hz\", outputPower < inputPower * 0.1);
    }

    @Test
    public void testBandPassFilter() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        profile.bandPassEnabled = true;
        profile.bandLowHz = 300;
        profile.bandHighHz = 3400;
        profile.order = 1;
        
        // Create signal with in-band and out-of-band components
        short[] input = new short[44100];
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (5000 * Math.sin(2 * Math.PI * 1000 * i / 44100.0) +  // in-band
                               5000 * Math.sin(2 * Math.PI * 100 * i / 44100.0) +   // below band
                               5000 * Math.sin(2 * Math.PI * 5000 * i / 44100.0));  // above band
        }
        
        short[] output = AudioFilterEngine.filter(input, profile);
        
        // Band pass should keep in-band, attenuate others
        double inputPower = 0;
        for (short s : input) inputPower += s * s;
        inputPower /= input.length;
        
        double outputPower = 0;
        for (short s : output) outputPower += s * s;
        outputPower /= output.length;
        
        // Should pass about 1/3 of the power (only 1000Hz component)
        Assert.assertTrue(\"Band pass should pass in-band only\", 
            outputPower > inputPower * 0.2 && outputPower < inputPower * 0.5);
    }

    @Test
    public void testLevelDb() {
        // Silent signal
        short[] silent = new short[1000];
        Assert.assertEquals(-120.0, AudioFilterEngine.levelDb(silent), 1.0);
        
        // Full scale sine
        short[] fullScale = new short[1000];
        for (int i = 0; i < fullScale.length; i++) {
            fullScale[i] = (short) (32767 * Math.sin(2 * Math.PI * 1000 * i / 44100.0));
        }
        double db = AudioFilterEngine.levelDb(fullScale);
        Assert.assertTrue(\"Full scale should be near 0 dB\", db > -3.0 && db < 0.5);
        
        // Half scale
        short[] halfScale = new short[1000];
        for (int i = 0; i < halfScale.length; i++) {
            halfScale[i] = (short) (16384 * Math.sin(2 * Math.PI * 1000 * i / 44100.0));
        }
        double dbHalf = AudioFilterEngine.levelDb(halfScale);
        Assert.assertTrue(\"Half scale should be ~-6dB\", dbHalf > -9 && dbHalf < -3);
    }

    @Test
    public void testFilterOrder() {
        AudioFilterEngine.FilterProfile profile = new AudioFilterEngine.FilterProfile();
        profile.highPassEnabled = true;
        profile.highPassHz = 100;
        
        // Test different orders
        short[] input = new short[1000];
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (1000 + 5000 * Math.sin(2 * Math.PI * 50 * i / 44100.0));
        }
        
        for (int order = 1; order <= 6; order++) {
            profile.order = order;
            short[] output = AudioFilterEngine.filter(input, profile);
            Assert.assertEquals(input.length, output.length);
        }
    }

    @Test
    public void testGenerateSpectrogram() {
        short[] input = new short[8192];
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (10000 * Math.sin(2 * Math.PI * 1000 * i / 44100.0));
        }
        
        double[][] spec = AudioFilterEngine.generateSpectrogram(input, 1024, 256);
        
        Assert.assertNotNull(spec);
        Assert.assertEquals(512, spec.length); // fftSize / 2
        Assert.assertTrue(spec[0].length > 0);
        
        // Check that 1000Hz bin has energy
        int bin1000 = (int) (1000 * 1024 / 44100);
        Assert.assertTrue(\"1000Hz bin should have energy\", spec[bin1000][0] > -60);
    }
}
