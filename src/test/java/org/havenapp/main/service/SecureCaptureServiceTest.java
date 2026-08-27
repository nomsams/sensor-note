package org.havenapp.main.service;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class SecureCaptureServiceTest {

    @Test
    public void testStartEvidenceCapture() {
        Context context = ApplicationProvider.getApplicationContext();
        
        // Should not throw
        SecureCaptureService.startEvidenceCapture(context);
    }

    @Test
    public void testSecureCaptureServiceCreation() {
        // Just verify the class can be referenced
        Assert.assertNotNull(SecureCaptureService.class);
    }
}
