package org.havenapp.main.service;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.Facing;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SecureCaptureService extends Service {
    private File evidenceDir;
    private final Handler handler = new Handler();
    private CameraView cameraView;
    private static final long VIDEO_MS = 5_000L;

    public static void startEvidenceCapture(Context context) {
        context.startForegroundService(new Intent(context, SecureCaptureService.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2006, ArmEvidenceNotification.build(this));
        PreferenceManager preferences = new PreferenceManager(this);
        evidenceDir = new File(
                new File(getExternalFilesDir(null), preferences.getDefaultMediaStoragePath()),
                ".secure-evidence");
        if (!evidenceDir.exists()) evidenceDir.mkdirs();
        prepareCamera();

        if (isCaptureEnabled()) {
            handler.post(this::capturePhoto);
            handler.postDelayed(this::captureVideo, 1_000L);
            handler.postDelayed(this::finishEvidence, 15_500L);
        } else {
            finishEvidence();
        }
        return START_NOT_STICKY;
    }

    private void capturePhoto() {
        if (!isCaptureEnabled()) return;
        final String name = stamp(".evidence.jpg");
        cameraView.takePictureSnapshot();
        handler.postDelayed(() -> EvidenceChain.append(evidenceDir, name), 2_000L);
    }

    private void captureVideo() {
        if (!isCaptureEnabled()) return;
        String name = stamp(".evidence.mp4");
        cameraView.takeVideoSnapshot(new File(evidenceDir, name));
        handler.postDelayed(() -> {
            cameraView.stopVideo();
            EvidenceChain.append(evidenceDir, name);
        }, VIDEO_MS);
    }

    private boolean isCaptureEnabled() {
        return cameraView != null && cameraView.isOpened();
    }

    private void finishEvidence() {
        if (cameraView != null) {
            try {
                cameraView.stopVideo();
                cameraView.close();
                ((ViewGroup) cameraView.getParent()).removeView(cameraView);
            } catch (Exception ignored) {
            }
            cameraView = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void prepareCamera() {
        CountDownLatch opened = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                cameraView = new CameraView(this);
                cameraView.setAudio(Audio.OFF);
                cameraView.setFacing(Facing.FRONT);
                cameraView.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
                addContentView(cameraView, cameraView.getLayoutParams());
                cameraView.open();
            } finally {
                opened.countDown();
            }
        });
        try {
            opened.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    private String stamp(String suffix) {
        return new SimpleDateFormat(Utils.DATE_TIME_PATTERN, Locale.getDefault())
                .format(new Date()) + suffix;
    }

}
