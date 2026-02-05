# Spring AI Alibaba 可观测性完整指南

## 目录

- [一、可观测性概述](#一可观测性概述)
- [二、核心概念](#二核心概念)
- [三、预定义指标](#三预定义指标)
- [四、实现原理](#四实现原理)
- [五、快速开始](#五快速开始)
- [六、查看观测数据](#六查看观测数据)
- [七、集成外部平台](#七集成外部平台)
- [八、高级配置](#八高级配置)
- [九、最佳实践](#九最佳实践)

---

## 一、可观测性概述

### 1.1 什么是可观测性

可观测性（Observability）是指通过观察系统的外部输出（指标、日志、追踪）来理解系统内部状态的能力。

Spring AI Alibaba 基于 **Micrometer Observation API** 实现了完整的可观测性支持，无需修改业务代码即可自动捕获图工作流的执行信息。

### 1.2 可观测性的三大支柱

```
┌─────────────────────────────────────────────────────────────────┐
│                      可观测性                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Metrics    │  │   Tracing    │  │ Structured Logging   │  │
│  │   指标       │  │   追踪       │  │  结构化日志          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│         │                  │                      │             │
│         ▼                  ▼                      ▼             │
│   ┌──────────┐      ┌──────────┐           ┌──────────┐        │
│   │ 数字聚合 │      │  请求链路│           │  事件记录│        │
│   │ 计数/耗时│      │  调用关系│           │  详细日志│        │
│   │ QPS/延迟 │      │  Span树  │           │  JSON格式│        │
│   └──────────┘      └──────────┘           └──────────┘        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

| 支柱 | 作用 | 回答问题 | 典型工具 |
|------|------|----------|----------|
| **Metrics** | 数字化聚合数据 | 发生了多少？有多快？ | Prometheus, Grafana |
| **Tracing** | 请求路径记录 | 经过了哪些节点？调用关系？ | Jaeger, Tempo, Langfuse |
| **Logging** | 结构化事件记录 | 发生了什么？详细信息？ | ELK, Loki |

### 1.3 项目中的可观测性架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Micrometer Observation API                   │
│                    (统一的可观测性框架)                          │
└─────────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ Metrics  │    │ Tracing  │    │ Logging  │
    │ Counter  │    │ Span     │    │ KeyValue │
    │ Timer    │    │ TraceId  │    │ Context  │
    └──────────┘    └──────────┘    └──────────┘
          │               │               │
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │Prometheus│    │ Jaeger   │    │ Langfuse │
    │Grafana   │    │ Tempo    │    │ ELK      │
    └──────────┘    └──────────┘    └──────────┘
```

---

## 二、核心概念

### 2.1 观测级别

Spring AI Alibaba 定义了三种观测类型（`SpringAiAlibabaKind`）：

| 类型 | 枚举值 | 说明 | 代码位置 |
|------|--------|------|----------|
| **图级别** | `GRAPH` | 整个图执行过程的观测 | `GraphObservationDocumentation.java` |
| **节点级别** | `GRAPH_NODE` | 单个节点执行的观测 | `GraphNodeObservationDocumentation.java` |
| **边级别** | `GRAPH_EDGE` | 条件边（路由）执行的观测 | `GraphEdgeObservationDocumentation.java` |

### 2.2 观测上下文

每种观测类型都有对应的 Context 类，定义了要捕获的数据结构：

```java
// 图级别上下文
public class GraphObservationContext extends Observation.Context {
    private final String graphName;      // 图名称
    private final Map<String, Object> state;  // 输入状态
    private final Object output;         // 输出结果
}

// 节点级别上下文
public class GraphNodeObservationContext extends Observation.Context {
    private final String nodeName;       // 节点名称
    private final String event;          // 事件类型
}

// 边级别上下文
public class GraphEdgeObservationContext extends Observation.Context {
    private final String graphEdgeName;  // 边名称
    private final Map<String, Object> state;  // 执行状态
    private final Object output;         // 输出结果
}
```

### 2.3 生命周期监听器

`GraphObservationLifecycleListener` 是可观测性的核心实现，通过 `GraphLifecycleListener` 接口监听图执行的各个阶段：

| 事件 | 触发时机 | 观测操作 |
|------|----------|----------|
| `onStart` | 图开始执行（START 节点） | 创建父级 Observation |
| `before` | 节点执行前 | 创建子级 Observation |
| `after` | 节点执行后 | 停止子级 Observation |
| `onComplete` | 图完成（END 节点） | 停止父级 Observation |
| `onError` | 发生错误 | 记录错误信息，停止 Observation |

---

## 三、预定义指标

### 3.1 指标名称

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `spring.ai.alibaba.graph` | Counter | 图执行计数 |
| `spring.ai.alibaba.graph.node` | Counter | 节点执行计数 |
| `spring.ai.alibaba.graph.edge` | Counter | 边执行计数 |

### 3.2 低基数标签（Low Cardinality）

适合用于聚合查询和分组统计：

| 标签名 | 值示例 | 说明 |
|--------|--------|------|
| `spring.ai.alibaba.kind` | `graph` / `graph_node` / `graph_edge` | 观测类型 |
| `spring.ai.alibaba.graph.name` | `customer-service-bot` | 图名称 |
| `spring.ai.alibaba.graph.node.name` | `agent1` / `searchKB` | 节点名称 |
| `spring.ai.alibaba.graph.edge.name` | `agent1->agent2` | 边名称 |
| `spring.ai.alibaba.graph.success` | `true` / `false` | 图执行是否成功 |
| `spring.ai.alibaba.graph.node.success` | `true` / `false` | 节点执行是否成功 |
| `spring.ai.alibaba.graph.edge.success` | `true` / `false` | 边执行是否成功 |
| `spring.ai.alibaba.graph.event` | 事件类型 | 节点事件 |

### 3.3 高基数标签（High Cardinality）

适合详细分析，可能包含大量唯一值：

| 标签名 | 值示例 | 说明 |
|--------|--------|------|
| `node.before.state` | `user_input=查天气; intent=QUERY_WEATHER;` | 节点输入状态（OpenTelemetry 语义约定） |
| `node.after.state` | `temperature=5; weather=晴;` | 节点输出状态（OpenTelemetry 语义约定） |
| `spring.ai.alibaba.graph.state` | 完整状态字符串 | 图执行状态 |
| `spring.ai.alibaba.graph.output` | 输出结果字符串 | 图执行输出 |

### 3.4 GenAI 标准标签（遵循 OpenTelemetry GenAI 规范）

| 标签名 | 说明 |
|--------|------|
| `gen_ai.prompt` | AI 输入提示（GenAI OTel 约定） |
| `gen_ai.completion` | AI 输出完成（GenAI OTel 约定） |

### 3.5 Langfuse 兼容标签

| 标签名 | 说明 |
|--------|------|
| `langfuse.observation.input` | Langfuse 格式输入 |
| `langfuse.observation.output` | Langfuse 格式输出 |

### 3.6 Micrometer 自动提供的指标

| 指标 | 说明 |
|------|------|
| `duration` | 执行时长（自动计算 start 到 stop 的时间） |
| `error` | 异常信息（如果有错误） |
| `traceId` | 分布式追踪 ID |
| `spanId` | 当前 Span ID |
| `parentSpanId` | 父 Span ID |

---

## 四、实现原理

### 4.1 架构设计

```
用户请求
    │
    ▼
┌─────────────────┐
│   GraphRunner   │
│   (执行引擎)     │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│     NodeExecutor (节点执行器)        │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  context.doListeners(...)   │───┼──→ 触发监听器
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  GraphLifecycleListener 接口        │
│                                     │
│  ┌────────────────────────────────┐│
│  │ GraphObservationLifecycleListener ││ ← 可观测性实现
│  └────────────────────────────────┘│
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Micrometer Observation API        │
│   (统一的可观测性框架)               │
└────────┬────────────────────────────┘
         │
         ▼
   OTLP / Langfuse / Prometheus
```

### 4.2 监听器触发流程

代码位置：`GraphRunnerContext.java:287`

```java
public void doListeners(String scene, Exception e) {
    // 遍历所有注册的监听器
    for (GraphLifecycleListener listener : compiledGraph.compileConfig.lifecycleListeners()) {
        try {
            switch (scene) {
                case START:
                    listener.onStart(getCurrentNodeId(), getCurrentStateData(), config);
                    break;
                case NODE_BEFORE:
                    listener.before(getCurrentNodeId(), getCurrentStateData(), config, SystemClock.now());
                    break;
                case NODE_AFTER:
                    listener.after(getCurrentNodeId(), getCurrentStateData(), config, SystemClock.now());
                    break;
                case ERROR:
                    listener.onError(getCurrentNodeId(), getCurrentStateData(), e, config);
                    break;
            }
        } catch (Exception ex) {
            log.error("Error in listener", ex);
        }
    }
}
```

### 4.3 Observation 创建流程

#### 图级别 Observation

代码位置：`GraphObservationLifecycleListener.java:126`

```java
private void startGraphObservation(Map<String, Object> state) {
    String executionId = (String) state.get(GraphLifecycleListener.EXECUTION_ID_KEY);
    if (executionId == null) {
        return;
    }

    // 1. 创建 Observation（未启动）
    Observation graphObs = Observation.createNotStarted(
        "spring.ai.alibaba.graph.graph-execution",
        observationRegistry
    );

    // 2. 添加低基数 Tag（用于聚合查询）
    graphObs.lowCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.GRAPH_NAME.value(),
        "graph-execution"
    );

    // 3. 添加高基数 Tag（详细数据，会截断）
    String input = dumpState(state);
    graphObs.highCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.LANGFUSE_INPUT.value(),
        input
    );
    graphObs.highCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.GEN_AI_PROMPT.value(),
        input
    );

    // 4. 启动观测
    graphObs.start();

    // 5. 注册到全局缓存
    register(executionId, graphObs);
}
```

#### 节点级别 Observation

代码位置：`GraphObservationLifecycleListener.java:195`

```java
private void startNodeObservation(String nodeId, Map<String, Object> state) {
    GraphObservationContext ctx = getContext(state);
    if (ctx == null) {
        log.debug("No observation context found for node execution {}", nodeId);
        return;
    }

    // 1. 创建子级 Observation，并关联父级
    Observation nodeObservation = Observation.createNotStarted(
        "spring.ai.alibaba.graph.node." + nodeId,
        observationRegistry
    ).parentObservation(ctx.graphObservation);  // ← 建立父子关系

    // 2. 添加节点属性
    nodeObservation.lowCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.GRAPH_NODE_NAME.value(),
        nodeId
    );

    // 3. 记录输入状态
    String nodeInput = dumpState(state);
    nodeObservation.highCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.LANGFUSE_INPUT.value(),
        nodeInput
    );
    nodeObservation.highCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.GEN_AI_PROMPT.value(),
        nodeInput
    );

    // 4. 启动并打开 Scope（用于上下文传播）
    nodeObservation.start();
    Observation.Scope scope = nodeObservation.openScope();

    // 5. 保存到上下文
    ctx.nodeObservations.put(nodeId, nodeObservation);
    ctx.nodeScopes.put(nodeId, scope);
}
```

### 4.4 状态序列化

代码位置：`GraphObservationLifecycleListener.java:287`

```java
private String dumpState(Map<String, Object> state) {
    if (state == null || state.isEmpty()) {
        return "empty state";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Object> entry : state.entrySet()) {
        String key = entry.getKey();
        // 跳过内部字段和日志
        if (key.startsWith("_") || "logs".equals(key)) {
            continue;
        }
        Object value = entry.getValue();
        String valStr = String.valueOf(value);
        // 截断过长内容（超过 1000 字符）
        if (valStr.length() > 1000) {
            valStr = valStr.substring(0, 1000) + "... (truncated)";
        }
        sb.append(key).append("=").append(valStr).append("; ");
    }
    return sb.length() > 0 ? sb.toString() : "empty visible state";
}
```

### 4.5 父子关系建立

```
Graph Observation (父) - TraceId: abc123
    │
    ├─→ Node A Observation (子) - SpanId: def456, ParentSpanId: abc123
    │       │
    │       └─→ Span A (在追踪系统中)
    │
    ├─→ Node B Observation (子) - SpanId: ghi789, ParentSpanId: abc123
    │       │
    │       └─→ Span B (在追踪系统中)
    │
    └─→ Node C Observation (子) - SpanId: jkl012, ParentSpanId: abc123
            │
            └─→ Span C (在追踪系统中)
```

### 4.6 Scope 机制（上下文传播）

```java
// 打开 Scope - 将 Observation 放入 ThreadLocal
Observation.Scope scope = nodeObservation.openScope();

try {
    // 在这个作用域内，所有操作都会关联到这个 Observation
    // 包括子线程、异步操作等
    executeBusinessLogic();
} finally {
    // 关闭 Scope - 清理 ThreadLocal
    scope.close();
}
```

---

## 五、快速开始

### 5.1 最小配置

```java
// 1. 创建 ObservationRegistry
ObservationRegistry registry = ObservationRegistry.create();

// 2. 配置 StateGraph
StateGraph<Map<String, Object>> graph = StateGraph.builder()
    .observationRegistry(registry)  // 注入注册表
    .withLifecycleListener(
        new GraphObservationLifecycleListener(registry)  // 添加监听器
    )
    .addNode("agent1", state -> {
        // 业务逻辑
        return Map.of("result", "处理结果");
    })
    .addEdge("agent1", "agent2")
    .build();

// 3. 执行时自动追踪
graph.run(initialState).subscribe();
```

### 5.2 Spring Boot 配置

**添加依赖：**

```xml
<dependencies>
    <!-- Micrometer 核心 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- Prometheus 支持 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- OpenTelemetry 追踪支持 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- OTLP 导出 -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
</dependencies>
```

**配置 application.yml：**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
  observation:
    enabled: true
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces  # OTLP 收集器
  tracing:
    sampling:
      probability: 1.0  # 100% 采样
```

### 5.3 使用 CompileConfig 配置

```java
CompileConfig config = CompileConfig.builder()
    .observationRegistry(observationRegistry)
    .withLifecycleListener(
        new GraphObservationLifecycleListener(observationRegistry)
    )
    .build();

StateGraph<Map<String, Object>> graph = StateGraph.builder(config)
    .addNode("agent1", ...)
    .build();
```

---

## 六、查看观测数据

### 6.1 通过日志查看（最简单）

**配置 application.yml：**

```yaml
logging:
  level:
    io.micrometer.observation: DEBUG
    com.alibaba.cloud.ai.graph.observation: DEBUG
```

**日志输出示例：**

```
DEBUG o.m.observation.ObservationRegistry - Starting observation spring.ai.alibaba.graph.graph-execution
DEBUG o.m.observation.ObservationRegistry - Starting observation spring.ai.alibaba.graph.node.agent1
DEBUG o.m.observation.ObservationRegistry - Stopping observation spring.ai.alibaba.graph.node.agent1
DEBUG o.m.observation.ObservationRegistry - Stopping observation spring.ai.alibaba.graph.graph-execution
```

### 6.2 通过 Prometheus 查看

**访问 Prometheus 端点：**

```
http://localhost:8080/actuator/prometheus
```

**指标输出示例：**

```
# 图执行计数
spring_ai_alibaba_graph_success{graph_name="graph-execution",success="true",} 42.0

# 节点执行计数
spring_ai_alibaba_graph_node_success{graph_node_name="agent1",success="true",} 42.0
spring_ai_alibaba_graph_node_success{graph_node_name="agent2",success="true",} 38.0
spring_ai_alibaba_graph_node_success{graph_node_name="agent2",success="false",} 4.0

# 节点执行耗时
spring_ai_alibaba_graph_node_duration_seconds{graph_node_name="agent1",} 0.523
spring_ai_alibaba_graph_node_duration_seconds{graph_node_name="agent2",} 1.234
```

### 6.3 通过 Actuator 查看

**访问 Actuator 端点：**

```
# 查看所有指标
http://localhost:8080/actuator/metrics

# 查看特定指标
http://localhost:8080/actuator/metrics/spring.ai.alibaba.graph
http://localhost:8080/actuator/metrics/spring.ai.alibaba.graph.node
```

### 6.4 完整观测数据示例

```json
{
  "name": "spring.ai.alibaba.graph.node",
  "type": "graph_node",
  "tags": {
    "spring.ai.alibaba.kind": "graph_node",
    "spring.ai.alibaba.graph.node.name": "callWeatherAPI",
    "spring.ai.alibaba.graph.node.success": "true"
  },
  "attributes": {
    "node.before.state": "user_input=查天气; intent=QUERY_WEATHER;",
    "node.after.state": "temperature=5; weather=晴;",
    "gen_ai.prompt": "user_input=查天气; intent=QUERY_WEATHER;",
    "gen_ai.completion": "temperature=5; weather=晴;",
    "langfuse.observation.input": "user_input=查天气; intent=QUERY_WEATHER;",
    "langfuse.observation.output": "temperature=5; weather=晴;"
  },
  "timings": {
    "duration": "1.5s",
    "startTime": "2025-01-15T10:30:01Z",
    "endTime": "2025-01-15T10:30:02.5Z"
  },
  "tracing": {
    "traceId": "abc123",
    "spanId": "def456",
    "parentSpanId": "abc123"
  }
}
```

---

## 七、集成外部平台

### 7.1 Prometheus + Grafana

**架构图：**

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Spring AI   │────▶│ Prometheus  │────▶│  Grafana    │
│  Alibaba    │     │  (指标存储)  │     │  (可视化)   │
└─────────────┘     └─────────────┘     └─────────────┘
```

**配置步骤：**

1. **启动 Prometheus：**

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-ai-alibaba'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
```

2. **配置 Grafana 数据源：**

- 添加 Prometheus 数据源
- 导入或创建仪表板

3. **推荐的 Grafana 查询：**

```promql
# 图执行总次数
sum(spring_ai_alibaba_graph_success)

# 节点平均耗时
rate(spring_ai_alibaba_graph_node_duration_seconds_sum[5m]) /
rate(spring_ai_alibaba_graph_node_duration_seconds_count[5m])

# 节点成功率
sum(spring_ai_alibaba_graph_node_success{success="true"}) /
sum(spring_ai_alibaba_graph_node_success)
```

### 7.2 OpenTelemetry + Jaeger/Tempo

**架构图：**

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│ Spring AI   │────▶│ OTLP Collector   │────▶│ Jaeger/Tempo│
│  Alibaba    │     │ (收集器)          │     │ (追踪存储)  │
└─────────────┘     └──────────────────┘     └─────────────┘
```

**配置 application.yml：**

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

**在 Jaeger UI 中查看：**

- 访问 `http://localhost:16686`
- 搜索 TraceId 或服务名
- 查看完整的调用链路

### 7.3 Langfuse（AI 专用）

**配置：**

```yaml
langfuse:
  public-key: your-public-key
  secret-key: your-secret-key
  host: https://cloud.langfuse.com
```

**Langfuse Dashboard 显示内容：**

```
┌──────────────────────────────────────────────────────────┐
│ Trace: customer-service-bot (3.2s)                       │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  📊 Overview:                                            │
│  - Total Tokens: 1,234                                   │
│  - Total Cost: $0.012                                    │
│  - Success Rate: 95%                                     │
│                                                          │
│  🔍 Details:                                             │
│  ┌────────────────────────────────────────────────┐     │
│  │ classify (0.3s) ✅                             │     │
│  │ Input: user_message=我的订单什么时候发货？      │     │
│  │ Output: intent=ORDER_STATUS                    │     │
│  │ Tokens: 150 | Cost: $0.0015                    │     │
│  └────────────────────────────────────────────────┘     │
│  ┌────────────────────────────────────────────────┐     │
│  │ searchKB (1.5s) ✅                             │     │
│  │ Input: intent=ORDER_STATUS                     │     │
│  │ Output: status=shipped; eta=2025-01-16         │     │
│  │ Tokens: 800 | Cost: $0.008                     │     │
│  └────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

### 7.4 ELK / Loki

**配置 Logback 输出 JSON 格式日志：**

```xml
<!-- logback-spring.xml -->
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>

<root level="INFO">
    <appender-ref ref="JSON"/>
</root>
```

**日志输出示例：**

```json
{
  "timestamp": "2025-01-15T10:30:01.123Z",
  "level": "DEBUG",
  "logger": "io.micrometer.observation.ObservationRegistry",
  "message": "Starting observation",
  "context": {
    "name": "spring.ai.alibaba.graph.node.agent1",
    "kind": "graph_node",
    "tags": {
      "graph.node.name": "agent1",
      "graph.node.success": "true"
    },
    "attributes": {
      "langfuse.observation.input": "user_input=查天气;",
      "gen_ai.prompt": "user_input=查天气;"
    }
  },
  "tracing": {
    "traceId": "abc123",
    "spanId": "def456",
    "parentSpanId": "abc123"
  }
}
```

---

## 八、高级配置

### 8.1 自定义标签

```java
Observation.createNotStarted("my.operation", registry)
    .lowCardinalityKeyValue("custom.tag", "custom_value")  // 自定义低基数标签
    .highCardinalityKeyValue("custom.data", "详细数据")    // 自定义高基数标签
    .start();
```

### 8.2 自定义 Observation Convention

```java
public class CustomGraphObservationConvention implements GraphObservationConvention {

    @Override
    public String getName() {
        return "custom.graph.name";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(GraphObservationContext context) {
        return KeyValues.of(
            SpringAiAlibabaKind.GRAPH.getValue(),
            context.getGraphName()
        ).and("custom.tag", "custom.value");  // 添加自定义标签
    }
}
```

### 8.3 采样策略配置

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 只采样 10% 的请求
```

### 8.4 异步场景的 Scope 处理

```java
Observation.Scope scope = observation.openScope();
try {
    // 异步操作会自动继承上下文
    CompletableFuture.supplyAsync(() -> {
        // 这里的操作会关联到当前的 Observation
        return doSomething();
    });
} finally {
    scope.close();
}
```

---

## 九、最佳实践

### 9.1 环境区分

```yaml
# 开发环境：100% 采样，详细日志
management:
  tracing:
    sampling:
      probability: 1.0
logging:
  level:
    io.micrometer.observation: DEBUG

---
# 生产环境：10% 采样，INFO 日志
management:
  tracing:
    sampling:
      probability: 0.1
logging:
  level:
    io.micrometer.observation: INFO
```

### 9.2 敏感信息过滤

```java
// 在 state 中使用下划线前缀标记敏感字段
state.put("_api_key", "sk-xxx");  // 会被 dumpState 自动跳过
state.put("_user_token", "eyJ...");  // 会被 dumpState 自动跳过
```

### 9.3 性能优化建议

| 建议 | 说明 |
|------|------|
| 合理设置采样率 | 生产环境建议 10%-30% |
| 控制状态大小 | 避免在 state 中存储大对象 |
| 使用低基数标签 | 用于分组和聚合 |
| 高基数标签适量 | 避免基数爆炸 |

### 9.4 错误处理

```java
@Override
public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
    log.error("Error in graph/node {}: {}", nodeId, ex.getMessage());
    // 记录失败状态
    nodeObservation.lowCardinalityKeyValue(
        SpringAiAlibabaObservationMetricAttributes.GRAPH_NODE_SUCCESS.value(),
        "false"
    );
    nodeObservation.error(ex);
    nodeObservation.stop();
}
```

### 9.5 监控告警建议

```promql
# 告警规则示例

# 高错误率告警
sum(rate(spring_ai_alibaba_graph_node_success{success="false"}[5m])) /
sum(rate(spring_ai_alibaba_graph_node_success[5m])) > 0.05

# 慢查询告警
histogram_quantile(0.95,
  sum(rate(spring_ai_alibaba_graph_node_duration_seconds_bucket[5m])) by (le, graph_node_name)
) > 5
```

---

## 十、故障排查

### 10.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 指标未出现 | ObservationRegistry 未配置 | 检查是否注入了 registry |
| TraceId 不连续 | Scope 未正确关闭 | 确保使用 try-finally 关闭 Scope |
| 日志缺少追踪信息 | 日志格式未配置 | 配置 MDC 或 JSON 格式输出 |

### 10.2 调试技巧

**启用 DEBUG 日志：**

```yaml
logging:
  level:
    io.micrometer.observation: DEBUG
    com.alibaba.cloud.ai.graph.observation: DEBUG
```

**检查 Observation 状态：**

```java
ObservationRegistry registry = ...;
registry.getCurrentObservation();  // 查看当前 Observation
registry.getCurrentObservationScope();  // 查看当前 Scope
```

---

## 附录

### A. 相关文档

- [Micrometer 官方文档](https://micrometer.io/docs)
- [OpenTelemetry 规范](https://opentelemetry.io/docs/reference/specification/)
- [Spring Boot Actuator 指南](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Langfuse 文档](https://langfuse.com/docs)

### B. 代码位置索引

| 功能 | 文件路径 |
|------|----------|
| 观测类型枚举 | `spring-ai-alibaba-graph-core/.../observation/SpringAiAlibabaKind.java` |
| 图级别观测 | `spring-ai-alibaba-graph-core/.../observation/graph/` |
| 节点级别观测 | `spring-ai-alibaba-graph-core/.../observation/node/` |
| 边级别观测 | `spring-ai-alibaba-graph-core/.../observation/edge/` |
| 生命周期监听器 | `spring-ai-alibaba-graph-core/.../observation/GraphObservationLifecycleListener.java` |
| 指标生成器 | `spring-ai-alibaba-graph-core/.../observation/GraphMetricsGenerator.java` |
| 指标名称定义 | `spring-ai-alibaba-graph-core/.../observation/metric/SpringAiAlibabaObservationMetricNames.java` |
| 指标属性定义 | `spring-ai-alibaba-graph-core/.../observation/metric/SpringAiAlibabaObservationMetricAttributes.java` |

### C. 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0.0 | 2025-01-15 | 初始版本 |

---

*本文档由 Spring AI Alibaba 团队维护*
*最后更新：2025-01-15*
