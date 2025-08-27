package com.xenonbyte.anr.dump

import android.app.Application
import android.content.Context
import android.os.BatteryManager

/**
 * 电池数据转储器
 *
 * @author xubo
 */
class BatteryDumper : Dumper<BatteryData>("BatteryDumper") {
    override fun collectData(app: Application): BatteryData {
        val batteryManager = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var capacity = -1
        var chargingStatus = "Unknown"
        batteryManager?.apply {
            capacity = getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            chargingStatus = when (getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NotCharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                else -> "Unknown"
            }
        }
        return BatteryData(capacity, chargingStatus)
    }
}