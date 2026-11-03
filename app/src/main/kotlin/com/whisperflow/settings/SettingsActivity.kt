package com.whisperflow.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.MainActivity

/**
 * Legacy entry point kept for backwards compatibility; redirects immediately
 * to the unified Home screen in [MainActivity].
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}
