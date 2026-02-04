# Human In The Loop (HITL) 设计报告

## 1. 概述

Human In The Loop (HITL) 是 Spring AI Alibaba 框架中的一种**人工干预机制**，允许在 AI Agent 执行过程中暂停并等待人类审批后继续执行。这是通过 **Hook 机制** 和 **可中断节点 (InterruptableAction)** 实现的。

### 核心目的

- **安全性**: 在执行敏感操作（如删除文件、发送邮件、资金转账）前获得人工确认
- **控制性**: 允许人类修改 AI 生成的工具参数
- **可观测性**: 提供对 AI 决策过程的透明度

---

## 2. 架构设计

### 2.1 核心组件

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         HITL 架构组件                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Hook 机制层                                  │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │ AgentHook    │  │ ModelHook    │  │ InterruptionHook│         │   │
│  │  │ (BEFORE_     │  │ (BEFORE_     │  │ (特殊中断Hook) │          │   │
│  │  │  AGENT/      │  │  MODEL/      │  │               │          │   │
│  │  │  AFTER_      │  │  AFTER_MODEL)│  │               │          │   │
│  │  │  AGENT)      │  │              │  │               │          │   │
│  │  └──────────────┘  └──────┬───────┘  └──────────────┘          │   │
│  │                            │                                       │   │
│  │                  ┌─────────▼──────────┐                           │   │
│  │                  │ HumanInTheLoopHook │ ← AFTER_MODEL 位置       │   │
│  │                  │ (双重角色:          │                           │   │
│  │                  │  1. Hook处理反馈)   │                           │   │
│  │                  │  2. 可中断节点)     │                           │   │
│  │                  └─────────┬──────────┘                           │   │
│  └────────────────────────────┼─────────────────────────────────────┘   │
│                               │                                         │
│  ┌────────────────────────────┼─────────────────────────────────────┐   │
│  │                    Graph 执行层             │   │
│  │                              │                                     │   │
│  │  ┌───────────────────────────▼──────────────────────────────┐    │   │
│  │  │                    NodeExecutor                          │    │   │
│  │  │  ┌──────────────────────────────────────────────────┐   │    │   │
│  │  │  │ if (action instanceof InterruptableAction) {     │   │    │   │
│  │  │  │     Optional<InterruptionMetadata> metadata =     │   │    │   │
│  │  │  │         action.interrupt(...);                    │   │    │   │
│  │  │  │     if (metadata.isPresent()) {                  │   │    │   │
│  │  │  │         return Flux.just(GraphResponse.done(     │   │    │   │
│  │  │  │             InterruptionMetadata));  ← 中断!      │   │    │   │
│  │  │  │     }                                            │   │    │   │
│  │  │  │ }                                                 │   │    │   │
│  │  │  └──────────────────────────────────────────────────┘   │    │   │
│  │  └──────────────────────────┬───────────────────────────────┘    │   │
│  └─────────────────────────────┼─────────────────────────────────────┘   │
│                                │                                           │
│  ┌─────────────────────────────▼─────────────────────────────────────┐   │
│  │                    状态持久化层                                    │   │
│  │  ┌──────────────────────────────────────────────────────────┐    │   │
│  │  │  MemorySaver, FileSystemSaver, RedisSaver,               │    │   │
│  │  │  MongoDBSaver, DatabaseSaver...                          │    │   │
│  │  │  (保存中断时的状态，支持恢复)                             │    │   │
│  │  └──────────────────────────────────────────────────────────┘    │   │
│  └───────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 核心类与接口

| 类/接口 | 位置 | 作用 |
|---------|------|------|
| `Hook` | `agent/hook/Hook.java` | Hook 基础接口 |
| `HookPosition` | `agent/hook/HookPosition.java` | 定义 4 个插入位置枚举 |
| `ModelHook` | `agent/hook/ModelHook.java` | 模型级 Hook 抽象类 |
| `InterruptableAction` | `action/InterruptableAction.java` | 可中断节点接口 |
| `InterruptionMetadata` | `action/InterruptionMetadata.java` | 中断元数据（包含待审批工具） |
| `HumanInTheLoopHook` | `agent/hook/hip/HumanInTheLoopHook.java` | HITL 核心实现 |

