package com.diego.awedio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.diego.awedio.ui.MainScreen
import com.diego.awedio.ui.MainViewModel
import com.diego.awedio.ui.theme.AwedioTheme
import com.diego.awedio.util.AppLogger

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppLogger.i(TAG, "MainActivity created. Intent action: ${intent?.action}, type: ${intent?.type}")
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
        AppLogger.i(TAG, "onNewIntent called. Intent action: ${intent.action}, type: ${intent.type}")
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) {
            AppLogger.w(TAG, "handleIncomingIntent called with null intent")
            return
        }

        val action = intent.action
        val type = intent.type

        AppLogger.i(TAG, "Processing incoming intent: action=$action, type=$type")

        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action || Intent.ACTION_SEND_MULTIPLE == action) {
            val audioUris = extractAudioUris(intent)

            if (audioUris.isNotEmpty()) {
                AppLogger.i(TAG, "Successfully extracted ${audioUris.size} audio Uri(s)")
                if (audioUris.size == 1) {
                    Toast.makeText(this, getString(R.string.msg_audio_received), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.msg_audio_received_multiple, audioUris.size), Toast.LENGTH_SHORT).show()
                }
                viewModel.handleSharedAudioUris(audioUris)
            } else {
                AppLogger.e(TAG, "Received share intent ($action) but no valid audio Uri was extracted.")
            }
        } else {
            AppLogger.i(TAG, "Intent action ($action) is not a share action.")
        }
    }

    private fun extractAudioUris(intent: Intent): List<Uri> {
        // 1. Try EXTRA_STREAM as ArrayList (normal ACTION_SEND_MULTIPLE path)
        val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (!multipleUris.isNullOrEmpty()) {
            AppLogger.i(TAG, "Extracted ${multipleUris.size} Uri(s) from EXTRA_STREAM ArrayList")
            return multipleUris.toList()
        }

        // 2. Try EXTRA_STREAM (Single)
        val extraStreamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (extraStreamUri != null) {
            AppLogger.i(TAG, "Extracted Uri from EXTRA_STREAM: $extraStreamUri")
            return listOf(extraStreamUri)
        }

        // 3. Try ClipData (all items)
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val uris = (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
            if (uris.isNotEmpty()) {
                AppLogger.i(TAG, "Extracted ${uris.size} Uri(s) from ClipData")
                return uris
            }
        }

        // 4. Try intent.data
        val dataUri = intent.data
        if (dataUri != null) {
            AppLogger.i(TAG, "Extracted Uri from intent.data: $dataUri")
            return listOf(dataUri)
        }

        return emptyList()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
