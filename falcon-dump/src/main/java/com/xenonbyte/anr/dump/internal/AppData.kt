package com.xenonbyte.anr.dump.internal

import com.xenonbyte.anr.dump.DumpData
import org.json.JSONObject

/**
 * 应用数据结构
 *
 * @param versionName 应用版本名
 * @param versionCode 应用版本code
 * @param packageName 应用包名
 * @author xubo
 */
class AppData(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
) : DumpData {
    companion object {
        const val VERSION_NAME = "version_name"
        const val VERSION_CODE = "version_code"
        const val PACKAGE_NAME = "package_name"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(VERSION_NAME, versionName)
            put(VERSION_CODE, versionCode)
            put(PACKAGE_NAME, packageName)
        }
    }
}