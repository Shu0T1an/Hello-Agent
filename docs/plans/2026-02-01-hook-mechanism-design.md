# Hook 机制设计文档

**日期**: 2026-02-01
**作者**: Hello-Agent 项目
**版本**: 1.0

---

## 1. 概述

### 1.1 目标

在 Hello-Agent 项目中引入 Hook 机制，实现：
- **人工审批/干预**：支持在特定操作前要求人工审批
- **通用扩展机制**：提供可扩展的 Hook 系统

### 1.2 设计原则

- **节点模式**：Hook 被包装成节点插入图中，成为图结构的一部分
- **精简实现**：第一版仅实现 `BEFORE_MODEL` 和 `AFTER_MODEL` 两个位置
- **框架优先**：先搭建核心框架，具体实现后续添加

### 1.3 参考设计

基于 Spring AI Alibaba 的 Hook 机制设计，详见 `docs/HOOK_MECHANISM_COMPLETE_GUIDE.md`。

---

## 2. 核心接口设计

### 2.1 Hook 基础接口

```java
// 位置: graph-core/src/main/java/cn/ts/graph/hook/Hook.java
public interface Hook {
    String getName();
    void setAgentName(String agentName);
    String getAgentName();

    default HookPosition[] getHookPositions() {
        HookPositions annotation = this.getClass()
            .getAnnotation(HookPositions.class);
        if (annotation != null) {
            return annotation.value();
        }
        if (this instanceof ModelHook) {
            return new HookPosition[]{
                HookPosition.BEFORE_MODEL,
                HookPosition.AFTER_MODEL
            };
        }
        return new HookPosition[0];
    }

    default List<JumpTo> canJumpTo() {
        return List.of();
    }

    static String getFullHookName(Hook hook) {
        return "__hook_" + hook.getName();
    }
}
```

### 2.2 HookPosition 枚举

```java
// 位置: graph-core/src/main/java/cn/ts/graph/hook/HookPosition.java
public enum HookPosition {
    BEFORE_MODEL,
    AFTER_MODEL
}
```

### 2.3 ModelHook 抽象类

```java
// 位置: graph-core/src/main/java/cn/ts/graph/hook/ModelHook.java
public abstract class ModelHook implements Hook {
    private String agentName;

    public CompletableFuture<Map<String, Object>> beforeModel(
        State state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    public CompletableFuture<Map<String, Object>> afterModel(
        State state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

### 2.4 HookPositions 注解

```java
// 位置: graph-core/src/main/java/cn/ts/graph/hook/HookPositions.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HookPositions {
    HookPosition[] value();
}
```

---

## 3. 中断机制

### 3.1 InterruptableAction 接口

```java
// 位置: graph-core/src/main/java/cn/ts/graph/node/InterruptableAction.java
public interface InterruptableAction extends AsyncNodeAction {
    Optional<InterruptionMetadata> interrupt(
        String nodeId,
        State state,
        RunnableConfig config
    );
}
```

### 3.2 InterruptionMetadata 类

```java
// 位置: graph-core/src/main/java/cn/ts/graph/checkpoint/InterruptionMetadata.java
public class InterruptionMetadata {
    private final String nodeId;
    private final State state;
    private final List<ToolFeedback> toolFeedbacks;
    private final Map<String, Object> customData;
    private final Instant timestamp;

    public static Builder builder(String nodeId, State state) {
        return new Builder(nodeId, state);
    }
}
```

### 3.3 JumpTo 枚举

```java
// 位置: graph-core/src/main/java/cn/ts/graph/hook/JumpTo.java
public enum JumpTo {
    END,
    MODEL,
    TOOL
}
```

---

## 4. ReactAgent 集成

### 4.1 Builder 添加 hooks 支持

```java
// 位置: agent-core/src/main/java/cn/ts/agent/core/ReactAgent.java
public class ReactAgent implements Agent {
    private final List<Hook> hooks;

    public static class Builder {
        private List<Hook> hooks = List.of();

        public Builder hooks(List<Hook> hooks) {
            this.hooks = hooks != null ? hooks : List.of();
            return this;
        }
    }
}
```

### 4.2 initGraph 修改

在 `initGraph()` 方法中：
1. 添加核心节点后，分类 Hook
2. 为每个 Hook 创建对应的图节点
3. 设置边连接

### 4.3 图结构

```
START
  ↓
Hook1.before (如果存在)
  ↓
Hook2.before (如果存在)
  ↓
AGENT_MODEL
  ↓
Hook2.after (逆序)
  ↓
Hook1.after (逆序)
  ↓
AGENT_TOOL → 循环回 MODEL
  ↓
