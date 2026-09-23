package com.diego.awedio

import android.app.Application
import android.os.Build
import com.diego.awedio.util.AppLogger

class AwedioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.installCrashHandler()

        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }

        AppLogger.i(
            TAG,
            "Awedio started. Version: $versionName | Device: ${Build.MANUFACTURER} ${Build.MODEL} | " +
                "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
    }

    companion object {
        private const val TAG = "AwedioApp"
    }
}
