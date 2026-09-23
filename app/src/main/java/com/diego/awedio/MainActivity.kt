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
            val audioUri = extractAudioUri(intent)

            if (audioUri != null) {
                AppLogger.i(TAG, "Successfully extracted audio Uri: $audioUri")
                Toast.makeText(this, "Áudio recebido! Processando...", Toast.LENGTH_SHORT).show()
                viewModel.handleSharedAudioUri(audioUri)
            } else {
                AppLogger.e(TAG, "Received share intent ($action) but no valid audio Uri was extracted.")
            }
        } else {
            AppLogger.i(TAG, "Intent action ($action) is not a share action.")
        }
    }

    private fun extractAudioUri(intent: Intent): Uri? {
        // 1. Try EXTRA_STREAM (Single)
        val extraStreamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (extraStreamUri != null) {
            AppLogger.i(TAG, "Extracted Uri from EXTRA_STREAM: $extraStreamUri")
            return extraStreamUri
        }

        // 2. Try ClipData
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val itemUri = clipData.getItemAt(0).uri
            if (itemUri != null) {
                AppLogger.i(TAG, "Extracted Uri from ClipData: $itemUri")
                return itemUri
            }
        }

        // 3. Try intent.data
        if (intent.data != null) {
            AppLogger.i(TAG, "Extracted Uri from intent.data: ${intent.data}")
            return intent.data
        }

        // 4. Try EXTRA_STREAM (Multiple list fallback)
        val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (!multipleUris.isNullOrEmpty()) {
            AppLogger.i(TAG, "Extracted Uri from EXTRA_STREAM ArrayList: ${multipleUris[0]}")
            return multipleUris[0]
        }

        return null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