END
```

---

## 5. GraphRunner 集成

### 5.1 中断检测

在节点执行前检查是否实现 `InterruptableAction`：
- 如果是，调用 `interrupt()` 方法
- 如果返回中断元数据，保存检查点并返回

### 5.2 JumpTo 处理

在确定下一个节点时：
1. 检查配置中是否有 JumpTo 标记
2. 如果有，跳转到指定位置
3. 否则使用正常边路由

### 5.3 状态更新

Hook 返回的状态更新：
1. 提取 `jump_to` 标记（如果有）
2. 使用注册的合并策略合并其他更新

---

## 6. 内置 Hook

### 6.1 HumanInTheLoopHook

```java
// 位置: agent-core/src/main/java/cn/ts/agent/hook/HumanInTheLoopHook.java
@HookPositions(HookPosition.AFTER_MODEL)
public class HumanInTheLoopHook extends ModelHook
    implements InterruptableAction {

    private final Map<String, ToolConfig> approvalTools;

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        State state, RunnableConfig config) {
        // 处理用户反馈
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(
        String nodeId, State state, RunnableConfig config) {
        // 检查是否需要中断
    }

    public static Builder builder() {
        return new Builder();
    }
}
```

### 6.2 辅助类

```java
// 位置: agent-core/src/main/java/cn/ts/agent/hook/ToolFeedback.java
public class ToolFeedback {
    private final String id;
    private final String name;
    private final Map<String, Object> arguments;
    private final String description;
    private FeedbackResult result;
}

// 位置: agent-core/src/main/java/cn/ts/agent/hook/ToolConfig.java
public record ToolConfig(String name, String description) {}

// 位置: agent-core/src/main/java/cn/ts/agent/hook/FeedbackResult.java
public enum FeedbackResult {
    APPROVED,
    REJECTED,
    MODIFIED
}
```

---

## 7. 使用示例

### 7.1 创建自定义 Hook

```java
@HookPositions(HookPosition.BEFORE_MODEL)
public class LoggingHook extends ModelHook {
    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
        State state, RunnableConfig config) {
        state.value("messages").ifPresent(messages -> {
            log.info("Before LLM call, message count: {}",
                ((List<?>) messages).size());
        });
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public String getName() {
        return "LoggingHook";
    }
}
```

### 7.2 使用 HumanInTheLoopHook

```java
ReactAgent agent = ReactAgent.builder()
    .chatModel(chatModel)
    .tools(List.of(deleteFileTool, sendEmailTool))
    .hooks(List.of(
        new LoggingHook(),
        HumanInTheLoopHook.builder()
            .approvalOn("deleteFile", "删除文件，不可逆操作")
            .approvalOn("sendEmail", "发送邮件")
            .build()
    ))
    .checkpointer(checkpointManager)
    .build();
```

---

## 8. 文件结构

```
graph-core/src/main/java/cn/ts/graph/
├── hook/
│   ├── Hook.java                    # Hook 基础接口
│   ├── HookPosition.java            # 位置枚举
│   ├── HookPositions.java           # 位置注解
│   ├── JumpTo.java                  # 跳转枚举
│   └── ModelHook.java               # Model 级 Hook 抽象类
│
├── node/
│   ├── NodeAction.java              # 已有
│   ├── AsyncNodeAction.java         # 已有
│   └── InterruptableAction.java     # 新增：可中断动作接口
│
├── checkpoint/
│   ├── CheckpointManager.java       # 已有
│   ├── StateSnapshot.java           # 已有
│   └── InterruptionMetadata.java    # 新增：中断元数据
│
└── GraphRunner.java                 # 修改：集成中断检测

agent-core/src/main/java/cn/ts/agent/
├── hook/
│   ├── HumanInTheLoopHook.java      # 新增：人工审批 Hook
│   ├── ToolFeedback.java            # 新增：工具反馈
│   ├── ToolConfig.java              # 新增：工具配置
│   └── FeedbackResult.java          # 新增：反馈结果枚举
│
└── core/
    └── ReactAgent.java              # 修改：集成 Hook 支持
```

---

## 9. 实施步骤

### 阶段 1：基础接口 (graph-core)
- [ ] 创建 `Hook.java`
- [ ] 创建 `HookPosition.java`
- [ ] 创建 `HookPositions.java`
- [ ] 创建 `ModelHook.java`
- [ ] 创建 `InterruptableAction.java`
- [ ] 创建 `JumpTo.java`

### 阶段 2：中断元数据 (graph-core)
- [ ] 创建 `InterruptionMetadata.java`
- [ ] 扩展 `CheckpointMetadata` 支持中断场景

### 阶段 3：执行器集成 (graph-core)
- [ ] 修改 `GraphRunner.java` 添加中断检测逻辑
- [ ] 添加 JumpTo 路由处理
- [ ] 添加 Hook 状态更新处理

### 阶段 4：Agent 集成 (agent-core)
- [ ] 修改 `ReactAgent.java` 添加 hooks 支持
- [ ] 实现 Hook 节点创建和边连接逻辑
- [ ] 实现 Hook 分类和过滤

### 阶段 5：内置 Hook (agent-core)
- [ ] 创建 `HumanInTheLoopHook.java`
- [ ] 创建 `ToolFeedback.java`
- [ ] 创建 `ToolConfig.java`
- [ ] 创建 `FeedbackResult.java`

### 阶段 6：测试
- [ ] 编写单元测试
- [ ] 编写集成测试
- [ ] 编写端到端示例

---

## 10. 测试策略

### 10.1 单元测试

- 测试 Hook 接口实现
- 测试中断检测逻辑
- 测试状态更新处理

### 10.2 集成测试

- 测试 Agent 与 Hook 的集成
- 测试完整的中断-恢复流程
- 测试 JumpTo 流程控制

---

**文档版本**: 1.0
**最后更新**: 2026-02-01
