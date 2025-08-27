package com.xenonbyte.anr.dump

import org.json.JSONObject

/**
 * 设备数据
 *
 * @author xubo
 */
class DeviceData(
    val brand: String,
    val board: String,
    val hardware: String,
    val product: String,
    val manufacturer: String,
    val model: String,
    val display: String,
    val version: String,
    val totalMemory: Long,
    val freeMemory: Long,
    val cpuApi: String,
    val cpuCore: Int,
    val cpuMinFreq: String,
    val cpuMaxFreq: String,
    val cpuCurFreq: String,
) : DumpData {

    companion object {
        //制造商
        const val BRAND = "brand"

        //主板
        const val BOARD = "board"

        //硬件名
        const val HARDWARE = "hardware"

        //产品名
        const val PRODUCT = "product"

        //制造商
        const val MANUFACTURER = "manufacturer"

        //型号
        const val MODEL = "model"

        //系统名
        const val DISPLAY = "display"

        //系统版本
        const val VERSION = "version"

        //物理内存
        const val TOTAL_MEMORY = "total_memory"

        //可用内存
        const val FREE_MEMORY = "free_memory"

        //cpu架构
        const val CPU_API = "cpu_api"

        //cpu核心
        const val CPU_CORE = "cpu_core"

        //cpu最小频率
        const val CPU_MIN_FREQ = "cpu_min_freq"

        //cpu最大频率
        const val CPU_MAX_FREQ = "cpu_max_freq"

        //cpu当前频率
        const val CPU_CUR_FREQ = "cpu_cur_freq"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(BRAND, brand)
            put(BOARD, board)
            put(HARDWARE, hardware)
            put(PRODUCT, product)
            put(MANUFACTURER, manufacturer)
            put(MODEL, model)
            put(DISPLAY, display)
            put(VERSION, version)
            put(TOTAL_MEMORY, totalMemory)
            put(FREE_MEMORY, freeMemory)
            put(CPU_API, cpuApi)
            put(CPU_CORE, cpuCore)
            put(CPU_MIN_FREQ, cpuMinFreq)
            put(CPU_MAX_FREQ, cpuMaxFreq)
            put(CPU_CUR_FREQ, cpuCurFreq)
        }
    }
}