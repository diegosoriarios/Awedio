package com.diego.awedio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.diego.awedio.ui.MainScreen
import com.diego.awedio.ui.MainViewModel
import com.diego.awedio.ui.theme.AwedioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            AwedioTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val type = intent.type

        Log.i(TAG, "Incoming intent action: $action, type: $type")

        if (Intent.ACTION_SEND == action && type != null && type.startsWith("audio/")) {
            val audioUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }

            audioUri?.let { uri ->
                Log.i(TAG, "Received shared audio Uri: $uri")
                viewModel.handleSharedAudioUri(uri)
            } ?: Log.e(TAG, "Shared intent audio Uri was null")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
