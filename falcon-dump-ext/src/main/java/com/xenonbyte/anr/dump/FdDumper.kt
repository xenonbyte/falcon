package com.xenonbyte.anr.dump

import android.app.Application
import java.io.File

/**
 * Fd数据dumper
 *
 * @author xubo
 */
class FdDumper : Dumper<FdData>("FdDumper") {
    override fun collectData(app: Application): FdData {
        val fdFile = File("/proc/self/fd")
        val openFd = fdFile.list()?.size ?: 0
        return FdData(openFd)
    }
}