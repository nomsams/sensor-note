package org.havenapp.main.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public final class AudioFilterEngine {
    private static final int SAMPLE_RATE = 44100;

    public static class FilterProfile {
        public boolean highPassEnabled;
        public double highPassHz = 100;
        public boolean lowPassEnabled;
        public double lowPassHz = 8000;
        public boolean notchEnabled;
        public double notchHz = 50;
        public boolean bandPassEnabled;
        public double bandLowHz = 300;
        public double bandHighHz = 3400;
        public int order = 2;
    }

    private AudioFilterEngine() {
    }

    public static short[] filter(short[] input, FilterProfile profile) {
        if (input == null || input.length == 0) return new short[0];
        double[] samples = new double[input.length];
        for (int index = 0; index < input.length; index++) {
            samples[index] = input[index] / 32768.0;
        }

        for (int pass = 0; pass < Math.max(1, profile.order); pass++) {
            if (profile.highPassEnabled) onePole(samples, profile.highPassHz, true);
            if (profile.lowPassEnabled) onePole(samples, profile.lowPassHz, false);
            if (profile.bandPassEnabled) {
                onePole(samples, profile.bandHighHz, false);
                onePole(samples, profile.bandLowHz, true);
            }
            if (profile.notchEnabled) twoPoleNotch(samples, profile.notchHz);
        }

        short[] output = new short[samples.length];
        for (int index = 0; index < output.length; index++) {
            output[index] = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, samples[index] * 32767.0));
        }
        return output;
    }

    private static void onePole(double[] samples, double cutoffHz, boolean highPass) {
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        double dt = 1.0 / SAMPLE_RATE;
        double alpha = dt / (rc + dt);
        double previousInput = samples[0];
        double previousOutput = samples[0];
        for (int index = 0; index < samples.length; index++) {
            double input = samples[index];
            double lowPass = previousOutput + alpha * (input - previousOutput);
            previousOutput = lowPass;
            previousInput = input;
            samples[index] = highPass ? input - lowPass : lowPass;
        }
    }

    private static void twoPoleNotch(double[] samples, double centerHz) {
        double omega = 2.0 * Math.PI * centerHz / SAMPLE_RATE;
        double radius = 0.995;
        double a1 = -2.0 * radius * Math.cos(omega);
        double a2 = radius * radius;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int index = 0; index < samples.length; index++) {
            double x0 = samples[index];
            double y0 = x0 - a1 * y1 - a2 * y2 + 0.0005 * (x1 + x2);
            x2 = x1; x1 = x0; y2 = y1; y1 = y0;
            samples[index] = y0;
        }
    }

    public static short[] recordPreview(int seconds) throws Exception {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE * seconds * 2);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        short[] samples = new short[SAMPLE_RATE * seconds];
        if (recorder.getState() != AudioRecord.STATE_UNINITIALIZED) {
            recorder.startRecording();
            int read = 0;
            while (read < samples.length) {
                int count = recorder.read(samples, read, samples.length - read);
                if (count <= 0) break;
                read += count;
            }
            recorder.stop();
            recorder.release();
            return java.util.Arrays.copyOf(samples, read);
        }
        throw new IllegalStateException("AudioRecord unavailable");
    }

    public static void play(short[] samples) {
        if (samples == null || samples.length == 0) return;
        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuffer, samples.length * 2), AudioTrack.MODE_STATIC);
        track.write(samples, 0, samples.length);
        track.play();
    }

    public static double levelDb(short[] samples) {
        if (samples == null || samples.length == 0) return -120d;
        long sum = 0;
        for (short sample : samples) sum += (long) sample * sample;
        double rms = Math.sqrt(sum / (double) samples.length);
        return rms < 1 ? -120d : 20.0 * Math.log10(rms / 32768.0);
    }
}
