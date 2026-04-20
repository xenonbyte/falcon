package com.xenonbyte.anr.dump

import org.json.JSONArray
import org.json.JSONObject

private const val UNKNOWN_DUMPER_NAME = "unknown"
private const val PAYLOAD_PARSE_ERROR_NAME = "__payload_parse_error__"

/**
 * `hprofData` 的结构化包装。
 *
 * 底层协议仍然保持 JSON 字符串不变，对外新增该类型用于安全解析和查询。
 */
class FalconDumpPayload private constructor(
    val entries: List<FalconDumpEntry>,
    val raw: String
) {
    companion object {
        /**
         * 解析 Falcon 回调中的 `hprofData`。
         *
         * 该方法不会抛异常；当原始字符串非法时，会返回一个带错误条目的 payload。
         */
        @JvmStatic
        fun parse(raw: String?): FalconDumpPayload {
            if (raw.isNullOrBlank()) {
                return FalconDumpPayload(emptyList(), raw.orEmpty())
            }
            return try {
                val jsonArray = JSONArray(raw)
                val entries = buildList(jsonArray.length()) {
                    for (index in 0 until jsonArray.length()) {
                        add(parseEntry(jsonArray.opt(index), index))
                    }
                }
                FalconDumpPayload(entries, raw)
            } catch (e: Exception) {
                FalconDumpPayload(
                    entries = listOf(
                        FalconDumpEntry(
                            name = PAYLOAD_PARSE_ERROR_NAME,
                            data = null,
                            error = "parse dump payload failed: ${e.message}"
                        )
                    ),
                    raw = raw
                )
            }
        }

        @JvmStatic
        fun empty(): FalconDumpPayload = FalconDumpPayload(emptyList(), "")

        private fun parseEntry(value: Any?, index: Int): FalconDumpEntry {
            val jsonObject = value as? JSONObject
            if (jsonObject == null) {
                return FalconDumpEntry(
                    name = UNKNOWN_DUMPER_NAME,
                    data = null,
                    error = "unexpected dump payload entry at index=$index"
                )
            }
            return FalconDumpEntry(
                name = jsonObject.optString(KEY_DUMPER_NAME, UNKNOWN_DUMPER_NAME),
                data = jsonObject.optJSONObject(KEY_DUMPER_DATA),
                error = jsonObject.optString(KEY_DUMPER_ERROR).ifBlank { null }
            )
        }
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun hasErrors(): Boolean = entries.any { it.error != null }

    fun find(name: String): FalconDumpEntry? = entries.firstOrNull { it.name == name }

    fun require(name: String): FalconDumpEntry {
        return find(name) ?: throw IllegalArgumentException("No dump entry found for name=$name")
    }

    fun <T> decode(name: String, decoder: (JSONObject) -> T): T? {
        return find(name)?.decode(decoder)
    }

    fun <T> requireDecoded(name: String, decoder: (JSONObject) -> T): T {
        val entry = require(name)
        check(entry.isSuccess()) { "Dump entry for name=$name failed: ${entry.error}" }
        val data = entry.data
        check(data != null) { "Dump entry for name=$name has no data" }
        val decoded = decoder(data)
        if (decoded is DumpData) {
            validateDumpDataShape(
                name = name,
                actual = data,
                expected = decoded.toJson(),
                path = name
            )
        }
        return decoded
    }
}

/**
 * 单个 Dumper 结果条目。
 */
class FalconDumpEntry(
    val name: String,
    val data: JSONObject?,
    val error: String?
) {
    fun isSuccess(): Boolean = error == null

    fun <T> decode(decoder: (JSONObject) -> T): T? {
        if (!isSuccess()) {
            return null
        }
        return data?.let(decoder)
    }
}

private fun validateDumpDataShape(
    name: String,
    actual: JSONObject,
    expected: JSONObject,
    path: String
) {
    val keys = expected.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val keyPath = "$path.$key"
        check(actual.has(key) && !actual.isNull(key)) {
            "Dump entry for name=$name is missing required field: $keyPath"
        }
        validateDumpDataValue(
            name = name,
            actual = actual.get(key),
            expected = expected.get(key),
            path = keyPath
        )
    }
}

private fun validateDumpDataValue(
    name: String,
    actual: Any,
    expected: Any,
    path: String
) {
    when (expected) {
        is JSONObject -> {
            check(actual is JSONObject) {
                "Dump entry for name=$name has invalid field type at $path"
            }
            validateDumpDataShape(name, actual, expected, path)
        }

        is JSONArray -> {
            check(actual is JSONArray) {
                "Dump entry for name=$name has invalid field type at $path"
            }
            check(actual.length() >= expected.length()) {
                "Dump entry for name=$name has invalid array size at $path"
            }
            for (index in 0 until expected.length()) {
                validateDumpDataValue(
                    name = name,
                    actual = actual.get(index),
                    expected = expected.get(index),
                    path = "$path[$index]"
                )
            }
        }

        is Number -> check(actual is Number) {
            "Dump entry for name=$name has invalid field type at $path"
        }

        is Boolean -> check(actual is Boolean) {
            "Dump entry for name=$name has invalid field type at $path"
        }

        is String -> check(actual is String) {
            "Dump entry for name=$name has invalid field type at $path"
        }
    }
}
