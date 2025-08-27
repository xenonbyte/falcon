package com.xenonbyte.anr.dump.internal

import android.app.Application
import android.os.Build
import com.xenonbyte.anr.dump.Dumper

/**
 * 应用数据dumper
 *
 * @author xubo
 */
class AppDumper : Dumper<AppData>("AppDumper") {

    override fun collectData(app: Application): AppData {
        var versionName = ""
        var versionCode = 0L
        val packageName = app.packageName
        try {
            val packageInfo = app.packageManager.getPackageInfo(packageName, 0)
            packageInfo?.let {
                versionName = it.versionName ?: ""
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode
                } else {
                    it.versionCode.toLong()
                }
            }
        } catch (e: Exception) {

        }
        return AppData(versionName, versionCode, packageName)
    }
}