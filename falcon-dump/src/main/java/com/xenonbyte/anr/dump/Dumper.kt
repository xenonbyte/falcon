package com.xenonbyte.anr.dump

import android.app.Application
import org.json.JSONObject

/**
 * 数据转储器
 *
 * @param name 转储器名
 * @param T 返回转储数据类型，必须是DumpData子类
 * @author xubo
 */
abstract class Dumper<out T : DumpData>(val name: String) {

    /**
     * 采集转储数据
     *
     * @param app
     * @return 转储数据
     */
    abstract fun collectData(app: Application): T

    override fun equals(other: Any?): Boolean {
        //比较地址
        if (this === other) return true
        //比较类型
        if (other == null || other::class != this::class) return false
        //比较name
        return name == (other as Dumper<*>).name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString() = "Dumper(name='$name')"
}

//转储器名key
const val KEY_DUMPER_NAME = "name"

//转储器数据key
const val KEY_DUMPER_DATA = "data"

//转储器错误key
const val KEY_DUMPER_ERROR = "error"

/**
 * 采集转储数据并转换成Json数据
 *
 * @param app
 * @return 转储数据[JSONObject]实例
 */
fun Dumper<*>.collectDataToJson(app: Application): JSONObject {
    return JSONObject().apply {
        try {
            put(KEY_DUMPER_NAME, name)
            put(KEY_DUMPER_DATA, collectData(app))
        } catch (e: Exception) {
            JSONObject().apply {
                put(KEY_DUMPER_NAME, name)
                put(KEY_DUMPER_ERROR, "collect data failed: ${e.message}")
            }
        }
    }
}