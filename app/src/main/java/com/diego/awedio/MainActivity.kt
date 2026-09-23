package com.diego.awedio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action || Intent.ACTION_SEND_MULTIPLE == action) {
            val audioUri = extractAudioUri(intent)

            if (audioUri != null) {
                Log.i(TAG, "Successfully extracted shared audio Uri: $audioUri")
                Toast.makeText(this, "Áudio recebido! Processando...", Toast.LENGTH_SHORT).show()
                viewModel.handleSharedAudioUri(audioUri)
            } else {
                Log.w(TAG, "Received share intent ($action) but no valid audio Uri was extracted.")
            }
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
        if (extraStreamUri != null) return extraStreamUri

        // 2. Try ClipData
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val itemUri = clipData.getItemAt(0).uri
            if (itemUri != null) return itemUri
        }

        // 3. Try intent.data
        if (intent.data != null) return intent.data

        // 4. Try EXTRA_STREAM (Multiple list fallback)
        val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (!multipleUris.isNullOrEmpty()) {
            return multipleUris[0]
        }

        return null
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
