package com.xenonbyte.anr.dump

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * 电池数据转储器
 *
 * @author xubo
 */
class BatteryDumper private constructor(
    private val batteryManagerProvider: ((Application) -> BatteryManager?)? = null,
    private val batteryIntentProvider: ((Application) -> Intent?)? = null
) : Dumper<BatteryData>("BatteryDumper") {
    companion object {
        private val KNOWN_BATTERY_STATUSES = setOf(
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL
        )

        internal fun createForTest(
            batteryManagerProvider: (Application) -> BatteryManager?,
            batteryIntentProvider: (Application) -> Intent?
        ): BatteryDumper {
            return BatteryDumper(
                batteryManagerProvider = batteryManagerProvider,
                batteryIntentProvider = batteryIntentProvider
            )
        }
    }

    constructor() : this(null, null)

    override fun collectData(app: Application): BatteryData {
        val batteryManager = resolveBatteryManager(app)
        val batteryIntent = resolveBatteryIntent(app)
        return BatteryData(
            capacity = resolveCapacity(batteryManager, batteryIntent),
            chargingStatus = mapChargingStatus(resolveChargingStatus(batteryManager, batteryIntent))
        )
    }

    private fun resolveBatteryManager(app: Application): BatteryManager? {
        val provider = batteryManagerProvider
        return if (provider != null) {
            provider.invoke(app)
        } else {
            app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        }
    }

    private fun resolveBatteryIntent(app: Application): Intent? {
        val provider = batteryIntentProvider
        return if (provider != null) {
            provider.invoke(app)
        } else {
            app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
    }

    private fun resolveCapacity(batteryManager: BatteryManager?, batteryIntent: Intent?): Int {
        val propertyCapacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
        if (propertyCapacity in 0..100) {
            return propertyCapacity
        }

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) {
            return -1
        }

        return ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
    }

    private fun resolveChargingStatus(batteryManager: BatteryManager?, batteryIntent: Intent?): Int {
        val propertyStatus = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: Int.MIN_VALUE
        if (propertyStatus in KNOWN_BATTERY_STATUSES) {
            return propertyStatus
        }

        return batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    }

    private fun mapChargingStatus(status: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NotCharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Unknown"
        }
    }
}
