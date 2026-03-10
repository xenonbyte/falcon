package com.xenonbyte.anr

import org.junit.Assert.*
import org.junit.Test

/**
 * 简单的测试用例，用于验证测试框架配置
 */
class SimpleTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun string_concatenation() {
        val result = "Hello" + " " + "World"
        assertEquals("Hello World", result)
    }
}
