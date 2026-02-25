# 本地工具目录

这个目录用于存放所有本地工具，这些工具会被 `LocalToolScanner` 自动扫描并注册到 `tool_definition` 表。

## 如何创建本地工具

### 1. 创建工具类

在 `cn.ts.web.tools` 包下创建一个新类，并添加 `@Component` 注解：

```java
package cn.ts.web.tools;

import org.springframework.ai.chat.client.advisor.Tool;
import org.springframework.stereotype.Component;

@Component
public class MyCustomTools {

    @Tool(name = "my_tool", description = "我的自定义工具")
    public String myTool(String input) {
        // 工具实现逻辑
        return "处理结果: " + input;
    }
}
```

### 2. 工具注解说明

使用 Spring AI 的 `@Tool` 注解来标记工具方法：

- **name**: 工具名称（必须唯一），如果不指定则使用方法名
- **description**: 工具描述，用于 AI 理解工具的用途

### 3. 方法签名规范

工具方法应该遵循以下规范：

```java
// 简单参数
@Tool(name = "tool_name", description = "工具描述")
public ReturnType toolName(ParamType1 param1, ParamType2 param2) {
    // 实现
}

// 支持的参数类型：
// - 基本类型：int, long, double, float, boolean
// - 字符串：String
// - 对象：会被序列化为 JSON
```

### 4. 工具最佳实践

#### 4.1 参数验证

```java
@Tool(name = "divide", description = "除法计算")
public double divide(double a, double b) {
    if (b == 0) {
        throw new IllegalArgumentException("除数不能为零");
    }
    return a / b;
}
```

#### 4.2 日志记录

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MyTools {
    private static final Logger logger = LoggerFactory.getLogger(MyTools.class);

    @Tool(name = "my_tool", description = "工具描述")
    public String myTool(String input) {
        logger.info("执行工具 my_tool，输入: {}", input);
        // ... 实现
        return result;
    }
}
```

#### 4.3 异常处理

```java
@Tool(name = "risky_tool", description = "可能失败的工具")
public String riskyTool(String input) {
    try {
        // 可能失败的操作
        return doSomething(input);
    } catch (Exception e) {
        logger.error("工具执行失败: {}", e.getMessage(), e);
        return "错误: " + e.getMessage();
    }
}
```

### 5. 工具类型

#### 5.1 计算工具

```java
@Tool(name = "calculate_tax", description = "计算税费")
public double calculateTax(double amount, double rate) {
    return amount * rate;
}
```

#### 5.2 数据转换工具

```java
@Tool(name = "json_to_xml", description = "将 JSON 转换为 XML")
public String jsonToXml(String json) {
    // 转换逻辑
    return xmlResult;
}
```

#### 5.3 外部 API 调用工具

```java
import org.springframework.web.client.RestTemplate;

@Component
public class ApiTools {
    private final RestTemplate restTemplate;

    public ApiTools(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Tool(name = "get_weather", description = "获取天气信息")
    public String getWeather(String city) {
        String url = "https://api.weather.com/" + city;
        return restTemplate.getForObject(url, String.class);
    }
}
```

#### 5.4 数据库查询工具

```java
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class DatabaseTools {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(name = "get_user", description = "根据 ID 获取用户信息")
    public String getUser(Long userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, userId);
    }
}
```

### 6. 自动注册流程

1. 应用启动时，`LocalToolScanner` 会自动扫描 `cn.ts.web.tools` 包
2. 检测所有带 `@Component` 注解的类
3. 查找这些类中带 `@Tool` 注解的方法
4. 自动在 `tool_definition` 表中创建或更新工具记录
5. 工具类型标记为 `LOCAL`

### 7. 测试工具

创建工具后，可以通过以下方式测试：

1. **检查数据库**：
   ```sql
   SELECT * FROM tool_definition WHERE tool_type = 'LOCAL';
   ```

2. **使用 Agent**：
   ```bash
   # 创建一个使用该工具的 Agent
   curl -X POST http://localhost:8080/api/agents \
     -H "Content-Type: application/json" \
     -d '{
       "agentName": "test-agent",
       "displayName": "测试 Agent",
       "modelId": 1,
       "toolIds": [1],
       "systemPrompt": "你是一个有用的助手"
     }'
   ```

3. **执行 Agent**：
   ```bash
   curl "http://localhost:8080/api/stream/agent/test-agent/execute?message=使用计算器工具计算 5 + 3"
   ```

### 8. 注意事项

1. **唯一性**：确保工具名称在整个系统中唯一
2. **描述清晰**：提供清晰的描述帮助 AI 理解工具用途
3. **线程安全**：工具方法应该是线程安全的
4. **性能考虑**：避免长时间运行的操作，或使用异步方式
5. **错误处理**：妥善处理异常，避免影响整个 Agent 执行

### 9. 示例工具

查看 `ExampleTools.java` 获取更多示例：
- 计算器工具（加减乘除）
- 字符串工具（大小写转换、反转）
- 数学工具（平方、平方根、幂运算）
- 时间工具（获取时间戳、休眠）

## 10. FileSystemTools (Read/Write/Edit/Glob/Grep)

`FileSystemTools` �ṩ���� Claude Code ���һ�µ� 5 ���ļ����ߣ�

- `Read(file_path, offset?, limit?, pages?)`
- `Write(file_path, content)`
- `Edit(file_path, old_string, new_string, replace_all?)`
- `Glob(pattern, path?)`
- `Grep(pattern, path?, glob?, output_mode?, type?, ignore_case?, context?, after_context?, before_context?, multiline?)`

Ĭ�ϰ�ȫ���ԣ�

- ����������`D:/JavaProject/Hello-Agent`
- д��������`D:/JavaProject/Hello-Agent/docs`��`D:/JavaProject/Hello-Agent/uploads`

Ĭ������λ�� `application.yml` �� `agent.filetool` �ڵ㡣
