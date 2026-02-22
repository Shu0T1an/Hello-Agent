package cn.ts.web.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 简单的测试工具集
 *
 * @author tianshuo
 */
@Component
public class SimpleTools {

    @Tool(description = "获取当前日期和时间")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "计算两个数的和")
    public double add(double a, double b) {
        return a + b;
    }

    @Tool(description = "计算两个数的乘积")
    public double multiply(double a, double b) {
        return a * b;
    }
}
