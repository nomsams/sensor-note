package org.havenapp.main.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;

public class SecureCaptureService extends Service implements LifecycleOwner {
    private static final String TAG = "SecureCaptureService";
    private static final int NOTIFICATION_ID = 2006;
    private static final long CAPTURE_DURATION_MS = 15000L;
    
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Camera camera;
    private File evidenceDir;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private Handler mainHandler;
    private boolean isCapturing = false;
    private String photoPath;
    private String videoPath;
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    
    public static void startEvidenceCapture(Context context) {
        Intent intent = new Intent(context, SecureCaptureService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("SecureCaptureThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        
        PreferenceManager preferences = new PreferenceManager(this);
        evidenceDir = new File(
                new File(getExternalFilesDir(null), preferences.getDefaultMediaStoragePath()),
                ".secure-evidence");
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, ArmEvidenceNotification.build(this));
        
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        
        // Initialize CameraX asynchronously
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                startCapture();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "CameraX initialization failed", e);
                stopSelf();
            }
        }, ContextCompat.getMainExecutor(this));
        
        return START_STICKY;
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(android.view.Surface.ROTATION_0)
                .build();

        Recorder recorder = new Recorder.Builder()
                .setTargetVideoEncodingBitRate(2_000_000)
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    this, 
                    cameraSelector, 
                    preview, 
                    imageCapture, 
                    videoCapture);
        } catch (Exception e) {
            Log.e(TAG, "CameraX bindToLifecycle failed", e);
        }
    }

    private void startCapture() {
        if (isCapturing || imageCapture == null || videoCapture == null) {
            return;
        }
        isCapturing = true;
        
        // Capture photo
        String photoName = stamp(".evidence.jpg");
        photoPath = new File(evidenceDir, photoName).getAbsolutePath();
        EvidenceChain.append(evidenceDir, photoName);
        
        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(new File(photoPath)).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Photo saved: " + photoPath);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed", exception);
                    }
                });

        // Capture video
        String videoName = stamp(".evidence.mp4");
        videoPath = new File(evidenceDir, videoName).getAbsolutePath();
        EvidenceChain.append(evidenceDir, videoName);
        
        recording = videoCapture.getOutput().prepareRecording(
                this,
                new FileOutputOptions.Builder(new File(videoPath)).build())
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
                        if (!finalize.hasError()) {
                            Log.i(TAG, "Video saved: " + videoPath);
                        } else {
                            Log.e(TAG, "Video capture failed: " + finalize.getError());
                        }
                    }
                });

        /*
        videoCapture.startRecording(
                new VideoCapture.OutputFileOptions.Builder(new File(videoPath)).build(),
                ContextCompat.getMainExecutor(this),
                new VideoCapture.OnVideoSavedCallback() {
                    @Override
                    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Video saved: " + videoPath);
                    }
                    @Override
                    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        Log.e(TAG, "Video capture failed: " + message, cause);
                    }
                });
        */

        // Stop capture after duration
        mainHandler.postDelayed(() -> {
            stopCapture();
            finishEvidence();
        }, CAPTURE_DURATION_MS);
    }

    private void stopCapture() {
        if (videoCapture != null) {
            if (recording != null) {
                recording.stop();
            }
        }
        isCapturing = false;
    }

    private void finishEvidence() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        
        // Give some time for files to be finalized
        mainHandler.postDelayed(() -> {
            stopForeground(true);
            stopSelf();
        }, 1000);
    }

    private String stamp(String suffix) {
        return new SimpleDateFormat(Utils.DATE_TIME_PATTERN, Locale.getDefault())
                .format(new Date()) + suffix;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @NonNull
    @Override
    public LifecycleRegistry getLifecycle() {
        return lifecycle;
    }
}
