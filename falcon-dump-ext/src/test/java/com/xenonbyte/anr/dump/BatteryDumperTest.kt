package com.xenonbyte.anr.dump

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BatteryDumperTest {

    private val app = Application()

    @Test
    fun `优先使用 BatteryManager 属性值`() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 83
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) } returns
            BatteryManager.BATTERY_STATUS_CHARGING

        val dumper = BatteryDumper.createForTest(
            batteryManagerProvider = { batteryManager },
            batteryIntentProvider = {
                Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_LEVEL, 1)
                    .putExtra(BatteryManager.EXTRA_SCALE, 10)
                    .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
            }
        )

        val result = dumper.collectData(app)

        assertThat(result.capacity).isEqualTo(83)
        assertThat(result.chargingStatus).isEqualTo("Charging")
    }

    @Test
    fun `属性值不支持时回退到 sticky battery intent`() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns Int.MIN_VALUE
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) } returns Int.MIN_VALUE

        val dumper = BatteryDumper.createForTest(
            batteryManagerProvider = { batteryManager },
            batteryIntentProvider = {
                Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_LEVEL, 45)
                    .putExtra(BatteryManager.EXTRA_SCALE, 50)
                    .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
            }
        )

        val result = dumper.collectData(app)

        assertThat(result.capacity).isEqualTo(90)
        assertThat(result.chargingStatus).isEqualTo("Full")
    }

    @Test
    fun `旧行为返回零状态码时应该继续回退到 intent`() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 60
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) } returns 0

        val dumper = BatteryDumper.createForTest(
            batteryManagerProvider = { batteryManager },
            batteryIntentProvider = {
                Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_NOT_CHARGING)
            }
        )

        val result = dumper.collectData(app)

        assertThat(result.capacity).isEqualTo(60)
        assertThat(result.chargingStatus).isEqualTo("NotCharging")
    }

    @Test
    fun `属性状态返回 UNKNOWN 时应该继续回退到 intent`() {
        val batteryManager = mockk<BatteryManager>()
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 60
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) } returns
            BatteryManager.BATTERY_STATUS_UNKNOWN

        val dumper = BatteryDumper.createForTest(
            batteryManagerProvider = { batteryManager },
            batteryIntentProvider = {
                Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            }
        )

        val result = dumper.collectData(app)

        assertThat(result.capacity).isEqualTo(60)
        assertThat(result.chargingStatus).isEqualTo("Discharging")
    }

    @Test
    fun `所有来源都无效时返回兜底值`() {
        val dumper = BatteryDumper.createForTest(
            batteryManagerProvider = { null },
            batteryIntentProvider = {
                Intent(Intent.ACTION_BATTERY_CHANGED)
                    .putExtra(BatteryManager.EXTRA_LEVEL, -1)
                    .putExtra(BatteryManager.EXTRA_SCALE, 0)
            }
        )

        val result = dumper.collectData(app)

        assertThat(result.capacity).isEqualTo(-1)
        assertThat(result.chargingStatus).isEqualTo("Unknown")
    }
}
