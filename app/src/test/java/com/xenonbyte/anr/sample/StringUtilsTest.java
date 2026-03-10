package com.xenonbyte.anr.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字符串工具类测试
 *
 * 演示嵌套测试 (Nested Tests) 的用法
 */
@DisplayName("StringUtils Tests")
class StringUtilsTest {

    // ========== 嵌套测试：按功能分组 ==========

    @Nested
    @DisplayName("isEmpty 方法测试")
    class IsEmptyTests {

        @Test
        @DisplayName("null 应返回 true")
        void testNull() {
            assertTrue(StringUtils.isEmpty(null));
        }

        @Test
        @DisplayName("空字符串应返回 true")
        void testEmptyString() {
            assertTrue(StringUtils.isEmpty(""));
        }

        @Test
        @DisplayName("空格字符串应返回 false")
        void testWhitespace() {
            assertFalse(StringUtils.isEmpty("   "));
        }

        @Test
        @DisplayName("非空字符串应返回 false")
        void testNonEmpty() {
            assertFalse(StringUtils.isEmpty("hello"));
        }
    }

    @Nested
    @DisplayName("isBlank 方法测试")
    class IsBlankTests {

        @Test
        @DisplayName("null 应返回 true")
        void testNull() {
            assertTrue(StringUtils.isBlank(null));
        }

        @Test
        @DisplayName("空字符串应返回 true")
        void testEmptyString() {
            assertTrue(StringUtils.isBlank(""));
        }

        @Test
        @DisplayName("空格字符串应返回 true")
        void testWhitespace() {
            assertTrue(StringUtils.isBlank("   "));
        }

        @Test
        @DisplayName("有内容的字符串应返回 false")
        void testNonBlank() {
            assertFalse(StringUtils.isBlank("  hello  "));
        }
    }

    @Nested
    @DisplayName("truncate 方法测试")
    class TruncateTests {

        @Test
        @DisplayName("短于最大长度不截断")
        void testShortString() {
            assertEquals("hello", StringUtils.truncate("hello", 10));
        }

        @Test
        @DisplayName("等于最大长度不截断")
        void testExactLength() {
            assertEquals("hello", StringUtils.truncate("hello", 5));
        }

        @Test
        @DisplayName("超长字符串应截断并添加后缀")
        void testLongString() {
            String result = StringUtils.truncate("hello world", 8);
            assertEquals("hello...", result);
            assertEquals(8, result.length());
        }

        @Test
        @DisplayName("最大长度小于 4 应抛出异常")
        void testInvalidMaxLength() {
            assertThrows(IllegalArgumentException.class, () -> {
                StringUtils.truncate("hello", 3);
            });
        }
    }

    @Nested
    @DisplayName("reverse 方法测试")
    class ReverseTests {

        @Test
        @DisplayName("普通字符串反转")
        void testNormalString() {
            assertEquals("cba", StringUtils.reverse("abc"));
        }

        @Test
        @DisplayName("空字符串反转")
        void testEmptyString() {
            assertEquals("", StringUtils.reverse(""));
        }

        @Test
        @DisplayName("null 反转应返回 null")
        void testNull() {
            assertNull(StringUtils.reverse(null));
        }

        @Test
        @DisplayName("带空格的字符串反转")
        void testWithSpaces() {
            assertEquals("dlrow olleh", StringUtils.reverse("hello world"));
        }
    }

    @Nested
    @DisplayName("countWords 方法测试")
    class CountWordsTests {

        @Test
        @DisplayName("普通句子")
        void testNormalSentence() {
            assertEquals(3, StringUtils.countWords("hello world test"));
        }

        @Test
        @DisplayName("多个空格分隔")
        void testMultipleSpaces() {
            assertEquals(3, StringUtils.countWords("hello   world   test"));
        }

        @Test
        @DisplayName("前后有空格")
        void testLeadingTrailingSpaces() {
            assertEquals(2, StringUtils.countWords("  hello world  "));
        }

        @Test
        @DisplayName("空字符串")
        void testEmptyString() {
            assertEquals(0, StringUtils.countWords(""));
        }

        @Test
        @DisplayName("null 应返回 0")
        void testNull() {
            assertEquals(0, StringUtils.countWords(null));
        }
    }
}