---

## 3. Hook 机制详解

### 3.1 Hook 的四种插入位置

```java
// 位置: spring-ai-alibaba-agent-framework/.../hook/HookPosition.java
public enum HookPosition {
    BEFORE_AGENT,   // Agent 开始前
    AFTER_AGENT,    // Agent 结束后
    BEFORE_MODEL,   // LLM 调用前
    AFTER_MODEL     // LLM 调用后 ← HITL 使用此位置
}
```

### 3.2 Hook 在图中的节点转换

位置: `spring-ai-alibaba-agent-framework/.../agent/ReactAgent.java:286-334`

```java
// 1. 根据注解分类 Hook
List<Hook> afterModelHooks = filterHooksByPosition(hooks, HookPosition.AFTER_MODEL);

// 2. 为每个 Hook 创建图节点
for (Hook hook : afterModelHooks) {
    if (hook instanceof HumanInTheLoopHook humanInTheLoopHook) {
        // HITL 直接作为节点（实现了 InterruptableAction）
        graph.addNode(Hook.getFullHookName(hook) + ".afterModel", humanInTheLoopHook);
    } else {
        // 其他 Hook 包装为节点
        graph.addNode(Hook.getFullHookName(hook) + ".afterModel", modelHook::afterModel);
    }
}
```

### 3.3 图执行流程

```
        START
           ↓
    ┌──────────────────────────────────────┐
    │  beforeAgent hooks                   │
    │  (ShellTool: 初始化交互会话)          │
    └──────────────────────────────────────┘
           ↓
    ┌──────────────────────────────────────┐
    │  beforeModel hooks                   │
    │  (InterruptionHook: 前置检查)         │
    └──────────────────────────────────────┘
           ↓
    ┌──────────────────────────────────────┐
    │  AGENT_MODEL 节点                    │
    │  → LLM 调用，返回 AssistantMessage    │
    │     (包含 toolCalls)                  │
    └──────────────────────────────────────┘
           ↓
    ┌──────────────────────────────────────┐
    │  HumanInTheLoopHook                  │
    │  ┌────────────────────────────────┐  │
    │  │ Step 1: afterModel()          │  │
    │  │  - 检查 metadata 中是否有反馈   │  │
    │  │  - 有反馈 → 处理并继续          │  │
    │  │  - 无反馈 → 返回空              │  │
    │  └────────────────────────────────┘  │
    │           ↓                           │
    │  ┌────────────────────────────────┐  │
    │  │ Step 2: interrupt() [执行器]  │  │
    │  │  - 检查是否有待审批工具         │  │
    │  │  - 有 → 返回 InterruptionMetadata│ │
    │  │  - 无 → 继续执行                │  │
    │  └────────────────────────────────┘  │
    └──────────────────────────────────────┘
           ↓ (如果没有中断)
    ┌──────────────────────────────────────┐
    │  AGENT_TOOL 节点                     │
    │  → 执行已批准的工具                   │
    └──────────────────────────────────────┘
           ↓ (循环回 AGENT_MODEL)
    ┌──────────────────────────────────────┐
    │  afterAgent hooks                    │
    │  (ShellTool: 清理交互会话)            │
    └──────────────────────────────────────┘
           ↓
         END
```

---

## 4. 中断机制详解

### 4.1 InterruptableAction 接口

```java
// 位置: spring-ai-alibaba-graph-core/.../action/InterruptableAction.java
public interface InterruptableAction extends AsyncNodeActionWithConfig {
    /**
     * 在节点执行前检查是否需要中断
     * @return Optional<InterruptionMetadata> - 非空表示需要中断
     */
    Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config);
}
```

### 4.2 NodeExecutor 中的中断检测

位置: `spring-ai-alibaba-graph-core/.../executor/NodeExecutor.java:104-118`

