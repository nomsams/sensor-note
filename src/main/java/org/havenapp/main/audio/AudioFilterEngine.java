package org.havenapp.main.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.Arrays;

public final class AudioFilterEngine {
    private static final String TAG = \"AudioFilterEngine\";
    public static final int SAMPLE_RATE = 44100;

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

    /**
     * Apply filters to audio samples
     */
    public static short[] filter(short[] input, FilterProfile profile) {
        if (input == null || input.length == 0) return new short[0];
        double[] samples = new double[input.length];
        for (int index = 0; index < input.length; index++) {
            samples[index] = input[index] / 32768.0;
        }

        for (int pass = 0; pass < Math.max(1, profile.order); pass++) {
            if (profile.highPassEnabled) highPass(samples, profile.highPassHz);
            if (profile.lowPassEnabled) lowPass(samples, profile.lowPassHz);
            if (profile.bandPassEnabled) {
                highPass(samples, profile.bandLowHz);
                lowPass(samples, profile.bandHighHz);
            }
            if (profile.notchEnabled) notch(samples, profile.notchHz);
        }

        short[] output = new short[samples.length];
        for (int index = 0; index < output.length; index++) {
            double val = samples[index] * 32767.0;
            output[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, val));
        }
        return output;
    }

    /**
     * High-pass filter (one-pole)
     */
    private static void highPass(double[] samples, double cutoffHz) {
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        double dt = 1.0 / SAMPLE_RATE;
        double alpha = dt / (rc + dt);
        double previousOutput = samples[0];
        for (int index = 0; index < samples.length; index++) {
            double input = samples[index];
            double lowPass = previousOutput + alpha * (input - previousOutput);
            previousOutput = lowPass;
            samples[index] = input - lowPass;
        }
    }

    /**
     * Low-pass filter (one-pole)
     */
    private static void lowPass(double[] samples, double cutoffHz) {
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        double dt = 1.0 / SAMPLE_RATE;
        double alpha = dt / (rc + dt);
        double previousOutput = samples[0];
        for (int index = 0; index < samples.length; index++) {
            double input = samples[index];
            double output = previousOutput + alpha * (input - previousOutput);
            previousOutput = output;
            samples[index] = output;
        }
    }

    /**
     * Notch filter (two-pole)
     * H(z) = (1 - 2*cos(w0)*z^-1 + z^-2) / (1 - 2*r*cos(w0)*z^-1 + r^2*z^-2)
     */
    private static void notch(double[] samples, double centerHz) {
        double w0 = 2.0 * Math.PI * centerHz / SAMPLE_RATE;
        double radius = 0.995;  // Pole radius (close to 1 for narrow notch)
        
        double b0 = 1.0;
        double b1 = -2.0 * Math.cos(w0);
        double b2 = 1.0;
        double a1 = -2.0 * radius * Math.cos(w0);
        double a2 = radius * radius;
        
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int index = 0; index < samples.length; index++) {
            double x0 = samples[index];
            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x0; y2 = y1; y1 = y0;
            samples[index] = y0;
        }
    }

    /**
     * Record preview audio for specified seconds
     */
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
            return Arrays.copyOf(samples, read);
        }
        throw new IllegalStateException(\"AudioRecord unavailable\");
    }

    /**
     * Play audio samples
     */
    public static void play(short[] samples) {
        if (samples == null || samples.length == 0) return;
        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuffer, samples.length * 2), AudioTrack.MODE_STATIC);
        track.write(samples, 0, samples.length);
        track.play();
        
        // Wait for playback to complete
        try {
            Thread.sleep((long) (samples.length * 1000.0 / SAMPLE_RATE) + 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            track.release();
        }
    }

    /**
     * Calculate RMS level in dB
     */
    public static double levelDb(short[] samples) {
        if (samples == null || samples.length == 0) return -120d;
        long sum = 0;
        for (short sample : samples) sum += (long) sample * sample;
        double rms = Math.sqrt(sum / (double) samples.length);
        return rms < 1 ? -120d : 20.0 * Math.log10(rms / 32768.0);
    }

    /**
     * Generate spectrogram data for visualization
     * Returns 2D array: [frequency_bins][time_frames]
     */
    public static double[][] generateSpectrogram(short[] samples, int fftSize, int hopSize) {
        if (samples == null || samples.length == 0) return new double[0][0];
        
        int numFrames = (samples.length - fftSize) / hopSize + 1;
        if (numFrames <= 0) return new double[0][0];
        
        int numBins = fftSize / 2;
        double[][] spectrogram = new double[numBins][numFrames];
        
        // Window function (Hann)
        double[] window = new double[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (fftSize - 1)));
        }
        
        // Simple FFT implementation (Cooley-Tukey)
        for (int frame = 0; frame < numFrames; frame++) {
            int offset = frame * hopSize;
            double[] real = new double[fftSize];
            double[] imag = new double[fftSize];
            
            // Apply window
            for (int i = 0; i < fftSize && (offset + i) < samples.length; i++) {
                real[i] = (samples[offset + i] / 32768.0) * window[i];
            }
            
            // FFT
            fft(real, imag);
            
            // Compute magnitude spectrum
            for (int bin = 0; bin < numBins; bin++) {
                double magnitude = Math.sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]);
                spectrogram[bin][frame] = 20.0 * Math.log10(magnitude + 1e-10);
            }
        }
        
        return spectrogram;
    }

    /**
     * Simple iterative FFT (Cooley-Tukey)
     */
    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        // Bit-reversal permutation
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; j >= bit; bit >>= 1) j -= bit;
            j += bit;
            if (i < j) {
                double temp = real[i]; real[i] = real[j]; real[j] = temp;
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp;
            }
        }
        
        // Cooley-Tukey
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wlenCos = Math.cos(ang);
            double wlenSin = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wCos = 1, wSin = 0;
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j;
                    int v = i + j + len / 2;
                    double uReal = real[u];
                    double uImag = imag[u];
                    double vReal = real[v] * wCos - imag[v] * wSin;
                    double vImag = real[v] * wSin + imag[v] * wCos;
                    real[u] = uReal + vReal;
                    imag[u] = uImag + vImag;
                    real[v] = uReal - vReal;
                    imag[v] = uImag - vImag;
                    double nextWcos = wCos * wlenCos - wSin * wlenSin;
                    double nextWsin = wCos * wlenSin + wSin * wlenCos;
                    wCos = nextWcos;
                    wSin = nextWsin;
                }
            }
        }
    }
}
