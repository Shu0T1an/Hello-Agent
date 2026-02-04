# Spring AI Alibaba Hook 机制完整指南

## 目录

1. [Hook 概述](#1-hook-概述)
2. [Hook 定义与接口](#2-hook-定义与接口)
3. [Hook 构建与配置](#3-hook-构建与配置)
4. [Hook 嵌入 Graph 流程](#4-hook-嵌入-graph-流程)
5. [Hook 执行全流程](#5-hook-执行全流程)
6. [内置 Hook 实现](#6-内置-hook-实现)
7. [自定义 Hook 开发](#7-自定义-hook-开发)
8. [最佳实践](#8-最佳实践)

---

## 1. Hook 概述

### 1.1 什么是 Hook

**Hook** 是一种**插入式扩展机制**，允许在 Agent 执行流程的特定位置插入自定义逻辑。Hook 不是简单的回调函数，而是被转换为图中的**节点**，参与到整个图的执行流程中。

### 1.2 核心设计理念

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Hook 设计理念                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  传统回调方式                     Spring AI Alibaba Hook 方式            │
│  ───────────────────────────────────────────────────────────────────  │
│  在特定位置调用函数               将函数包装成节点插入图中                │
│  执行流是线性的                   执行流是图的边连接                      │
│  难以控制流程                     可以 JumpTo、中断、跳转                │
│  难以组合                         可以像节点一样串联/并联                 │
│                                                                         │
│  本质: Hook = 图节点 + 语义化的位置标签                                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Hook 的四大位置

```java
// 位置: agent/hook/HookPosition.java
public enum HookPosition {
    BEFORE_AGENT,   // Agent 开始前
    AFTER_AGENT,    // Agent 结束后
    BEFORE_MODEL,   // LLM 调用前
    AFTER_MODEL     // LLM 调用后
}
```

**位置图示**:

```
        START
           ↓
    ┌─────────────────────────────────────────────────────────────┐
    │  BEFORE_AGENT                                              │
    │  ┌─────────────────────────────────────────────────────┐   │
    │  │ Hook1.before → Hook2.before → ... → entryNode       │   │
    │  └─────────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
           ↓
    ┌─────────────────────────────────────────────────────────────┐
    │  BEFORE_MODEL                                              │
    │  ┌─────────────────────────────────────────────────────┐   │
    │  │ Hook1.beforeModel → Hook2.beforeModel → ...        │   │
    │  └─────────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
           ↓
    ┌─────────────────────────────────────────────────────────────┐
    │  AGENT_MODEL (LLM 调用)                                      │
    └─────────────────────────────────────────────────────────────┘
           ↓
    ┌─────────────────────────────────────────────────────────────┐
    │  AFTER_MODEL                                               │
    │  ┌─────────────────────────────────────────────────────┐   │
    │  │ HookN.afterModel → ... → Hook1.afterModel          │   │
    │  └─────────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
           ↓
    ┌─────────────────────────────────────────────────────────────┐
    │  AGENT_TOOL (工具执行，如果有)                               │
    └─────────────────────────────────────────────────────────────┘
           ↓ (循环回 BEFORE_MODEL)
    ┌─────────────────────────────────────────────────────────────┐
    │  AFTER_AGENT                                               │
    │  ┌─────────────────────────────────────────────────────┐   │
    │  │ HookN.after → ... → Hook1.after → END               │   │
    │  └─────────────────────────────────────────────────────┘   │
    └─────────────────────────────────────────────────────────────┘
           ↓
         END
```

---

## 2. Hook 定义与接口

### 2.1 核心接口层次

```
Hook (接口)
│
├── AgentHook (抽象类)
│   ├── beforeAgent(state, config)
│   └── afterAgent(state, config)
│
└── ModelHook (抽象类)
    ├── beforeModel(state, config)
    └── afterModel(state, config)
```

### 2.2 Hook 基础接口

```java
// 位置: agent/hook/Hook.java
public interface Hook {
    // 获取 Hook 名称
    String getName();

    // 设置/获取所属 Agent 名称
    void setAgentName(String agentName);
    String getAgentName();

    // 设置/获取所属 Agent 实例
    ReactAgent getAgent();
    void setAgent(ReactAgent agent);

    // 流程控制: 可以跳转到哪些位置
    default List<JumpTo> canJumpTo() {
        return List.of();
    }

    // 状态管理: 提供 KeyStrategy
    default Map<String, KeyStrategy> getKeyStrategys() {
        return Map.of();
    }

    // 获取 Hook 应该执行的位置
    default HookPosition[] getHookPositions() {
        HookPositions annotation = this.getClass().getAnnotation(HookPositions.class);
        if (annotation != null) {
            return annotation.value();
        }
        // 默认回退逻辑
        if (this instanceof AgentHook) {
            return new HookPosition[]{HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT};
        } else if (this instanceof ModelHook) {
            return new HookPosition[]{HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
        }
        return new HookPosition[0];
    }

    // 获取完整的 Hook 名称 (带前缀)
    static String getFullHookName(Hook hook) {
        return AGENT_HOOK_NAME_PREFIX + hook.getName();
    }
}
```

### 2.3 AgentHook 抽象类

```java
// 位置: agent/hook/AgentHook.java
public abstract class AgentHook implements Hook {

    private String agentName;
    private ReactAgent reactAgent;

    // Agent 开始前执行
    public CompletableFuture<Map<String, Object>> beforeAgent(
        OverAllState state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    // Agent 结束后执行
    public CompletableFuture<Map<String, Object>> afterAgent(
        OverAllState state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    // getter/setter...
}
```

### 2.4 ModelHook 抽象类

```java
// 位置: agent/hook/ModelHook.java
public abstract class ModelHook implements Hook {

    private String agentName;
    private ReactAgent reactAgent;

    // 模型调用前执行
    public CompletableFuture<Map<String, Object>> beforeModel(
        OverAllState state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    // 模型调用后执行
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    // getter/setter...
}
```

### 2.5 HookPositions 注解

```java
// 位置: agent/hook/HookPositions.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HookPositions {
    HookPosition[] value();
}

// 使用示例
@HookPositions(HookPosition.AFTER_MODEL)
public class HumanInTheLoopHook extends ModelHook {
    // ...
}
```

---

## 3. Hook 构建与配置

### 3.1 创建自定义 Hook

```java
// 示例 1: 简单的日志 Hook
@HookPositions(HookPosition.BEFORE_MODEL)
public class LoggingHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
        OverAllState state, RunnableConfig config) {

        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
        log.info("Before model call, message count: {}", messages.size());

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public String getName() {
        return "LoggingHook";
    }
}
```

```java
// 示例 2: 状态修改 Hook
@HookPositions(HookPosition.AFTER_MODEL)
public class MessageEnricherHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {

        // 添加自定义元数据到最后的消息
        List<Message> messages = new ArrayList<>((List<Message>) state.value("messages").orElse(List.of()));
        if (!messages.isEmpty()) {
            Message lastMessage = messages.get(messages.size() - 1);
            if (lastMessage instanceof AssistantMessage assistantMsg) {
                // 添加处理时间戳
                Map<String, Object> metadata = new HashMap<>(assistantMsg.getMetadata());
                metadata.put("processedAt", Instant.now());
                metadata.put("processedBy", "MessageEnricherHook");

                AssistantMessage enrichedMsg = AssistantMessage.builder()
                    .content(assistantMsg.getText())
                    .toolCalls(assistantMsg.getToolCalls())
                    .properties(metadata)
                    .build();

                Map<String, Object> updates = new HashMap<>();
                updates.put("messages", List.of(enrichedMsg, new RemoveByHash<>(assistantMsg)));
                return CompletableFuture.completedFuture(updates);
            }
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public String getName() {
        return "MessageEnricher";
    }
}
```

```java
// 示例 3: 带流程控制的 Hook
@HookPositions({HookPosition.AFTER_MODEL, HookPosition.AFTER_AGENT})
public class ConditionalRoutingHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {

        // 检查是否满足某个条件
        if (shouldSkipTool(state)) {
            // 设置跳转标记
            Map<String, Object> updates = new HashMap<>();
            updates.put("jump_to", JumpTo.end);
            return CompletableFuture.completedFuture(updates);
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public List<JumpTo> canJumpTo() {
        // 声明此 Hook 可以跳转到 END
        return List.of(JumpTo.end);
    }

    @Override
    public String getName() {
        return "ConditionalRouting";
    }

    private boolean shouldSkipTool(OverAllState state) {
        // 自定义条件逻辑
        return false;
    }
}
```

### 3.2 配置 Hook 到 Agent

```java
// 方式 1: 通过 Builder 配置
ReactAgent agent = ReactAgent.builder()
    .chatModel(chatModel)
    .tools(List.of(tool1, tool2, tool3))
    .hooks(List.of(
        new LoggingHook(),
        new MessageEnricherHook(),
        HumanInTheLoopHook.builder()
            .approvalOn("deleteFile", "删除文件操作")
            .approvalOn("sendEmail", "发送邮件操作")
            .build()
    ))
    .build();
```

```java
// 方式 2: 使用 AgentBuilderFactory
AgentBuilderFactory<ReactAgent.Builder, ReactAgent> factory =
    new DefaultAgentBuilderFactory<>();

ReactAgent agent = factory.builder()
    .chatModel(chatModel)
    .tools(List.of(tool1, tool2))
    .hooks(List.of(new LoggingHook()))
    .build();
```

---

## 4. Hook 嵌入 Graph 流程

### 4.1 流程概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Hook 嵌入 Graph 完整流程                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  用户构建 Agent                                                         │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  阶段 1: initGraph() - 图构建                                    │   │
│  │  ReactAgent.initGraph()                                         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.1 创建 StateGraph                                            │   │
│  │      StateGraph graph = new StateGraph(name, ...);               │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.2 添加核心节点                                                │   │
│  │      graph.addNode("agent_model", llmNode);                     │   │
│  │      graph.addNode("agent_tool", toolNode);                     │   │
│  │                                                                  │   │
│  │      图结构:                                                      │   │
│  │      ┌──────────────┐      ┌──────────────┐                     │   │
│  │      │ agent_model  │      │ agent_tool   │                     │   │
│  │      └──────────────┘      └──────────────┘                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.3 Hook 分类                                                  │   │
│  │      List<Hook> beforeAgentHooks =                              │   │
│  │          filterHooksByPosition(hooks, HookPosition.BEFORE_AGENT);│ │
│  │      List<Hook> afterModelHooks =                               │   │
│  │          filterHooksByPosition(hooks, HookPosition.AFTER_MODEL); │ │
│  │      ...                                                         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.4 Hook 转换为节点并添加到图中                                 │   │
│  │                                                                  │   │
│  │      for (Hook hook : beforeAgentHooks) {                       │   │
│  │          graph.addNode(                                         │   │
│  │              Hook.getFullHookName(hook) + ".before",            │   │
│  │              agentHook::beforeAgent  // 方法引用作为节点动作    │   │
│  │          );                                                     │   │
│  │      }                                                           │   │
│  │                                                                  │   │
│  │      for (Hook hook : afterModelHooks) {                        │   │
│  │          if (hook instanceof HumanInTheLoopHook hitl) {         │   │
│  │              graph.addNode(                                     │   │
│  │                  Hook.getFullHookName(hook) + ".afterModel",    │   │
│  │                  hitl  // Hook 本身实现 AsyncNodeActionWithConfig│   │
│  │              );                                                 │   │
│  │          } else {                                               │   │
│  │              graph.addNode(                                     │   │
│  │                  Hook.getFullHookName(hook) + ".afterModel",    │   │
│  │                  modelHook::afterModel                          │   │
│  │              );                                                 │   │
│  │          }                                                       │   │
│  │      }                                                           │   │
│  │                                                                  │   │
│  │      现在图中有:                                                  │   │
│  │      ┌────────────────────┐  ┌────────────────────┐             │   │
│  │      │ __agent_Hook1.before│ │ __agent_HITL.afterModel│          │   │
│  │      ├────────────────────┤  ├────────────────────┤             │   │
│  │      │ __agent_Hook2.before│ │ __agent_Hook4.afterModel│         │   │
│  │      └────────────────────┘  └────────────────────┘             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.5 确定入口和出口节点                                          │   │
│  │      String entryNode = determineEntryNode(...);                │   │
│  │      String loopEntryNode = determineLoopEntryNode(...);        │   │
│  │      String loopExitNode = determineLoopExitNode(...);          │   │
│  │      String exitNode = determineExitNode(...);                  │   │
│  │                                                                  │   │
│  │      规则:                                                        │   │
│  │      - entryNode: 第一个 beforeAgent 或第一个 beforeModel       │   │
│  │      - loopEntryNode: 第一个 beforeModel                        │   │
│  │      - loopExitNode: 最后一个 afterModel                        │   │
│  │      - exitNode: 最后一个 afterAgent 或 END                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1.6 设置边连接节点                                             │   │
│  │      setupHookEdges(graph, ...);                                 │   │
│  │                                                                  │   │
│  │      边连接规则:                                                  │   │
│  │      - START → entryNode                                         │   │
│  │      - beforeAgent[0] → beforeAgent[1] → ... → loopEntryNode    │   │
│  │      - loopEntryNode → agent_model                               │   │
│  │      - agent_model → afterModel[last] → ... → afterModel[0]     │   │
│  │      - afterModel[last] → agent_tool (如果有工具)                │   │
│  │      - agent_tool → loopEntryNode (循环)                         │   │
│  │      - loopExitNode → exitNode → END                             │   │
│  │                                                                  │   │
│  │      完整图结构:                                                  │   │
│  │                      START                                       │   │
│  │                        ↓                                         │   │
│  │              __agent_Hook1.before                                │   │
│  │                        ↓                                         │   │
│  │              __agent_Hook2.before                                │   │
│  │                        ↓                                         │   │
│  │              __agent_Hook3.beforeModel                           │   │
│  │                        ↓                                         │   │
│  │                   agent_model  ─────────────────┐               │   │
│  │                        ↓                      │                │   │
│  │              __agent_HITL.afterModel             │                │   │
│  │                        ↓                      │                │   │
│  │              __agent_Hook4.afterModel            │                │   │
│  │                        ↓                      ↓                │   │
│  │                   agent_tool ────────────────→ loopEntryNode    │   │
│  │                                               ↑                 │   │
│  │                        ↓                      └─────────────────┘   │
│  │              __agent_Hook5.after                                     │   │
│  │                        ↓                                           │   │
│  │                       END                                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  阶段 2: compile() - 编译图                                      │   │
│  │  StateGraph.compile(CompileConfig)                              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  2.1 验证图结构                                                   │   │
│  │      validateGraph();                                            │   │
│  │      - 检查所有节点是否有效                                       │   │
│  │      - 检查所有边是否连接有效                                     │   │
│  │      - 检查 START 节点存在                                       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  2.2 创建 CompiledGraph                                         │   │
│  │      return new CompiledGraph(this, config);                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  完成 - 图已编译，包含所有 Hook 节点                                     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 关键代码位置

#### 4.2.1 Hook 分类与节点添加

```java
// 位置: ReactAgent.java:286-334
@Override
protected StateGraph initGraph() throws GraphStateException {
    // ...
    // Categorize hooks by position
    List<Hook> beforeAgentHooks = filterHooksByPosition(hooks, HookPosition.BEFORE_AGENT);
    List<Hook> afterAgentHooks = filterHooksByPosition(hooks, HookPosition.AFTER_AGENT);
    List<Hook> beforeModelHooks = filterHooksByPosition(hooks, HookPosition.BEFORE_MODEL);
    List<Hook> afterModelHooks = filterHooksByPosition(hooks, HookPosition.AFTER_MODEL);

    // Add hook nodes for beforeAgent hooks
    for (Hook hook : beforeAgentHooks) {
        if (hook instanceof AgentHook agentHook) {
            graph.addNode(Hook.getFullHookName(hook) + ".before", agentHook::beforeAgent);
        } else if (hook instanceof MessagesAgentHook messagesAgentHook) {
            graph.addNode(Hook.getFullHookName(hook) + ".before",
                MessagesAgentHook.beforeAgentAction(messagesAgentHook));
        }
    }

    // Similar for afterAgent, beforeModel, afterModel...
    // ...
}
```

#### 4.2.2 边连接

```java
// 位置: ReactAgent.java:500-538
private static void setupHookEdges(
    StateGraph graph,
    List<Hook> beforeAgentHooks,
    List<Hook> afterAgentHooks,
    List<Hook> beforeModelHooks,
    List<Hook> afterModelHooks,
    String entryNode,
    String loopEntryNode,
    String loopExitNode,
    String exitNode,
    ReactAgent agentInstance) throws GraphStateException {

    // Chain before_agent hook
    chainHook(graph, beforeAgentHooks, ".before", loopEntryNode, loopEntryNode, exitNode);

    // Chain before_model hook
    chainHook(graph, beforeModelHooks, ".beforeModel", AGENT_MODEL_NAME, loopEntryNode, exitNode);

    // Chain after_model hook (reverse order)
    if (!afterModelHooks.isEmpty()) {
        chainModelHookReverse(graph, afterModelHooks, ".afterModel", AGENT_MODEL_NAME, loopEntryNode, exitNode);
    }

    // Chain after_agent hook (reverse order)
    if (!afterAgentHooks.isEmpty()) {
        chainAgentHookReverse(graph, afterAgentHooks, ".after", exitNode, loopEntryNode, exitNode);
    }

    // Add tool routing if tools exist
    if (agentInstance.hasTools) {
        setupToolRouting(graph, loopExitNode, loopEntryNode, exitNode, agentInstance);
    }
}
```

#### 4.2.3 Hook 链接

```java
// 位置: ReactAgent.java:588-614
private static void chainHook(
    StateGraph graph,
    List<Hook> hooks,
    String nameSuffix,
    String defaultNext,
    String modelDestination,
    String endDestination) throws GraphStateException {

    // 串联相邻 Hook
    for (int i = 0; i < hooks.size() - 1; i++) {
        Hook m1 = hooks.get(i);
        Hook m2 = hooks.get(i + 1);
        addHookEdge(graph,
            Hook.getFullHookName(m1) + nameSuffix,
            Hook.getFullHookName(m2) + nameSuffix,
            modelDestination, endDestination,
            m1.canJumpTo());
    }

    // 最后一个 Hook 连接到默认目标
    if (!hooks.isEmpty()) {
        Hook last = hooks.get(hooks.size() - 1);
        addHookEdge(graph,
            Hook.getFullHookName(last) + nameSuffix,
            defaultNext,
            modelDestination, endDestination,
            last.canJumpTo());
    }
}
```

---

## 5. Hook 执行全流程

### 5.1 执行时序图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Hook 执行完整时序                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  用户调用                                                               │
│     agent.invoke("用户查询", config)                                     │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  1. GraphRunner.invoke()                                        │   │
│  │     - 获取 CompiledGraph                                         │   │
│  │     - 初始化执行上下文                                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  2. 从 START 节点开始                                            │   │
│  │     currentNodeId = START                                       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  3. MainGraphExecutor.execute()                                 │   │
│  │     - 循环执行直到 END                                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  4. 通过边找到下一个节点                                         │   │
│  │     nextNodeId = edges.getNext(currentNodeId, state);            │   │
│  │     // START → "__agent_Hook1.before"                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  5. NodeExecutor.executeNode()                                  │   │
│  │     - 获取节点动作: AsyncNodeActionWithConfig action             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  6. 检查是否是可中断节点                                         │   │
│  │     if (action instanceof InterruptableAction) {                │   │
│  │         Optional<InterruptionMetadata> metadata =                │   │
│  │             action.interrupt(nodeId, state, config);            │   │
│  │         if (metadata.isPresent()) {                             │   │
│  │             return Flux.just(GraphResponse.done(metadata));    │   │
│  │         }                                                        │   │
│  │     }                                                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  7. 触发节点监听器                                               │   │
│  │     context.doListeners(NODE_BEFORE, null);                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  8. 执行节点动作                                                 │   │
│  │     CompletableFuture<Map<String, Object>> future =             │   │
│  │         action.apply(state, config);                            │   │
│  │                                                                  │   │
│  │     对于 Hook 节点:                                               │   │
│  │     - agentHook::beforeAgent → 调用 agentHook.beforeAgent()     │   │
│  │     - modelHook::afterModel → 调用 modelHook.afterModel()       │   │
│  │     - hitl (InterruptableAction) → 调用 hitl.apply() →          │   │
│  │       afterModel() + interrupt() 检查                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  9. 处理节点返回结果                                             │   │
│  │     handleActionResult(context, updateState, resultValue)       │   │
│  │     - 合并更新到状态: context.mergeIntoCurrentState(updateState) │   │
│  │     - 检查 JumpTo 标记                                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  10. 通过边找到下一个节点                                        │   │
│  │      Command nextCommand = context.nextNodeId(...);             │   │
│  │      context.setNextNodeId(nextCommand.gotoNode());             │   │
│  │                                                                  │   │
│  │      可能的跳转:                                                  │   │
│  │      - 正常流程: 下一个 Hook 节点                                 │   │
│  │      - JumpTo.end: 直接跳到 END                                  │   │
│  │      - JumpTo.model: 跳回 agent_model                            │   │
│  │      - JumpTo.tool: 跳到 agent_tool                              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  11. 触发节点监听器                                              │   │
│  │      context.doListeners(NODE_AFTER, null);                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  12. 继续循环 (回到步骤 4)                                       │   │
│  │      mainGraphExecutor.execute(context, resultValue)            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  13. 到达 END 节点，返回结果                                     │   │
│  │      return Flux.just(GraphResponse.of(output));                │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│     ↓                                                                 │
│  完成                                                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Hook 执行示例

假设有以下 Hook 配置:

```java
ReactAgent agent = ReactAgent.builder()
    .hooks(List.of(
        new LoggingHook(),           // BEFORE_MODEL
        new MessageEnricherHook(),    // AFTER_MODEL
        HumanInTheLoopHook.builder()  // AFTER_MODEL
            .approvalOn("deleteFile", "删除文件")
            .build()
    ))
    .build();
```

**执行流程**:

```
1. 用户调用: agent.invoke("请删除重要文件", config)

2. BEFORE_MODEL 阶段
   └─→ LoggingHook.beforeModel()
       └─→ 日志输出: "Before model call, message count: 1"

3. AGENT_MODEL 阶段
   └─→ LLM 调用
       └─→ 返回: AssistantMessage { toolCalls: [deleteFile("important.txt")] }

4. AFTER_MODEL 阶段 (逆序执行)
   └─→ HumanInTheLoopHook.apply()
       ├─→ HumanInTheLoopHook.afterModel()
       │   └─→ 检查 feedback: 无
       │   └─→ 返回 Map.of()
       │
       └─→ NodeExecutor 检测到 InterruptableAction
           └─→ HumanInTheLoopHook.interrupt()
               ├─→ 检查 toolCalls: deleteFile 需要审批
               ├─→ 构建 InterruptionMetadata
               └─→ 返回 Optional.of(metadata)

   【执行中断，返回 InterruptionMetadata】

5. 用户审批: REJECTED

6. 恢复执行 (使用相同 threadId + feedback)
   └─→ HumanInTheLoopHook.afterModel()
       ├─→ 检查 feedback: 有
       ├─→ 处理 REJECTED: 创建 ToolResponseMessage
       └─→ 返回更新: { messages: [ToolResponseMessage("已拒绝")] }

7. AFTER_MODEL 阶段继续
   └─→ MessageEnricherHook.afterModel()
       └─→ 添加元数据到消息

8. AGENT_TOOL 阶段
   └─→ 无工具需要执行 (已被拒绝)

9. 循环回 BEFORE_MODEL 或根据条件结束
```

---

## 6. 内置 Hook 实现

### 6.1 HumanInTheLoopHook

**位置**: `agent/hook/hip/HumanInTheLoopHook.java`

```java
@HookPositions(HookPosition.AFTER_MODEL)
public class HumanInTheLoopHook extends ModelHook
    implements AsyncNodeActionWithConfig, InterruptableAction {

    private Map<String, ToolConfig> approvalOn;

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {
        // 处理人类反馈
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(
        String nodeId, OverAllState state, RunnableConfig config) {
        // 检查是否需要中断
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder approvalOn(String toolName, String description) {
            // 配置需要审批的工具
        }
        public HumanInTheLoopHook build() {
            return new HumanInTheLoopHook(this);
        }
    }
}
```

**使用示例**:

```java
HumanInTheLoopHook hitl = HumanInTheLoopHook.builder()
    .approvalOn("deleteFile", "删除文件，不可逆操作")
    .approvalOn("sendEmail", "发送邮件")
    .approvalOn("transferFunds", "资金转账")
    .build();

ReactAgent agent = ReactAgent.builder()
    .hooks(List.of(hitl))
    .checkpointer(memorySaver)  // 必须配置
    .build();
```

### 6.2 InterruptionHook

**位置**: `agent/hook/InterruptionHook.java`

```java
public class InterruptionHook extends ModelHook
    implements AsyncNodeActionWithConfig, InterruptableAction {

    @Override
    public Optional<InterruptionMetadata> interrupt(
        String nodeId, OverAllState state, RunnableConfig config) {
        // 自定义中断逻辑
    }
}
```

### 6.3 TokenCounter

**位置**: `agent/hook/TokenCounter.java`

统计 Token 使用量:

```java
@HookPositions(HookPosition.AFTER_MODEL)
public class TokenCounter extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {
        // 统计并记录 Token 使用
    }
}
```

### 6.4 ToolInjection

**位置**: `agent/hook/ToolInjection.java`

允许 Hook 注入特定的工具:

```java
public interface ToolInjection extends Hook {
    String getRequiredToolName();
    Class<? extends ToolCallback> getRequiredToolType();

    void injectTool(ToolCallback tool);
}
```

---

## 7. 自定义 Hook 开发

### 7.1 基础 Hook 模板

```java
package com.example.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@HookPositions(HookPosition.AFTER_MODEL)
public class MyCustomHook extends ModelHook {

    private static final Logger log = LoggerFactory.getLogger(MyCustomHook.class);

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {

        // 1. 获取状态数据
        state.value("messages").ifPresent(messages -> {
            log.info("Current messages: {}", messages);
        });

        // 2. 执行自定义逻辑
        // ...你的代码...

        // 3. 返回状态更新 (可选)
        Map<String, Object> updates = Map.of(
            "customKey", "customValue"
        );
        return CompletableFuture.completedFuture(updates);
    }

    @Override
    public String getName() {
        return "MyCustomHook";
    }
}
```

### 7.2 带流程控制的 Hook

```java
@HookPositions({HookPosition.AFTER_MODEL, HookPosition.AFTER_AGENT})
public class ConditionalTerminationHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {

        // 检查是否满足终止条件
        if (shouldTerminate(state)) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("jump_to", JumpTo.end);
            updates.put("terminationReason", "条件满足，提前结束");
            return CompletableFuture.completedFuture(updates);
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of(JumpTo.end);
    }

    @Override
    public String getName() {
        return "ConditionalTermination";
    }

    private boolean shouldTerminate(OverAllState state) {
        // 自定义条件判断逻辑
        return state.value("someCondition")
            .map(value -> Boolean.TRUE.equals(value))
            .orElse(false);
    }
}
```

### 7.3 可中断 Hook

```java
@HookPositions(HookPosition.BEFORE_MODEL)
public class CustomInterruptionHook extends ModelHook
    implements AsyncNodeActionWithConfig, InterruptableAction {

    @Override
    public CompletableFuture<Map<String, Object>> apply(
        OverAllState state, RunnableConfig config) {
        return beforeModel(state, config);
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
        OverAllState state, RunnableConfig config) {
        // Hook 逻辑
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(
        String nodeId, OverAllState state, RunnableConfig config) {

        // 检查是否需要中断
        if (needsInterruption(state)) {
            InterruptionMetadata metadata = InterruptionMetadata.builder(nodeId, state)
                .addCustomData("reason", "需要用户确认")
                .build();
            return Optional.of(metadata);
        }

        return Optional.empty();
    }

    @Override
    public String getName() {
        return "CustomInterruption";
    }

    private boolean needsInterruption(OverAllState state) {
        // 中断条件判断
        return false;
    }
}
```

### 7.4 消息处理 Hook

```java
@HookPositions(HookPosition.AFTER_MODEL)
public class MessageTransformationHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {

        List<Message> messages = (List<Message>) state.value("messages")
            .orElse(List.of());

        if (messages.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof AssistantMessage assistantMsg)) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 转换消息
        AssistantMessage transformed = AssistantMessage.builder()
            .content(assistantMsg.getText().toUpperCase())  // 示例: 转大写
            .toolCalls(assistantMsg.getToolCalls())
            .properties(assistantMsg.getMetadata())
            .build();

        // 返回更新: 添加新消息，移除旧消息
        Map<String, Object> updates = new HashMap<>();
        updates.put("messages", List.of(
            transformed,
            new RemoveByHash<>(assistantMsg)
        ));

        return CompletableFuture.completedFuture(updates);
    }

    @Override
    public String getName() {
        return "MessageTransformation";
    }
}
```

---

## 8. 最佳实践

### 8.1 Hook 命名规范

```java
// 推荐: 使用描述性名称
public class LoggingHook extends ModelHook {
    @Override
    public String getName() {
        return "LoggingHook";  // 与类名一致
    }
}

// 不推荐: 使用模糊名称
public class Hook1 extends ModelHook {
    @Override
    public String getName() {
        return "hook1";  // 不清晰
    }
}
```

### 8.2 Hook 执行顺序

```java
// Hook 按照在列表中的顺序执行
List<Hook> hooks = List.of(
    new ValidationHook(),      // 1. 先验证
    new EnrichmentHook(),      // 2. 再增强
    new LoggingHook(),         // 3. 最后记录
    HumanInTheLoopHook.builder().build()  // 4. 人工审批
);

// AFTER_MODEL 位置会逆序执行
// 执行顺序: HITL → Logging → Enrichment → Validation
```

### 8.3 状态更新原则

```java
// ✅ 推荐: 使用 RemoveByHash 替换消息
@Override
public CompletableFuture<Map<String, Object>> afterModel(
    OverAllState state, RunnableConfig config) {

    AssistantMessage newMsg = createNewMessage();
    AssistantMessage oldMsg = getOldMessage(state);

    Map<String, Object> updates = new HashMap<>();
    updates.put("messages", List.of(
        newMsg,           // 添加新消息
        new RemoveByHash<>(oldMsg)  // 移除旧消息
    ));
    return CompletableFuture.completedFuture(updates);
}

// ❌ 不推荐: 直接修改状态
@Override
public CompletableFuture<Map<String, Object>> afterModel(
    OverAllState state, RunnableConfig config) {

    // 不要直接修改 state，应该返回更新
    state.messages().add(new Message());  // 错误!
    return CompletableFuture.completedFuture(Map.of());
}
```

### 8.4 异常处理

```java
@Override
public CompletableFuture<Map<String, Object>> afterModel(
    OverAllState state, RunnableConfig config) {

    try {
        // 可能抛出异常的代码
        Map<String, Object> result = processState(state);
        return CompletableFuture.completedFuture(result);
    } catch (Exception e) {
        log.error("Hook execution failed", e);
        // 返回空更新，让流程继续
        return CompletableFuture.completedFuture(Map.of());
    }
}
```

### 8.5 性能考虑

```java
// ✅ 推荐: 异步处理耗时操作
@Override
public CompletableFuture<Map<String, Object>> afterModel(
    OverAllState state, RunnableConfig config) {

    return CompletableFuture.supplyAsync(() -> {
        // 耗时操作在异步线程执行
        return heavyProcessing(state);
    });
}

// ❌ 不推荐: 阻塞主线程
@Override
public CompletableFuture<Map<String, Object>> afterModel(
    OverAllState state, RunnableConfig config) {

    // 耗时操作阻塞了主线程
    heavyProcessing(state);  // 错误!
    return CompletableFuture.completedFuture(Map.of());
}
```

### 8.6 Hook 组合模式

```java
// 模式 1: 验证链
List<Hook> validationChain = List.of(
    new InputValidationHook(),
    new SecurityCheckHook(),
    new RateLimitHook()
);

// 模式 2: 增强管道
List<Hook> enrichmentPipeline = List.of(
    new ContextEnrichmentHook(),
    new MetadataEnrichmentHook(),
    new CachingHook()
);

// 模式 3: 观察者模式
List<Hook> observers = List.of(
    new MetricsHook(),
    new LoggingHook(),
    new TracingHook()
);

// 组合使用
ReactAgent agent = ReactAgent.builder()
    .hooks(Stream.of(validationChain, enrichmentPipeline, observers)
        .flatMap(List::stream)
        .collect(Collectors.toList()))
    .build();
```

### 8.7 测试 Hook

```java
@Test
public void testCustomHook() {
    // 创建测试 Hook
    TestHook testHook = new TestHook();

    // 创建 Agent
    ReactAgent agent = ReactAgent.builder()
        .hooks(List.of(testHook))
        .build();

    // 执行
    agent.invoke("test", config).blockLast();

    // 验证
    assertTrue(testHook.isBeforeModelCalled());
    assertTrue(testHook.isAfterModelCalled());
    assertEquals("expectedValue", testHook.getCapturedState());
}

private static class TestHook extends ModelHook {
    private boolean beforeModelCalled = false;
    private boolean afterModelCalled = false;
    private Object capturedState;

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(
        OverAllState state, RunnableConfig config) {
        beforeModelCalled = true;
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(
        OverAllState state, RunnableConfig config) {
        afterModelCalled = true;
        capturedState = state.value("someKey").orElse(null);
        return CompletableFuture.completedFuture(Map.of());
    }

    // getters...
}
```

---

## 9. 附录

### 9.1 JumpTo 枚举

```java
public enum JumpTo {
    end,    // 跳转到 END (结束执行)
    tool,   // 跳转到工具执行节点
    model   // 跳转到模型调用节点
}
```

### 9.2 核心类文件索引

| 文件 | 位置 | 作用 |
|------|------|------|
| `Hook.java` | `agent/hook/` | Hook 基础接口 |
| `HookPosition.java` | `agent/hook/` | 位置枚举 |
| `HookPositions.java` | `agent/hook/` | 位置注解 |
| `AgentHook.java` | `agent/hook/` | Agent 级 Hook 抽象类 |
| `ModelHook.java` | `agent/hook/` | Model 级 Hook 抽象类 |
| `HumanInTheLoopHook.java` | `agent/hook/hip/` | HITL 实现 |
| `ReactAgent.java` | `agent/` | Agent 构建 (集成 Hook) |
| `NodeExecutor.java` | `graph-core/executor/` | 节点执行器 |
| `CompiledGraph.java` | `graph-core/` | 编译后的图 |
| `StateGraph.java` | `graph-core/` | 图构建器 |

### 9.3 完整示例

```java
// 完整的 Agent 配置示例
public class AgentExample {

    public static void main(String[] args) {
        // 1. 创建 ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();

        // 2. 创建工具
        ToolCallback deleteFileTool = ToolCallback.builder()
            .name("deleteFile")
            .description("删除文件")
            .function("deleteFile", (DeleteFileRequest request) -> {
                Files.deleteIfExists(Paths.get(request.getFilename()));
                return "文件已删除: " + request.getFilename();
            })
            .inputType(DeleteFileRequest.class)
            .build();

        // 3. 创建 Hooks
        List<Hook> hooks = List.of(
            // 日志记录
            new LoggingHook(),
            // Token 统计
            new TokenCounter(),
            // 人工审批
            HumanInTheLoopHook.builder()
                .approvalOn("deleteFile", "删除文件操作")
                .build()
        );

        // 4. 构建 Agent
        ReactAgent agent = ReactAgent.builder()
            .chatModel(chatModel)
            .tools(List.of(deleteFileTool))
            .hooks(hooks)
            .checkpointer(new MemorySaver())  // HITL 需要
            .build();

        // 5. 执行
        String threadId = UUID.randomUUID().toString();
        RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .build();

        Flux<GraphResponse<NodeOutput>> result = agent.invoke(
            "请删除名为 test.txt 的文件",
            AppConfig.builder().build(),
            config
        );

        // 6. 处理结果
        result.subscribe(response -> {
            if (response.isDone()) {
                Object value = response.resultValue().orElse(null);
                if (value instanceof InterruptionMetadata metadata) {
                    // 显示审批界面
                    showApprovalUI(metadata);

                    // 用户审批后恢复
                    resumeWithApproval(agent, threadId, metadata);
                }
            }
        });
    }

    private static void showApprovalUI(InterruptionMetadata metadata) {
        for (ToolFeedback tf : metadata.toolFeedbacks()) {
            System.out.println("AI 请求审批: " + tf.getName());
            System.out.println("参数: " + tf.getArguments());
            // 显示 UI 并收集用户输入...
        }
    }

    private static void resumeWithApproval(
        ReactAgent agent,
        String threadId,
        InterruptionMetadata originalMetadata) {

        // 构建反馈
        List<ToolFeedback> feedbacks = originalMetadata.toolFeedbacks().stream()
            .map(tf -> ToolFeedback.builder()
                .id(tf.getId())
                .name(tf.getName())
                .result(FeedbackResult.APPROVED)  // 用户批准
                .build())
            .collect(Collectors.toList());

        InterruptionMetadata feedback = InterruptionMetadata.builder(originalMetadata)
            .toolFeedbacks(feedbacks)
            .build();

        // 恢复执行
        RunnableConfig resumeConfig = RunnableConfig.builder()
            .threadId(threadId)
            .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedback)
            .build();

        agent.invoke(null, AppConfig.builder().build(), resumeConfig)
            .blockLast();
    }
}
```

---

**文档版本**: 1.0
**最后更新**: 2026-02-01
**作者**: Spring AI Alibaba 项目
