package org.havenapp.main.service;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = \"DeviceAdminReceiver\";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, \"Device admin enabled\");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, \"Device admin disabled\");
    }

    @Override
    public void onDisableRequested(Context context, Intent intent) {
        super.onDisableRequested(context, intent);
        Log.i(TAG, \"Device admin disable requested\");
    }
}
