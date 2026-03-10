package com.xenonbyte.anr.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calculator 单元测试
 *
 * 演示各种 JUnit 5 测试技巧：
 * - 基本断言
 * - 异常测试
 * - 参数化测试
 */
@DisplayName("Calculator Tests")
class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    // ========== 基本运算测试 ==========

    @Test
    @DisplayName("加法: 2 + 3 = 5")
    void testAdd() {
        double result = calculator.add(2, 3);
        assertEquals(5, result, 0.001, "2 + 3 should equal 5");
    }

    @Test
    @DisplayName("加法: 负数相加")
    void testAddNegativeNumbers() {
        double result = calculator.add(-5, -3);
        assertEquals(-8, result, 0.001, "-5 + -3 should equal -8");
    }

    @Test
    @DisplayName("减法: 10 - 4 = 6")
    void testSubtract() {
        double result = calculator.subtract(10, 4);
        assertEquals(6, result, 0.001, "10 - 4 should equal 6");
    }

    @Test
    @DisplayName("乘法: 3 × 4 = 12")
    void testMultiply() {
        double result = calculator.multiply(3, 4);
        assertEquals(12, result, 0.001, "3 × 4 should equal 12");
    }

    @Test
    @DisplayName("乘法: 负数 × 负数 = 正数")
    void testMultiplyNegativeNumbers() {
        double result = calculator.multiply(-2, -3);
        assertEquals(6, result, 0.001, "-2 × -3 should equal 6");
    }

    @Test
    @DisplayName("除法: 20 ÷ 4 = 5")
    void testDivide() {
        double result = calculator.divide(20, 4);
        assertEquals(5, result, 0.001, "20 ÷ 4 should equal 5");
    }

    // ========== 异常测试 ==========

    @Test
    @DisplayName("除法: 除以零应抛出异常")
    void testDivideByZero() {
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> calculator.divide(10, 0),
                "Division by zero should throw IllegalArgumentException"
        );
        assertEquals("Division by zero is not allowed", exception.getMessage());
    }

    @Test
    @DisplayName("百分比: 超出范围应抛出异常")
    void testPercentageOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.percentage(100, 150);
        }, "Percentage > 100 should throw exception");

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.percentage(100, -10);
        }, "Negative percentage should throw exception");
    }

    @Test
    @DisplayName("平方根: 负数应抛出异常")
    void testSqrtNegativeNumber() {
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.sqrt(-1);
        }, "Square root of negative number should throw exception");
    }

    // ========== 参数化测试 ==========

    @ParameterizedTest
    @CsvSource({
            "1, 1, 2",
            "10, 20, 30",
            "-5, 5, 0",
            "0.1, 0.2, 0.3"
    })
    @DisplayName("参数化加法测试")
    void testAddParameterized(double a, double b, double expected) {
        double result = calculator.add(a, b);
        assertEquals(expected, result, 0.001,
                () -> a + " + " + b + " should equal " + expected);
    }

    @ParameterizedTest
    @CsvSource({
            "100, 10, 10",
            "200, 50, 100",
            "50, 0, 0"
    })
    @DisplayName("参数化百分比测试")
    void testPercentageParameterized(double value, double percent, double expected) {
        double result = calculator.percentage(value, percent);
        assertEquals(expected, result, 0.001);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 6, 8, 10, 100})
    @DisplayName("偶数判断测试")
    void testIsEven(int number) {
        assertTrue(calculator.isEven(number), number + " should be even");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5, 7, 9, 101})
    @DisplayName("奇数判断测试 (不是偶数)")
    void testIsOdd(int number) {
        assertFalse(calculator.isEven(number), number + " should not be even");
    }

    // ========== 边界测试 ==========

    @Test
    @DisplayName("边界: 零值运算")
    void testZeroOperations() {
        assertEquals(5, calculator.add(5, 0));
        assertEquals(5, calculator.subtract(5, 0));
        assertEquals(0, calculator.multiply(5, 0));
        assertEquals(0, calculator.divide(0, 5));
    }

    @Test
    @DisplayName("边界: 大数运算")
    void testLargeNumbers() {
        double large = 1_000_000_000.0;
        assertEquals(2_000_000_000.0, calculator.add(large, large), 0.001);
    }

    @Test
    @DisplayName("边界: 小数精度")
    void testDecimalPrecision() {
        double result = calculator.add(0.1, 0.2);
        assertEquals(0.3, result, 0.0001, "0.1 + 0.2 should be close to 0.3");
    }

    // ========== 组合测试 ==========

    @Test
    @DisplayName("组合: 链式运算")
    void testChainedOperations() {
        // (2 + 3) * 4 = 20
        double step1 = calculator.add(2, 3);
        double step2 = calculator.multiply(step1, 4);
        assertEquals(20, step2, 0.001);
    }

    @Test
    @DisplayName("组合: 所有运算")
    void testAllOperations() {
        double a = 10;
        double b = 2;

        assertAll("all basic operations",
                () -> assertEquals(12, calculator.add(a, b), 0.001),
                () -> assertEquals(8, calculator.subtract(a, b), 0.001),
                () -> assertEquals(20, calculator.multiply(a, b), 0.001),
                () -> assertEquals(5, calculator.divide(a, b), 0.001)
        );
    }
}
