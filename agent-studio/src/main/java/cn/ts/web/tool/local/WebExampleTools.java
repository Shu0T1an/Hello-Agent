package cn.ts.web.tool.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 示例本地工具集
 * <p>
 * 这个类演示了如何创建本地工具。每个使用 @Tool 注解的方法都会被
 * LocalToolScanner 自动扫描并注册到 tool_definition 表。
 * </p>
 */
@Component("webExampleTools")
public class WebExampleTools {

    private static final Logger logger = LoggerFactory.getLogger(WebExampleTools.class);

    /**
     * 计算器工具 - 加法
     */
    @Tool(name = "calculator_add", description = "计算两个数的和")
    public double add(double a, double b) {
        logger.info("Calculating: {} + {}", a, b);
        return a + b;
    }

    /**
     * 计算器工具 - 减法
     */
    @Tool(name = "calculator_subtract", description = "计算两个数的差")
    public double subtract(double a, double b) {
        logger.info("Calculating: {} - {}", a, b);
        return a - b;
    }

    /**
     * 计算器工具 - 乘法
     */
    @Tool(name = "calculator_multiply", description = "计算两个数的乘积")
    public double multiply(double a, double b) {
        logger.info("Calculating: {} * {}", a, b);
        return a * b;
    }

    /**
     * 计算器工具 - 除法
     */
    @Tool(name = "calculator_divide", description = "计算两个数的商")
    public double divide(double a, double b) {
        logger.info("Calculating: {} / {}", a, b);
        if (b == 0) {
            throw new IllegalArgumentException("Divisor cannot be zero");
        }
        return a / b;
    }

    /**
     * 字符串工具 - 转大写
     */
    @Tool(name = "string_toUpperCase", description = "将字符串转换为大写")
    public String toUpperCase(String text) {
        logger.info("Converting to uppercase: {}", text);
        return text.toUpperCase();
    }

    /**
     * 字符串工具 - 转小写
     */
    @Tool(name = "string_toLowerCase", description = "将字符串转换为小写")
    public String toLowerCase(String text) {
        logger.info("Converting to lowercase: {}", text);
        return text.toLowerCase();
    }

    /**
     * 字符串工具 - 反转字符串
     */
    @Tool(name = "string_reverse", description = "反转字符串")
    public String reverse(String text) {
        logger.info("Reversing string: {}", text);
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * 数学工具 - 计算平方
     */
    @Tool(name = "math_square", description = "计算一个数的平方")
    public double square(double x) {
        logger.info("Calculating square of: {}", x);
        return x * x;
    }

    /**
     * 数学工具 - 计算平方根
     */
    @Tool(name = "math_sqrt", description = "计算一个数的平方根")
    public double sqrt(double x) {
        logger.info("Calculating square root of: {}", x);
        if (x < 0) {
            throw new IllegalArgumentException("Cannot calculate square root of negative number");
        }
        return Math.sqrt(x);
    }

    /**
     * 数学工具 - 计算幂
     */
    @Tool(name = "math_power", description = "计算 x 的 y 次幂")
    public double power(double x, double y) {
        logger.info("Calculating: {} ^ {}", x, y);
        return Math.pow(x, y);
    }

    /**
     * 时间工具 - 获取当前时间戳
     */
    @Tool(name = "time_currentTimestamp", description = "获取当前时间戳（毫秒）")
    public long getCurrentTimestamp() {
        long timestamp = System.currentTimeMillis();
        logger.info("Current timestamp: {}", timestamp);
        return timestamp;
    }

    /**
     * 时间工具 - 线程休眠
     */
    @Tool(name = "time_sleep", description = "让当前线程休眠指定的毫秒数")
    public String sleep(long milliseconds) {
        logger.info("Sleeping for {} milliseconds", milliseconds);
        try {
            Thread.sleep(milliseconds);
            return "Slept for " + milliseconds + " milliseconds";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Sleep was interrupted";
        }
    }
}
