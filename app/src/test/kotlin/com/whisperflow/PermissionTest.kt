package com.whisperflow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whisperflow.audio.PermissionHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionTest {

    @Test
    fun `audio permission returns boolean without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hasPermission = PermissionHelper.hasAudioPermission(context)
        // In clean test environment, RECORD_AUDIO is initially false unless granted
        assertFalse(hasPermission)
    }

    @Test
    fun `create permission intent points to application details settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = PermissionHelper.createPermissionIntent(context)
        assertNotNull(intent)
        assertNotNull(intent.data)
        assert(intent.data.toString().contains(context.packageName))
    }
}
