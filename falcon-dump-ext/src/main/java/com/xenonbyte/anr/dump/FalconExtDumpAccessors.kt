package com.xenonbyte.anr.dump

import org.json.JSONObject

private const val ACTIVITY_DUMPER_NAME = "ActivityDumper"
private const val BATTERY_DUMPER_NAME = "BatteryDumper"
private const val DEVICE_DUMPER_NAME = "DeviceDumper"
private const val FD_DUMPER_NAME = "FdDumper"

fun FalconDumpPayload.activityData(): ActivityData? = decode(ACTIVITY_DUMPER_NAME, ::parseActivityData)

fun FalconDumpPayload.requireActivityData(): ActivityData = requireDecoded(ACTIVITY_DUMPER_NAME, ::parseActivityData)

fun FalconDumpPayload.batteryData(): BatteryData? = decode(BATTERY_DUMPER_NAME, ::parseBatteryData)

fun FalconDumpPayload.requireBatteryData(): BatteryData = requireDecoded(BATTERY_DUMPER_NAME, ::parseBatteryData)

fun FalconDumpPayload.deviceData(): DeviceData? = decode(DEVICE_DUMPER_NAME, ::parseDeviceData)

fun FalconDumpPayload.requireDeviceData(): DeviceData = requireDecoded(DEVICE_DUMPER_NAME, ::parseDeviceData)

fun FalconDumpPayload.fdData(): FdData? = decode(FD_DUMPER_NAME, ::parseFdData)

fun FalconDumpPayload.requireFdData(): FdData = requireDecoded(FD_DUMPER_NAME, ::parseFdData)

private fun parseActivityData(json: JSONObject): ActivityData {
    return ActivityData(
        activityStack = json.optString(ActivityData.ACTIVITY_STACK),
        appForeground = json.optBoolean(ActivityData.APP_FOREGROUND)
    )
}

private fun parseBatteryData(json: JSONObject): BatteryData {
    return BatteryData(
        capacity = json.optInt(BatteryData.CAPACITY, -1),
        chargingStatus = json.optString(BatteryData.CHARGING_STATUS)
    )
}

private fun parseDeviceData(json: JSONObject): DeviceData {
    return DeviceData(
        brand = json.optString(DeviceData.BRAND),
        board = json.optString(DeviceData.BOARD),
        hardware = json.optString(DeviceData.HARDWARE),
        product = json.optString(DeviceData.PRODUCT),
        manufacturer = json.optString(DeviceData.MANUFACTURER),
        model = json.optString(DeviceData.MODEL),
        display = json.optString(DeviceData.DISPLAY),
        version = json.optString(DeviceData.VERSION),
        totalMemory = json.optLong(DeviceData.TOTAL_MEMORY),
        freeMemory = json.optLong(DeviceData.FREE_MEMORY),
        cpuApi = json.optString(DeviceData.CPU_API),
        cpuCore = json.optInt(DeviceData.CPU_CORE),
        cpuMinFreq = json.optString(DeviceData.CPU_MIN_FREQ),
        cpuMaxFreq = json.optString(DeviceData.CPU_MAX_FREQ),
        cpuCurFreq = json.optString(DeviceData.CPU_CUR_FREQ)
    )
}

private fun parseFdData(json: JSONObject): FdData {
    return FdData(
        openFd = json.optInt(FdData.OPEN_FD)
    )
}
