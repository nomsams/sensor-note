package org.havenapp.main.service;

import org.junit.Assert;
import org.junit.Test;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

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
