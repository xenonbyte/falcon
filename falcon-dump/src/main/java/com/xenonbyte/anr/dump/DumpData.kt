package com.xenonbyte.anr.dump

import org.json.JSONObject

/**
 * 转储数据接口
 *
 * @author xubo
 */
interface DumpData {

    /**
     * 转储数据转换为Json对象
     *
     * @return [JSONObject]实例
     */
    fun toJson(): JSONObject
}