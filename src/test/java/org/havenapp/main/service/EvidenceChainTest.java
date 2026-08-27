package org.havenapp.main.service;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


import org.junit.Assert;
import org.junit.Test;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class EvidenceChainTest {

    @Test
    public void testEvidenceChainAppend() {
        Context context = ApplicationProvider.getApplicationContext();
        File evidenceDir = new File(context.getFilesDir(), "test_evidence");
        evidenceDir.mkdirs();
        
        String testFile = "test_evidence.jpg";
        
        // Should not throw
        EvidenceChain.append(evidenceDir, testFile);
        
        // Clean up
        evidenceDir.delete();
    }

    @Test
    public void testEvidenceChainWithNonExistentDir() {
        Context context = ApplicationProvider.getApplicationContext();
        File evidenceDir = new File(context.getFilesDir(), "nonexistent_evidence");
        
        String testFile = "test_evidence.jpg";
        
        // Should not throw even if directory doesn't exist
        EvidenceChain.append(evidenceDir, testFile);
    }
}