```java
// 执行节点前检查是否可中断
if (action instanceof InterruptableAction) {
    // 从反馈中合并状态更新（如果有）
    context.getConfig().metadata(RunnableConfig.STATE_UPDATE_METADATA_KEY).ifPresent(...);

    // 调用 interrupt() 方法
    Optional<InterruptionMetadata> interruptMetadata = ((InterruptableAction) action)
        .interrupt(currentNodeId, context.cloneState(context.getCurrentState()), context.getConfig());

    // 如果返回中断元数据，立即停止执行并返回
    if (interruptMetadata.isPresent()) {
        resultValue.set(interruptMetadata.get());
        return Flux.just(GraphResponse.done(interruptMetadata.get()));
    }
}
```

**关键点**:
- 中断检测发生在节点执行**之前**
- 一旦返回 `InterruptionMetadata`，执行流立即停止
- 状态通过 Checkpointer 持久化保存

---

## 5. HumanInTheLoopHook 双角色机制

### 5.1 类声明

```java
// 位置: spring-ai-alibaba-agent-framework/.../hook/hip/HumanInTheLoopHook.java:47
@HookPositions(HookPosition.AFTER_MODEL)
public class HumanInTheLoopHook extends ModelHook
    implements AsyncNodeActionWithConfig, InterruptableAction {
```

**实现的三个接口**:

| 接口 | 方法 | 作用 |
|------|------|------|
| `ModelHook` | `afterModel()` | 处理人类反馈，更新工具调用 |
| `AsyncNodeActionWithConfig` | `apply()` | 作为异步节点执行 |
| `InterruptableAction` | `interrupt()` | 检查是否需要中断 |

### 5.2 角色 1: Hook - 处理反馈

```java
// HumanInTheLoopHook.java:67-145
public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
    // 1. 从配置中获取人工反馈
    Optional<InterruptionMetadata> feedback = config.getMetadataAndRemove(
        RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY,
        new TypeRef<InterruptionMetadata>() {}
    );

    if (feedback.isEmpty()) {
        return CompletableFuture.completedFuture(Map.of()); // 无反馈，继续
    }

    // 2. 处理每个工具调用
    List<ToolCall> newToolCalls = new ArrayList<>();
    List<ToolResponse> responses = new ArrayList<>();

    for (ToolCall toolCall : assistantMessage.getToolCalls()) {
        ToolFeedback toolFeedback = findFeedback(toolCall);

        if (toolFeedback != null) {
            switch (toolFeedback.getResult()) {
                case APPROVED:
                    newToolCalls.add(toolCall); // 保持原样
                    break;
                case EDITED:
                    // 使用修改后的参数
                    newToolCalls.add(new ToolCall(..., toolFeedback.getArguments()));
                    break;
                case REJECTED:
                    // 添加拒绝响应消息
                    responses.add(new ToolResponse(
                        toolCall.id(),
                        "Tool call rejected by human. Reason: " + toolFeedback.getDescription()
                    ));
                    break;
            }
        } else {
            // 无需审批的工具，自动批准
            newToolCalls.add(toolCall);
        }
    }

    // 3. 返回更新后的消息
    return CompletableFuture.completedFuture(Map.of("messages", newMessages));
}
```

### 5.3 角色 2: 可中断节点 - 触发中断

