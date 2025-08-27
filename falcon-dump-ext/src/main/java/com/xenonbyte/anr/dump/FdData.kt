package com.xenonbyte.anr.dump

import org.json.JSONObject

/**
 * 文件描述符数据
 *
 * @author xubo
 */
class FdData(
    val openFd: Int
) : DumpData {
    companion object {
        //打开的文件描述符数
        const val OPEN_FD = "open_fd"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(OPEN_FD, openFd)
        }
    }
}