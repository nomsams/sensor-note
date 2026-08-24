package org.havenapp.main.service;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public final class DevicePolicyHelper {
    private static final String AIRPLANE_SETTINGS = Settings.ACTION_AIRPLANE_MODE_SETTINGS;

    private DevicePolicyHelper() {
    }

    public static void openAirplaneMode(Context context) {
        context.startActivity(new Intent(AIRPLANE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        toast(context, "Android requires manual airplane-mode confirmation");
    }

    public static void requestBluetoothOff(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 31) {
                adapter.disable();
                toast(context, "Bluetooth off requested");
            } else {
                toast(context, "Bluetooth permission required");
            }
        }
    }

    public static void openNfcSettings(Context context, NfcAdapter adapter) {
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
        try {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            context.startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        toast(context, "Select USB mode: No data transfer / charging only");
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