```java
// HumanInTheLoopHook.java:148-169
public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
    // 1. 获取最后的 AssistantMessage
    AssistantMessage lastMessage = getLastAssistantMessage(state);
    if (lastMessage == null || !lastMessage.hasToolCalls()) {
        return Optional.empty(); // 无工具调用，无需中断
    }

    // 2. 检查是否已有反馈
    Optional<Object> feedback = config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY);
    if (feedback.isPresent()) {
        // 验证反馈是否有效
        if (validateFeedback((InterruptionMetadata) feedback.get(), lastMessage.getToolCalls())) {
            return Optional.empty(); // 反馈有效，继续执行
        }
        // 反馈无效，重新构建中断元数据
        return buildInterruptionMetadata(state, lastMessage);
    }

    // 3. 无反馈，构建中断元数据
    return buildInterruptionMetadata(state, lastMessage);
}

private Optional<InterruptionMetadata> buildInterruptionMetadata(OverAllState state, AssistantMessage lastMessage) {
    InterruptionMetadata.Builder builder = InterruptionMetadata.builder(...);
    boolean needsInterruption = false;

    for (ToolCall toolCall : lastMessage.getToolCalls()) {
        if (approvalOn.containsKey(toolCall.name())) {
            // 需要审批的工具
            ToolConfig config = approvalOn.get(toolCall.name());
            builder.addToolFeedback(ToolFeedback.builder()
                .id(toolCall.id())
                .name(toolCall.name())
                .arguments(toolCall.arguments())
                .description(config.getDescription())
                .build());
            needsInterruption = true;
        } else {
            // 自动批准的工具
            builder.addToolsAutomaticallyApproved(toolCall);
        }
    }

    return needsInterruption ? Optional.of(builder.build()) : Optional.empty();
}
```

---

