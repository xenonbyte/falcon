package com.xenonbyte.anr

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Falcon 健康监控器
 *
 * 用于监控 ANR 监测库自身的健康状态，防止监控库本身影响应用性能。
 *
 * ## 功能
 *
 * 1. **错误计数**: 记录监控过程中发生的错误数量
 * 2. **自动降级**: 当错误数超过阈值时自动进入降级模式
 * 3. **自动恢复**: 错误间隔超过重置间隔后自动恢复
 * 4. **错误详情**: 保留最近的错误信息用于诊断
 *
 * ## 健康状态
 *
 * - [HealthStatus.HEALTHY]: 正常运行，完整监控功能
 * - [HealthStatus.DEGRADED]: 降级模式，跳过部分监控以减少影响
 *
 * ## 配置参数
 *
 * - [maxErrorCount]: 触发降级的错误阈值（默认 50）
 * - [errorResetIntervalMs]: 错误计数重置间隔（默认 60 秒）
 *
 * @param maxErrorCount 触发降级的最大错误数量
 * @param errorResetIntervalMs 错误计数重置间隔（毫秒）
 * @author xubo
 */
internal class FalconHealthMonitor(
    private val maxErrorCount: Int = DEFAULT_MAX_ERROR_COUNT,
    private val errorResetIntervalMs: Long = DEFAULT_ERROR_RESET_INTERVAL_MS
) {

    companion object {
        /**
         * 默认最大错误数量
         * 超过此数量后进入降级模式
         */
        private const val DEFAULT_MAX_ERROR_COUNT = 50

        /**
         * 默认错误重置间隔（毫秒）
         * 超过此间隔后重置错误计数
         */
        private const val DEFAULT_ERROR_RESET_INTERVAL_MS = 60_000L // 1 分钟

        /**
         * 保留的最近错误记录数量
         */
        private const val MAX_RECENT_ERRORS = 10
    }

    // 错误计数
    private val errorCount = AtomicLong(0)

    // 最后一次错误时间
    private val lastErrorTime = AtomicLong(0)

    // 健康状态
    private val healthStatus = AtomicReference(HealthStatus.HEALTHY)

    // 错误详情（保留最近 MAX_RECENT_ERRORS 个错误）
    private val recentErrors = mutableListOf<ErrorRecord>()

    // 错误列表访问锁
    private val errorLock = Any()

    /**
     * 记录错误
     *
     * @param error 错误信息
     * @return 是否超过错误阈值
     */
    fun recordError(error: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastErrorTime.get()

        // 如果距离上次错误超过重置间隔，重置错误计数
        if (currentTime - lastTime > errorResetIntervalMs) {
            errorCount.set(0)
            healthStatus.set(HealthStatus.HEALTHY)
        }

        val newCount = errorCount.incrementAndGet()
        lastErrorTime.set(currentTime)

        // 记录错误详情
        synchronized(errorLock) {
            recentErrors.add(ErrorRecord(currentTime, error))
            if (recentErrors.size > MAX_RECENT_ERRORS) {
                recentErrors.removeAt(0)
            }
        }

        // 检查是否超过阈值
        if (newCount >= maxErrorCount) {
            healthStatus.set(HealthStatus.DEGRADED)
            FalconUtils.log(LogLevel.ERROR) {
                "Falcon health monitor: Error count ($newCount) exceeded threshold ($maxErrorCount), " +
                    "switching to DEGRADED mode"
            }
            return true
        }

        return false
    }

    /**
     * 重置健康状态
     */
    fun reset() {
        errorCount.set(0)
        lastErrorTime.set(0)
        healthStatus.set(HealthStatus.HEALTHY)
        synchronized(errorLock) {
            recentErrors.clear()
        }
        FalconUtils.log(LogLevel.WARN) {
            "Falcon health monitor: Reset to HEALTHY status"
        }
    }

    /**
     * 获取当前健康状态
     */
    fun getHealthStatus(): HealthStatus = healthStatus.get()

    /**
     * 是否健康
     */
    fun isHealthy(): Boolean = healthStatus.get() == HealthStatus.HEALTHY

    /**
     * 获取错误统计信息
     */
    fun getErrorStats(): ErrorStats {
        synchronized(errorLock) {
            return ErrorStats(
                errorCount.get(),
                lastErrorTime.get(),
                healthStatus.get(),
                recentErrors.toList()
            )
        }
    }

    /**
     * 健康状态枚举
     */
    enum class HealthStatus {
        HEALTHY, // 健康，正常运行
        DEGRADED // 降级，错误过多但仍可运行
    }

    /**
     * 错误记录
     */
    data class ErrorRecord(
        val timestamp: Long,
        val error: String
    )

    /**
     * 错误统计信息
     */
    data class ErrorStats(
        val totalErrors: Long,
        val lastErrorTime: Long,
        val status: HealthStatus,
        val recentErrors: List<ErrorRecord>
    )
}
