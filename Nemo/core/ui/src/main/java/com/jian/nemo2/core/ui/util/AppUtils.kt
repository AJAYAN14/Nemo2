package com.jian.nemo2.core.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppUtils {
    fun getVersionName(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                ).versionName ?: "Unknown"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
