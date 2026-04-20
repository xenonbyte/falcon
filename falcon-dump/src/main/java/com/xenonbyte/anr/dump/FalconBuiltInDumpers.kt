package com.xenonbyte.anr.dump

import android.app.Application
import com.xenonbyte.anr.dump.internal.AppDumper
import com.xenonbyte.anr.dump.internal.MemoryDumper
import com.xenonbyte.anr.dump.internal.ThreadDumper

/**
 * 内置应用信息 Dumper 的公开包装。
 *
 * 保持与历史内置 dumper 相同的 payload name，便于与已有解析逻辑兼容。
 */
class FalconAppDumper : Dumper<FalconAppData>("AppDumper") {
    private val delegate = AppDumper()

    override fun collectData(app: Application): FalconAppData {
        return delegate.collectData(app).toStableModel()
    }
}

/**
 * 内置内存信息 Dumper 的公开包装。
 */
class FalconMemoryDumper : Dumper<FalconMemoryData>("MemoryDumper") {
    private val delegate = MemoryDumper()

    override fun collectData(app: Application): FalconMemoryData {
        return delegate.collectData(app).toStableModel()
    }
}

/**
 * 内置线程信息 Dumper 的公开包装。
 */
class FalconThreadDumper : Dumper<FalconThreadData>("ThreadDumper") {
    private val delegate = ThreadDumper()

    override fun collectData(app: Application): FalconThreadData {
        return delegate.collectData(app).toStableModel()
    }
}
