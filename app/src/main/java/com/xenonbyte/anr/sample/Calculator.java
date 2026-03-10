package com.xenonbyte.anr.sample;

/**
 * 简单计算器类
 *
 * 用于演示单元测试的示例类。
 */
public class Calculator {

    /**
     * 加法
     */
    public double add(double a, double b) {
        return a + b;
    }

    /**
     * 减法
     */
    public double subtract(double a, double b) {
        return a - b;
    }

    /**
     * 乘法
     */
    public double multiply(double a, double b) {
        return a * b;
    }

    /**
     * 除法
     *
     * @throws IllegalArgumentException 当除数为 0 时抛出
     */
    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Division by zero is not allowed");
        }
        return a / b;
    }

    /**
     * 计算百分比
     */
    public double percentage(double value, double percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
        }
        return value * (percent / 100);
    }

    /**
     * 计算幂
     */
    public double power(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    /**
     * 计算平方根
     *
     * @throws IllegalArgumentException 当输入负数时抛出
     */
    public double sqrt(double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot calculate square root of negative number");
        }
        return Math.sqrt(value);
    }

    /**
     * 判断是否为偶数
     */
    public boolean isEven(int number) {
        return number % 2 == 0;
    }

    /**
     * 获取绝对值
     */
    public double abs(double value) {
        return Math.abs(value);
    }
}
