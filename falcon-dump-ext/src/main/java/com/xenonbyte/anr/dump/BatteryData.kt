package com.xenonbyte.anr.dump

import org.json.JSONObject

/**
 * 电池数据
 *
 * @author xubo
 */
class BatteryData(
    val capacity: Int,
    val chargingStatus: String
) : DumpData {
    companion object {
        //电池电量
        const val CAPACITY = "capacity"

        //充电状态
        const val CHARGING_STATUS = "charging_status"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(CAPACITY, capacity)
            put(CHARGING_STATUS, chargingStatus)
        }
    }
}