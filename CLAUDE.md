# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Hello-Agent 是一个基于 Java 21 和 Spring Boot 3.3 构建的 AI Agent 框架，参考了 Spring AI 的设计理念。项目采用多模块架构，实现了状态图执行引擎和 ReAct Agent 模式。

## 模块结构

- **graph-core**: 图执行引擎核心模块
  - `StateGraph`: 图构建器，提供 DSL 风格 API 定义节点和边
  - `GraphRunner`: 图执行器，支持同步和响应式流式执行
  - `CompiledGraph`: 编译后的可执行图
  - `State`: 状态接口，支持键值对存储和自定义合并策略
  - 支持 Mermaid 格式的图可视化

- **agent-core**: Agent 实现模块
  - `ReactAgent`: ReAct 模式 Agent，组合 LLMNode 和 ToolNode
  - `LLMNode`: 大语言模型节点，集成 Spring AI ChatModel
  - `ToolNode`: 工具执行节点，支持 Spring AI @Tool 注解
  - 使用 Spring AI OpenAI starter

- **Agent-Studio**: Web 应用模块
  - 提供 SSE 流式 API 端点
  - `AgentExecutionService`: Agent 注册和执行服务
  - 集成 MyBatis、MySQL、WebFlux

- **frontend**: Vue 3 + TypeScript + Vite 前端项目

## 构建与运行

### 后端

```bash
# 编译整个项目（在根目录）
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package

# 运行 Agent-Studio 应用（端口 8080）
cd Agent-Studio
mvn spring-boot:run

# 运行单个模块测试
cd graph-core
mvn test
```

### 前端

```bash
cd frontend
npm install
npm run dev     # 开发服务器
npm run build   # 生产构建
```

## 核心架构概念

### 状态图执行流程

1. **图定义**: 使用 `StateGraph` 添加节点和边
2. **编译**: 调用 `compile()` 生成 `CompiledGraph`
3. **执行**:
   - 同步执行: `compiledGraph.invoke(initialState)`
   - 流式执行: `compiledGraph.stream(initialState)` 返回 `Flux<GraphResponse<NodeOutput>>`

### 状态管理

- `MapState`: 默认状态实现，基于 Map 存储
- 支持自定义合并策略:
  - `ReplaceStrategy`: 覆盖旧值（默认）
  - `AppendStrategy`: 追加到列表
  - 通过 `registerKeyStrategy()` 注册

### ReAct Agent 流程

```
START → llmNode → (有toolCalls?) → toolNode → llmNode → ...
                     ↓
                   (无) → END
```

- `llmNode`: 调用 LLM，可能产生 toolCalls
- `toolNode`: 执行工具调用
- 条件边根据消息内容决定下一步

### 响应式执行

- 使用 Project Reactor 的 `Flux` 实现流式执行
- `GraphResponse` 封装节点输出，支持部分流数据
- 适用于 SSE 场景和流式 LLM 响应

## Spring AI 集成

- 使用 `spring-ai-starter-model-openai` 依赖
- ChatModel 由 Spring 自动配置
- 工具通过 `@Tool` 注解定义，传递给 Agent 构造函数
- 参考 `agent-core/src/main/java/cn/ts/agent/example/` 下的示例

## 配置说明

- `application.yml`: 基础配置（端口 8080）
- `application-dev.yml`: 开发环境配置（MySQL 连接等）
- Spring AI 相关配置需要添加 API Key 配置

## 代码规范

- Java 版本: 21
- 使用 Lombok 简化代码
- 异常处理: 使用 `GraphException` 及其子类
- 响应式编程: 使用 Reactor Core

## Web API

- `GET /api/stream/agent/{agentName}/execute`: SSE 流式执行
- `GET /api/stream/agent/{agentName}/exists`: 检查 Agent 是否存在
- `GET /api/stream/agents`: 获取所有已注册 Agent
