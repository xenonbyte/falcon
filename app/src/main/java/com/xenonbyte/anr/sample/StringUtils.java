package com.xenonbyte.anr.sample;

/**
 * 字符串工具类
 *
 * 用于演示单元测试的示例类。
 */
public class StringUtils {

    private StringUtils() {
        // 工具类不允许实例化
    }

    /**
     * 判断字符串是否为空
     *
     * @param str 输入字符串
     * @return null 或空字符串返回 true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否为空白
     *
     * @param str 输入字符串
     * @return null、空字符串或全空格返回 true
     */
    public static boolean isBlank(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 截断字符串
     *
     * @param str       输入字符串
     * @param maxLength 最大长度（至少 4，用于显示 "..."）
     * @return 截断后的字符串
     * @throws IllegalArgumentException 当 maxLength < 4 时抛出
     */
    public static String truncate(String str, int maxLength) {
        if (maxLength < 4) {
            throw new IllegalArgumentException("maxLength must be at least 4");
        }
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 反转字符串
     *
     * @param str 输入字符串
     * @return 反转后的字符串，null 返回 null
     */
    public static String reverse(String str) {
        if (str == null) {
            return null;
        }
        return new StringBuilder(str).reverse().toString();
    }

    /**
     * 统计单词数
     *
     * @param str 输入字符串
     * @return 单词数量
     */
    public static int countWords(String str) {
        if (str == null || str.trim().isEmpty()) {
            return 0;
        }
        String[] words = str.trim().split("\\s+");
        return words.length;
    }
}
