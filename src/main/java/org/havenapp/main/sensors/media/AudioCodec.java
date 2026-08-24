/*
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */

package org.havenapp.main.sensors.media;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

public class AudioCodec {

    private static final int SAMPLE_BUFFER_SIZE = 8192;

    private AudioRecord recorder = null;
	private int minSize;

	/**
	 * Configures the recorder and starts it
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public void start() throws IllegalStateException, IOException {
		if (recorder == null) {
			minSize = AudioRecord.getMinBufferSize(
					44100,
					AudioFormat.CHANNEL_IN_DEFAULT,
					AudioFormat.ENCODING_PCM_16BIT);
            Log.d("AudioCodec", "Minimum buffer size is "+ minSize);
			recorder = new AudioRecord(
					MediaRecorder.AudioSource.MIC,
					44100,
					AudioFormat.CHANNEL_IN_DEFAULT,
					AudioFormat.ENCODING_PCM_16BIT,
			Math.max(SAMPLE_BUFFER_SIZE, minSize));

			recorder.startRecording();
		}
	}
	
	/**
	 * Returns current sound level
	 * @return sound level
	 */
    public short[] getAmplitude() {
    	if (recorder != null) {
            short[] buffer = new short[SAMPLE_BUFFER_SIZE];
            int readBytes = 0;
            while (readBytes < SAMPLE_BUFFER_SIZE) {
                readBytes += recorder.read(buffer, readBytes, SAMPLE_BUFFER_SIZE - readBytes);
            }

            short[] copyToReturn = Arrays.copyOf(buffer, SAMPLE_BUFFER_SIZE);

            Log.d("AudioCodec", "Recorder has read: "+ readBytes + "bytes");


    		return copyToReturn;
    	}
    	return null;
    }
      
    
    public void stop() {
        if (recorder != null
            && recorder.getState() != AudioRecord.STATE_UNINITIALIZED) {
        	recorder.stop();
        	recorder.release();
        	Log.i("AudioCodec", "Sampling stopped");
        }
        Log.i("AudioCodec", "Recorder set to null");
        recorder = null;
    }
}
