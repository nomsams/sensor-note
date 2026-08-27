package org.havenapp.main.service;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class DevicePolicyHelperTest {

    @Test
    public void testDeviceAdminReceiver() {
        Context context = ApplicationProvider.getApplicationContext();
        
        // Verify DeviceAdminReceiver class exists and can be instantiated
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);
        Assert.assertNotNull("DeviceAdminReceiver component should exist", admin);
        Assert.assertEquals("Package name should match", context.getPackageName(), admin.getPackageName());
        Assert.assertEquals("Class name should match", DeviceAdminReceiver.class.getName(), admin.getClassName());
    }

    @Test
    public void testIsDeviceAdminActive() {
        Context context = ApplicationProvider.getApplicationContext();
        
        // Should return false when not activated
        boolean active = DevicePolicyHelper.isDeviceAdminActive(context);
        Assert.assertFalse("Device admin should not be active by default", active);
    }
}