## 6. 完整执行流程时序图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Human In The Loop 完整执行流程                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  第一阶段: 首次执行 (触发中断)                                         │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                       │   │
│  │  用户发起请求                                                          │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 1. ReactAgent.invoke(threadId, config)                      │     │   │
│  │  │    - config 中无 HUMAN_FEEDBACK_METADATA_KEY                 │     │   │
│  │  │    - threadId: "thread-123"                                  │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 2. beforeAgent hooks (可选)                                  │     │   │
│  │  │    - ShellToolHook 初始化会话                                 │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 3. AGENT_MODEL 节点执行                                      │     │   │
│  │  │    - LLM 返回 AssistantMessage                               │     │   │
│  │  │    - 包含 toolCalls:                                         │     │   │
│  │  │      • deleteFile(name="important.txt")  ← 需要审批          │     │   │
│  │  │      • readFile(name="config.txt")      ← 自动批准           │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 4. HumanInTheLoopHook.afterModel()                          │     │   │
│  │  │    - 检查 config.metadata: 无反馈                            │     │   │
│  │  │    - 返回 Map.of() (空更新)                                  │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 5. NodeExecutor.executeNode()                               │     │   │
│  │  │    - 检测到下一个节点是 InterruptableAction                   │     │   │
│  │  │    - 调用 interrupt() 方法                                   │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 6. HumanInTheLoopHook.interrupt()                           │     │   │
│  │  │    - getLastAssistantMessage() 获取工具调用                  │     │   │
│  │  │    - 检查 approvalOn: "deleteFile" 需要审批                  │     │   │
│  │  │    - 构建 InterruptionMetadata:                              │     │   │
│  │  │      {                                                        │     │   │
│  │  │        toolFeedbacks: [                                      │     │   │
│  │  │          {                                                   │     │   │
│  │  │            id: "call_123",                                   │     │   │
│  │  │            name: "deleteFile",                               │     │   │
│  │  │            arguments: "{\"name\":\"important.txt\"}",         │     │   │
│  │  │            description: "AI is requesting to delete file..." │     │   │
│  │  │          }                                                   │     │   │
│  │  │        ],                                                    │     │   │
│  │  │        toolsAutomaticallyApproved: [readFile...]             │     │   │
│  │  │      }                                                        │     │   │
│  │  │    - 返回 Optional.of(metadata)                              │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 7. NodeExecutor 检测到中断                                   │     │   │
│  │  │    - resultValue.set(interruptionMetadata)                  │     │   │
│  │  │    - return Flux.just(GraphResponse.done(metadata))         │     │   │
│  │  │    - 状态通过 Checkpointer 保存                               │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 8. 返回 InterruptionMetadata 给调用者                        │     │   │
│  │  │    - 包含待审批的工具信息                                     │     │   │
│  │  │    - 线程状态已保存，可稍后恢复                               │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  第二阶段: 人工审批 (外部系统)                                         │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 9. 系统展示审批界面给用户                                     │     │   │
│  │  │    ┌─────────────────────────────────────────────────────┐   │     │   │
│  │  │    │ AI 请求执行以下操作，请审批:                          │   │     │   │
│  │  │    │                                                     │   │     │   │
│  │  │    │ ☐ deleteFile                                        │   │     │   │
│  │  │    │    描述: 删除重要文件                                │   │     │   │
│  │  │    │    参数: {"name":"important.txt"}                   │   │     │   │
│  │  │    │                                                     │   │     │   │
│  │  │    │ [批准] [拒绝] [编辑参数]                             │   │     │   │
│  │  │    └─────────────────────────────────────────────────────┘   │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  用户选择操作:                                                         │   │
│  │  - APPROVED: 批准执行                                                 │   │
│  │  - REJECTED: 拒绝执行 (提供原因)                                      │   │
│  │  - EDITED: 修改参数后执行 (如 name="backup.txt")                       │   │
│  │                                                                             │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  第三阶段: 恢复执行 (处理反馈)                                         │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 10. ReactAgent.invoke(threadId, configWithFeedback)         │     │   │
│  │  │     - threadId: "thread-123" (相同，恢复线程)                │     │   │
│  │  │     - config.addMetadata(                                   │     │   │
│  │  │         HUMAN_FEEDBACK_METADATA_KEY,                        │     │   │
│  │  │         InterruptionMetadata {                              │     │   │
│  │  │           toolFeedbacks: [{                                 │     │   │
│  │  │             id: "call_123",                                 │     │   │
│  │  │             name: "deleteFile",                             │     │   │
│  │  │             result: REJECTED,                               │     │   │
│  │  │             description: "文件太重要，不能删除"               │     │   │
│  │  │           }]                                                │     │   │
│  │  │         })                                                  │     │   │
│  │  │     - Checkpointer 恢复之前保存的状态                        │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 11. 从保存的节点继续执行 (HumanInTheLoopHook)                │     │   │
│  │  │     - 调用 afterModel(state, config)                        │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 12. HumanInTheLoopHook.afterModel() 处理反馈                │     │   │
│  │  │     - 从 config 中获取反馈                                   │     │   │
│  │  │     - 处理 REJECTED:                                         │     │   │
│  │  │       • 创建 ToolResponseMessage                            │     │   │
│  │  │       • 内容: "Tool call rejected by human. Reason: ..."    │     │   │
│  │  │     - 返回更新的 messages:                                   │     │   │
│  │  │       {                                                      │     │   │
│  │  │         messages: [                                          │     │   │
│  │  │           AssistantMessage (移除旧的 toolCalls),             │     │   │
│  │  │           ToolResponseMessage (拒绝响应),                    │     │   │
│  │  │           RemoveByHash(旧消息)                               │     │   │
│  │  │         ]                                                    │     │   │
│  │  │       }                                                      │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 13. 状态更新后继续执行                                       │     │   │
│  │  │     - 合并 ToolResponseMessage 到状态                        │     │   │
│  │  │     - 下次 LLM 调用会看到拒绝响应                            │     │   │
│  │  │     - LLM 可能会选择其他工具或修改操作                       │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │     ↓                                                                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │ 14. 继续循环或结束                                           │     │   │
│  │  │     - 如果有其他工具调用，可能再次触发中断                   │     │   │
│  │  │     - 最终完成并返回结果                                     │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. InterruptionMetadata 数据结构

```java
// 位置: spring-ai-alibaba-graph-core/.../action/InterruptionMetadata.java
public final class InterruptionMetadata extends NodeOutput {

    // 待审批的工具反馈列表
    private List<ToolFeedback> toolFeedbacks;

    // 自动批准的工具列表
    private List<ToolCall> toolsAutomaticallyApproved;

    // 自定义元数据
    private final Map<String, Object> metadata;

    // 工具反馈
    public static class ToolFeedback {
        String id;                  // 工具调用 ID
        String name;                // 工具名称
        String arguments;           // 工具参数 (JSON 字符串)
        FeedbackResult result;      // 审批结果: APPROVED/REJECTED/EDITED
        String description;         // 描述/原因
    }

    public enum FeedbackResult {
        APPROVED,   // 批准 - 按原参数执行
        REJECTED,   // 拒绝 - 不执行，返回拒绝原因
        EDITED      // 编辑 - 使用修改后的参数执行
    }
}
```

