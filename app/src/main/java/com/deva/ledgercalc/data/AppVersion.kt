package com.deva.ledgercalc.data

import android.content.Context
import android.content.pm.PackageManager

/**
 * Version details for the About page, read back from the installed package.
 *
 * They come from the platform rather than a generated constant so the number shown is the one the
 * device is actually running, and so the build needs no generated Java source to exist.
 */
data class AppVersion(val name: String, val code: Long) {

    fun label(): String = "v$name"

    companion object {
        fun read(context: Context): AppVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppVersion(name = info.versionName ?: "—", code = code)
        } catch (_: PackageManager.NameNotFoundException) {
            AppVersion(name = "—", code = 0L)
        }
    }
}
