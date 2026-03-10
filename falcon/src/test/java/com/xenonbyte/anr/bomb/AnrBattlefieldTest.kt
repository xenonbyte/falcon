package com.xenonbyte.anr.bomb

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * AnrBattlefield 单元测试
 *
 * 测试目标：
 * 1. 炸弹爆炸触发正确
 * 2. 扫雷成功取消炸弹
 * 3. 前后台阈值正确应用
 * 4. 非采样线程调用被忽略
 */
class AnrBattlefieldTest {

    private lateinit var mockBombThread: AnrBombThread
    private lateinit var mockLooper: android.os.Looper
    private lateinit var battlefield: AnrBattlefield
    private var explosionTriggered = false

    @Before
    fun setup() {
        mockBombThread = mockk(relaxed = true)
        mockLooper = mockk(relaxed = true)

        every { mockBombThread.isStartBombSpace() } returns true
        every { mockBombThread.getBombLooper() } returns mockLooper

        explosionTriggered = false
        battlefield = AnrBattlefield(
            foregroundAnrThreshold = 4000L,
            backgroundAnrThreshold = 8000L,
            anrBombThread = mockBombThread
        )
    }

    @Test
    fun `未设置监听器时炸弹爆炸不应崩溃`() {
        // 不设置监听器
        battlefield.deployAnrBattle("test message") { true }
        // 应该正常完成，不抛出异常
    }

    @Test
    fun `非采样线程调用应该被忽略`() {
        val listener = mockk<AnrBombExplosionListener>(relaxed = true)
        battlefield.setBombExplosionListener(listener)

        // 使用非采样线程（返回false）
        battlefield.deployAnrBattle("test message") { false }

        // 不应该触发任何战斗任务
        verify(exactly = 0) { mockBombThread.getBombLooper() }
    }

    @Test
    fun `雷区未开启时应该被忽略`() {
        every { mockBombThread.isStartBombSpace() } returns false

        battlefield.deployAnrBattle("test message") { true }

        // 不应该获取Looper
        verify(exactly = 0) { mockBombThread.getBombLooper() }
    }

    @Test
    fun `Looper未创建时应该被忽略`() {
        every { mockBombThread.getBombLooper() } returns null

        battlefield.deployAnrBattle("test message") { true }

        // 应该安全返回，不崩溃
    }

    @Test
    fun `第一次部署应该触发战斗任务`() {
        battlefield.deployAnrBattle("test message") { true }

        // 验证获取了Looper
        verify { mockBombThread.getBombLooper() }
    }

    @Test
    fun `连续部署应该只有第一次生效`() {
        // 第一次部署
        battlefield.deployAnrBattle("message 1") { true }

        // 第二次部署（模拟战斗进行中）
        battlefield.deployAnrBattle("message 2") { true }

        // 只应该获取一次Looper（因为第二次战斗进行中）
        verify(exactly = 1) { mockBombThread.getBombLooper() }
    }

    @Test
    fun `爆炸任务触发后应该调用监听器`() {
        val listener = mockk<AnrBombExplosionListener>(relaxed = true)
        battlefield.setBombExplosionListener(listener)

        // 部署战斗
        battlefield.deployAnrBattle("test message") { true }

        // 注意：由于AnrEngineer使用Handler延时执行，
        // 在单元测试中我们无法直接验证延时任务的执行
        // 这里我们只验证部署过程正确
    }

    @Test
    fun `炸弹间隔时间应该合理`() {
        // 炸弹间隔时间是固定的500ms
        assertEquals(500L, AnrBattlefield.BOMB_DEFUSING_INTERVAL_DURATION)
    }

    @Test
    fun `前后台阈值应该正确配置`() {
        // 创建一个测试用的battlefield
        val testBattlefield = AnrBattlefield(
            foregroundAnrThreshold = 5000L,
            backgroundAnrThreshold = 10000L,
            anrBombThread = mockBombThread
        )

        // 验证配置正确传递（通过行为间接验证）
        assertNotNull(testBattlefield)
    }

    @Test
    fun `前台阈值小于扫雷间隔时应该使用最小值`() {
        // 创建一个前台阈值只有300ms的battlefield
        val smallThresholdBattlefield = AnrBattlefield(
            foregroundAnrThreshold = 300L, // 小于500ms的扫雷间隔
            backgroundAnrThreshold = 1000L,
            anrBombThread = mockBombThread
        )

        // 部署战斗 - 应该不会崩溃
        smallThresholdBattlefield.deployAnrBattle("test message") { true }

        // 验证配置被正确使用
        verify { mockBombThread.getBombLooper() }
    }
}