---

## 8. 配置与使用

### 8.1 创建 HITL Hook

```java
HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
    .approvalOn("deleteFile", "删除文件，此操作不可逆")
    .approvalOn("sendEmail", "发送邮件")
    .approvalOn("transferFunds", "资金转账")
    .build();
```

### 8.2 添加到 Agent

```java
ReactAgent agent = ReactAgent.builder()
    .tools(List.of(deleteFileTool, sendEmailTool, readFileTool))
    .hooks(List.of(hitlHook))
    .build();
```

### 8.3 首次调用 (触发中断)

```java
RunnableConfig config = RunnableConfig.builder()
    .threadId("thread-123")
    .checkpointer(memorySaver)  // 必须配置状态保存
    .build();

AppConfig appConfig = AppConfig.builder().build();

Flux<GraphResponse<NodeOutput>> result = agent.invoke("请删除重要文件", appConfig, config);

// 订阅结果
result.subscribe(response -> {
    if (response.isDone()) {
        Object value = response.resultValue().orElse(null);
        if (value instanceof InterruptionMetadata metadata) {
            // 显示审批界面
            showApprovalUI(metadata);
        }
    }
});
```

### 8.4 处理审批后恢复

```java
// 用户拒绝删除文件
InterruptionMetadata feedback = InterruptionMetadata.builder()
    .toolFeedbacks(List.of(
        ToolFeedback.builder()
            .id("call_123")
            .name("deleteFile")
            .result(FeedbackResult.REJECTED)
            .description("文件太重要，不能删除")
            .build()
    ))
    .build();

// 使用相同 threadId 恢复执行
RunnableConfig resumeConfig = RunnableConfig.builder()
    .threadId("thread-123")  // 相同 threadId
    .checkpointer(memorySaver)
    .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedback)
    .build();

agent.invoke(null, appConfig, resumeConfig).subscribe(...);
```

---

## 9. 关键设计点

### 9.1 非阻塞式中断

**重要**: HITL **不是**真正的"等待输入"机制，而是**检查-返回-恢复**模式:

```
┌────────────────────────────────────────────────────────────────┐
│  传统等待模式 (不使用)                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ 执行到中断点  │ →  │ 阻塞等待输入  │ →  │ 收到输入继续  │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│       ↑                                         ↑               │
│       └────────────────── 阻塞线程 ──────────────┘              │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  HITL 使用的中断-返回-恢复模式                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ 执行到中断点  │ →  │ 立即返回元数据│ →  │ 保存状态退出  │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                         ↓       │
│                                   ┌──────────────┐    │       │
│                                   │ 外部收集输入  │    │       │
│                                   └──────────────┘    │       │
│                                            ↓           │       │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │ 重新调用     │ ←  │ 加载保存状态  │ ←  │ 用户完成输入  │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
└────────────────────────────────────────────────────────────────┘
```

### 9.2 状态持久化依赖

HITL **必须**配合 Checkpointer 使用:

| Checkpointer 类型 | 适用场景 |
|------------------|----------|
| MemorySaver | 单机测试 |
| FileSystemSaver | 单机持久化 |
| RedisSaver | 分布式环境 |
| MongoDBSaver | 分布式环境 |
| DatabaseSaver | 企业应用 |

### 9.3 线程隔离

```java
String threadId = UUID.randomUUID().toString();  // 每个会话唯一 ID

// 首次调用
agent.invoke(query, config.withThreadId(threadId));

// 恢复时使用相同 threadId
agent.invoke(null, config.withThreadId(threadId).withMetadata(feedback));
```

### 9.4 元数据传递通道

```java
// RunnableConfig 中定义的元数据键
String HUMAN_FEEDBACK_METADATA_KEY = "__human_feedback__";   // 反馈数据
String STATE_UPDATE_METADATA_KEY = "__state_update__";       // 状态更新
```

---

## 10. 反馈处理详解

