package cn.ts.agent.example;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 示例工具类
 * <p>
 * 包含一些示例工具方法，使用 Spring AI 的 @Tool 注解
 * </p>
 *
 * @author tianshuo
 */
@Component
public class ExampleTools {

    /**
     * 计算两个数的和
     *
     * @param a 第一个数
     * @param b 第二个数
     * @return 两数之和
     */
    @Tool(description = "计算两个数的和")
    public int add(int a, int b) {
        return a + b;
    }

    /**
     * 计算两个数的差
     *
     * @param a 被减数
     * @param b 减数
     * @return 两数之差
     */
    @Tool(description = "计算两个数的差")
    public int subtract(int a, int b) {
        return a - b;
    }

    /**
     * 计算两个数的积
     *
     * @param a 第一个数
     * @param b 第二个数
     * @return 两数之积
     */
    @Tool(description = "计算两个数的积")
    public int multiply(int a, int b) {
        return a * b;
    }

    /**
     * 计算两个数的商
     *
     * @param a 被除数
     * @param b 除数
     * @return 两数之商
     */
    @Tool(description = "计算两个数的商")
    public double divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为零");
        }
        return (double) a / b;
    }

    /**
     * 获取当前时间
     *
     * @return 当前时间的字符串表示
     */
    @Tool(description = "获取当前时间")
    public String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * 搜索天气信息
     *
     * @param city 城市名称
     * @return 天气信息
     */
    @Tool(description = "搜索指定城市的天气信息")
    public String getWeather(String city) {
        // 这里是模拟的天气数据
        // 实际应用中可以调用真实的天气 API
        return String.format("天气查询结果：%s 今天天气晴朗，温度 25°C", city);
    }

    /**
     * 获取城市人口
     *
     * @param city 城市名称
     * @return 城市人口信息
     */
    @Tool(description = "获取指定城市的人口信息")
    public String getCityPopulation(String city) {
        // 这里是模拟的数据
        // 实际应用中可以调用真实的 API
        return String.format("人口查询结果：%s 的人口约为 1000 万", city);
    }
}
