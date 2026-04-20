package com.xenonbyte.anr.dump

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DeviceDumperTest {

    @Test
    fun `cpu0 不可读时应该回退到其他 cpu 目录`() {
        val cpuRoot = createTempDirectory("device-dumper").toFile()
        File(cpuRoot, "cpu4/cpufreq").mkdirs()
        File(cpuRoot, "cpu4/cpufreq/cpuinfo_min_freq").toPath().writeText("300000")
        File(cpuRoot, "cpu4/cpufreq/cpuinfo_max_freq").toPath().writeText("2100000")
        File(cpuRoot, "cpu4/cpufreq/scaling_cur_freq").toPath().writeText("1500000")

        val app = mockk<Application>()
        every { app.getSystemService(any<String>()) } returns null

        val dumper = DeviceDumper.createForTest(cpuRoot) { file -> file.readText().trim() }

        val result = dumper.collectData(app)

        assertThat(result.cpuMinFreq).isEqualTo("300000")
        assertThat(result.cpuMaxFreq).isEqualTo("2100000")
        assertThat(result.cpuCurFreq).isEqualTo("1500000")
    }

    @Test
    fun `没有可读 cpu 频率文件时应该返回空字符串`() {
        val cpuRoot = createTempDirectory("device-dumper-empty").toFile()
        File(cpuRoot, "cpu0").mkdirs()

        val app = mockk<Application>()
        every { app.getSystemService(any<String>()) } returns null

        val dumper = DeviceDumper.createForTest(cpuRoot) { file -> file.readText().trim() }

        val result = dumper.collectData(app)

        assertThat(result.cpuMinFreq).isEmpty()
        assertThat(result.cpuMaxFreq).isEmpty()
        assertThat(result.cpuCurFreq).isEmpty()
    }
}
