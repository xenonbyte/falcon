package com.xenonbyte.anr.sample;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xenonbyte.anr.Falcon;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Falcon 集成测试 (在 Demo 应用中)
 *
 * 验证 Falcon 在真实应用环境中的功能
 */
@RunWith(AndroidJUnit4.class)
public class FalconIntegrationTest {

    @Before
    public void setUp() {
        // 每个测试前的准备工作
    }

    @After
    public void tearDown() {
        try {
            Falcon.stopMonitoring();
            Falcon.resetHealthMonitor();
        } catch (Exception e) {
            // 忽略清理异常
        }
    }

    @Test
    public void testFalconIsInitialized() {
        // 验证 Falcon 已经在 Application 中初始化
        String healthStatus = Falcon.getHealthStatus();
        assertNotNull("Health status should not be null", healthStatus);
        assertTrue("Health status should contain information",
                healthStatus.length() > 0);
    }

    @Test
    public void testGetHealthStatus() {
        String status = Falcon.getHealthStatus();

        // 验证健康状态包含必要信息
        assertNotNull(status);
        System.out.println("Falcon Health Status:\n" + status);
    }

    @Test
    public void testResetHealthMonitor() {
        // 重置健康监控器
        Falcon.resetHealthMonitor();

        String status = Falcon.getHealthStatus();
        assertNotNull(status);
    }

    @Test
    public void testStopAndRestartMonitoring() throws InterruptedException {
        // 停止监控
        Falcon.stopMonitoring();
        Thread.sleep(200);

        // 重新启动监控
        Falcon.startMonitoring();
        Thread.sleep(200);

        // 验证状态
        String status = Falcon.getHealthStatus();
        assertNotNull(status);
    }

    @Test
    public void testApplicationContext() {
        // 验证应用上下文正确
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.xenonbyte.anr.sample", appContext.getPackageName());
    }

    private void assertEquals(String expected, String actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
