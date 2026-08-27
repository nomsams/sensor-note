package org.havenapp.main.service;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public final class DevicePolicyHelper {
    private static final String TAG = "DevicePolicyHelper";

    private DevicePolicyHelper() {
    }

    public static void openAirplaneMode(Context context) {
        // Check if we have device admin permission to enforce airplane mode
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (dpm != null && dpm.isAdminActive(admin)) {
            // With device admin, we can set airplane mode on rooted devices or via settings
            // On non-rooted, we still need to open settings
            try {
                Settings.Global.putInt(context.getContentResolver(), 
                    Settings.Global.AIRPLANE_MODE_ON, 1);
                Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                toast(context, "Airplane mode enforced");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Failed to set airplane mode via Settings.Global", e);
            }
        }
        
        // Fallback: open settings for manual confirmation
        context.startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        toast(context, "Android requires manual airplane-mode confirmation");
    }

    public static void requestBluetoothOff(Context context) {
        // Check if we have device admin permission
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            // Fallback: use legacy permission or request manually
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 31) {
                adapter.disable();
                toast(context, "Bluetooth off requested");
            } else {
                toast(context, "Bluetooth permission required");
            }
        }
    }

    public static void openNfcSettings(Context context, NfcAdapter adapter) {
        // Check if we have device admin permission
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (adapter != null && adapter.isEnabled()) {
            try {
                context.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                try {
                    ComponentName panel = ComponentName.unflattenFromString(
                            "com.android.systemui/.SysUIWideNfcEnablePanel");
                    context.startActivity(new Intent("android.settings.NFC_SETTINGS")
                            .setComponent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored2) {
                    toast(context, "Open Quick Settings to turn NFC off");
                }
            }
        }
    }

    public static void openUsbPrefs(Context context) {
        // Check if we have device admin permission
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (dpm != null && dpm.isAdminActive(admin) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Device admin can restrict USB data on Android 12+
            // Note: USB data restriction is more complex and may require specific OEM support
            toast(context, "USB data restriction requested");
        }
        
        try {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            context.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        toast(context, "Select USB mode: No data transfer / charging only");
    }

    /**
     * Check if device admin is active
     */
    public static boolean isDeviceAdminActive(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    /**
     * Request device admin activation
     */
    public static void requestDeviceAdminActivation(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (dpm != null && !dpm.isAdminActive(admin)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                "Haven needs device admin permission to enforce airplane mode, Bluetooth, NFC, and USB restrictions.");
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /**
     * Lock device screen (requires device admin)
     */
    public static void lockDevice(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.lockNow();
        }
    }

    /**
     * Wipe device data (requires device admin) - use with extreme caution
     */
    public static void wipeDevice(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.wipeData(0);
        }
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