### 10.1 APPROVED (批准)

```java
// 原始工具调用
ToolCall original = new ToolCall("call_123", "function", "deleteFile", '{"name":"file.txt"}');

// 用户批准
ToolFeedback feedback = ToolFeedback.builder()
    .result(FeedbackResult.APPROVED)
    .build();

// 处理: 保持原样
newToolCalls.add(original);  // 原始调用直接加入
```

**结果**: 工具按原始参数执行

### 10.2 REJECTED (拒绝)

```java
// 用户拒绝
ToolFeedback feedback = ToolFeedback.builder()
    .result(FeedbackResult.REJECTED)
    .description("文件不能删除")
    .build();

// 处理: 创建拒绝响应
ToolResponse response = new ToolResponse(
    "call_123",
    "deleteFile",
    "Tool call rejected by human. Reason: 文件不能删除"
);
```

**结果**:
- 工具不执行
- LLM 收到拒绝响应，可选择其他方案

### 10.3 EDITED (编辑)

```java
// 用户修改参数
ToolFeedback feedback = ToolFeedback.builder()
    .result(FeedbackResult.EDITED)
    .arguments('{"name":"backup.txt"}')  // 修改后的参数
    .build();

// 处理: 创建新工具调用
ToolCall edited = new ToolCall(
    "call_123",           // 保持相同 ID
    "function",
    "deleteFile",
    '{"name":"backup.txt"}'  // 新参数
);
newToolCalls.add(edited);
```

**结果**: 工具使用修改后的参数执行

---

## 11. ShellTool 配合使用

ShellTool (`spring-ai-alibaba-agent-framework/.../shell/ShellTool.java`) 提供交互式 CLI 支持，与 HITL 配合:

```java
// ShellToolHook 在 beforeAgent/afterAgent 时执行
ShellToolHook shellHook = new ShellToolHook(
    InteractiveShell.builder()
        .hitlHandler(hitl -> {
            // 显示审批提示
            System.out.println("AI 请求审批:");
            for (ToolFeedback tf : hitl.getToolFeedbacks()) {
                System.out.println("  - " + tf.getName() + ": " + tf.getDescription());
            }
            // 等待用户输入
            return getUserInput();
        })
        .build()
);
```

---

## 12. 扩展性

### 12.1 自定义 Hook

```java
@HookPositions(HookPosition.BEFORE_MODEL)
public class LoggingHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        log.info("Before model call: {}", state.value("messages"));
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

### 12.2 自定义中断逻辑

```java
public class CustomInterruptionHook extends InterruptionHook {

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        // 自定义中断条件
        if (shouldInterrupt(state)) {
            return Optional.of(buildCustomMetadata(state));
        }
        return Optional.empty();
    }
}
```

---

## 13. 总结

### 13.1 HITL 核心机制

1. **Hook 机制**: 在图的特定位置插入自定义逻辑
2. **可中断节点**: 通过 `InterruptableAction` 接口实现中断检测
3. **双角色设计**: `HumanInTheLoopHook` 同时扮演 Hook 和可中断节点
4. **非阻塞模式**: 检查-返回-恢复，而非真正等待输入
5. **状态持久化**: 配合 Checkpointer 实现中断后恢复

### 13.2 文件索引

| 文件 | 位置 | 作用 |
|------|------|------|
| `Hook.java` | `agent/hook/` | Hook 基础接口 |
| `HookPosition.java` | `agent/hook/` | 插入位置枚举 |
| `ModelHook.java` | `agent/hook/` | 模型级 Hook |
| `InterruptableAction.java` | `action/` | 可中断节点接口 |
| `InterruptionMetadata.java` | `action/` | 中断元数据 |
| `HumanInTheLoopHook.java` | `agent/hook/hip/` | HITL 实现 |
| `NodeExecutor.java` | `graph-core/executor/` | 执行器 (检测中断) |
| `ReactAgent.java` | `agent/` | Agent 构建 (集成 Hook) |

---

**文档版本**: 1.0
**最后更新**: 2026-02-01
**作者**: Spring AI Alibaba 项目
