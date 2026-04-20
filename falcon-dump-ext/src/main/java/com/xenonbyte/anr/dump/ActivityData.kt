package com.xenonbyte.anr.dump

import org.json.JSONObject

/**
 * Activity数据
 *
 * @author xubo
 */
class ActivityData(
    val activityStack: String,
    val appForeground: Boolean
) : DumpData {
    companion object {
        // activity 堆栈
        const val ACTIVITY_STACK = "activity_stack"

        // app 是否前台
        const val APP_FOREGROUND = "app_foreground"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(ACTIVITY_STACK, activityStack)
            put(APP_FOREGROUND, appForeground)
        }
    }
}
