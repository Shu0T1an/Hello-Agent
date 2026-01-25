# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Hello-Agent 是一个基于 Java 21 和 Spring Boot 3.3 构建的 AI Agent 框架，参考了 Spring AI 的设计理念。项目采用多模块架构，实现了状态图执行引擎和 ReAct Agent 模式，支持 MCP (Model Context Protocol) 集成和检查点系统。

## 模块结构

- **graph-core**: 图执行引擎核心模块
  - `StateGraph`: 图构建器，提供 DSL 风格 API 定义节点和边
  - `GraphRunner`: 图执行器，支持同步和响应式流式执行
  - `CompiledGraph`: 编译后的可执行图
  - `State`: 状态接口，支持键值对存储和自定义合并策略
  - `CheckpointManager`: 检查点管理器接口
  - `CheckpointConfig`: 检查点配置（ALWAYS/ON_SPECIFIC_NODES/MANUAL/ERROR 策略）
  - `StateSnapshot`: 状态快照
  - `CheckpointMetadata`: 检查点元数据
  - 支持 Mermaid 格式的图可视化

- **agent-core**: Agent 实现模块
  - `ReactAgent`: ReAct 模式 Agent，组合 LLMNode 和 ToolNode
  - `LLMNode`: 大语言模型节点，集成 Spring AI ChatModel
  - `ToolNode`: 工具执行节点，支持 Spring AI @Tool 注解
  - `McpManager`: MCP 连接管理器接口
  - `ToolUtils`: 统一工具管理类
  - 使用 Spring AI OpenAI starter

- **Agent-Studio**: Web 应用模块
  - 提供 SSE 流式 API 端点
  - `AgentExecutionService`: Agent 注册和执行服务
  - `AgentConfig`: Agent 创建和注册配置
  - `ApiKeyConfig`: API 密钥统一管理
  - `McpServerConfig`: MCP 服务器连接配置
  - `NodeJsConfig`: Node.js/NPX 路径配置
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

```java
state.registerKeyStrategy("messages", AppendStrategy.getInstance());
state.registerKeyStrategy("iteration", ReplaceStrategy.getInstance());
```

### ReAct Agent 流程

```
START → MODEL → (有toolCalls?) → TOOL → MODEL → ...
                   ↓
                 (其他) → END
```

**节点类型**:
- `MODEL`: LLM 节点，调用大语言模型
- `TOOL`: 工具节点，执行工具调用
- `END`: 结束节点

**路由逻辑详解**:
- **从 MODEL 节点**:
  - 有 toolCalls → TOOL
  - 是 ToolResponseMessage → MODEL（继续循环）
  - 其他 → END
- **从 TOOL 节点**:
  - iteration < maxIterations → MODEL
  - 其他 → END

### 响应式执行

- 使用 Project Reactor 的 `Flux` 实现流式执行
- `GraphResponse` 封装节点输出，支持部分流数据
- 适用于 SSE 场景和流式 LLM 响应

## MCP 集成

Hello-Agent 集成了 Model Context Protocol (MCP)，支持动态加载外部工具。

### McpManager 接口

McpManager 负责 MCP 连接的生命周期管理：
- 连接注册、注销、更新
- 健康检查和自动重连
- 统计信息查询

### 连接类型

- **STDIO**: 通过标准输入输出通信（适用于 npx 包）
- **SSE**: 通过 Server-Sent Events 通信

### 配置示例

```yaml
mcp:
  servers:
    servers:
      - name: amap
        type: STDIO
        description: 高德地图 MCP 服务
        stdio:
          command: ${nodejs.npx-path}
          args:
            - "-y"
            - "@amap/amap-maps-mcp-server"
          env:
            AMAP_MAPS_API_KEY: ${api.keys.amap}
        timeout-seconds: 30
        auto-reconnect: true
```

### 占位符解析

配置支持以下占位符：
- `${nodejs.npx-path}`: 自动解析为 NodeJsConfig 中配置的 NPX 路径
- `${api.keys.xxx}`: 自动解析为 ApiKeyConfig 中的对应服务 API 密钥

### 与 Spring AI 工具系统集成

MCP 客户端可自动转换为 Spring AI 工具：

```java
List<Object> tools = new ArrayList<>();
tools.add(new SimpleTools());
tools.addAll(mcpManager.getAllMcpClients()); // 添加 MCP 客户端

// MCP 客户端自动转换为 ToolCallback
List<McpSyncClient> mcpClients = mcpManager.getAllMcpClients();
ToolCallback[] toolCallbacks = mcpManager.getAllToolCallbacks();
```

## 检查点系统

检查点系统支持状态持久化和恢复，适用于长任务中断恢复和错误调试。

### CheckpointManager 接口

主要功能：
- `createCheckpoint()`: 创建检查点
- `restoreContext()`: 从检查点恢复上下文
- `getState()`: 获取最新状态
- `getStateHistory()`: 获取状态历史
- `updateState()`: 更新状态

### CheckpointConfig 策略

- `ALWAYS`: 每个节点后创建检查点
- `ON_SPECIFIC_NODES`: 仅在指定节点创建检查点
- `MANUAL`: 手动创建检查点
- `ERROR`: 仅在错误时创建检查点

### StateSnapshot

状态快照包含：
- 执行上下文信息
- 状态数据
- 时间戳
- 来源标识（auto/manual/error/restore）

## 配置管理详解

### agent.execution 配置

```yaml
agent:
  execution:
    timeout: 300s              # 执行超时时间
    heartbeat-interval: 30s    # 心跳间隔
    max-title-length: 15        # 标题最大长度
    default-max-iterations: 10  # 默认最大迭代次数
    debug-mode: false           # 调试模式
```

### mcp.manager 配置

```yaml
mcp:
  manager:
    health-check-interval: 1m    # 健康检查间隔
    default-timeout: 30s         # 默认连接超时时间
    auto-connect-on-startup: true # 启动时自动连接
    max-retries: 3               # 最大重试次数
    retry-interval: 5s           # 重试间隔
    enable-health-check: true    # 是否启用健康检查
    health-check-timeout: 10s    # 健康检查超时时间
```

### mcp.servers 配置

支持 STDIO 和 SSE 两种连接类型，详见 MCP 集成章节。

### nodejs.npx-path 配置

跨平台 NPX 路径配置：

```yaml
nodejs:
  npx-path: ${NPX_PATH:D:\\Java\\nodejs\\npx.cmd}
```

### api.keys 配置

统一管理各类 API 密钥：

```yaml
api:
  keys:
    keys:
      amap: ${AMAP_API_KEY:default_key}
      openai: ${OPENAI_API_KEY}
```

### 配置占位符机制

支持 Spring Boot 占位符解析：
- 环境变量：`${API_KEY}`
- 默认值：`${NPX_PATH:/usr/local/bin/npx}`
- 配置引用：`${nodejs.npx-path}`

## 前端技术栈

### 核心依赖

- **Vue 3.5**: 渐进式 JavaScript 框架
- **TypeScript 5.9**: 类型安全的 JavaScript 超集
- **Vite 7**: 新一代前端构建工具

### 状态管理

- **Pinia 3**: Vue 官方推荐的状态管理库

### UI 渲染

- **Tailwind CSS 4**: 实用优先的 CSS 框架
- **@tailwindcss/typography**: Markdown 样式插件
- **Lucide Vue Next**: 图标库

### Markdown 渲染

- **markdown-it**: Markdown 解析器
- **Shiki**: 代码语法高亮
- **DOMPurify**: XSS 防护

### 开发命令

```bash
cd frontend
npm install      # 安装依赖
npm run dev      # 开发服务器
npm run build    # 生产构建
```

## Spring AI 集成

- 使用 `spring-ai-starter-model-openai` 依赖
- ChatModel 由 Spring 自动配置
- 工具通过 `@Tool` 注解定义，传递给 Agent 构造函数
- 参考 `agent-core/src/main/java/cn/ts/agent/example/` 下的示例

### MCP 工具集成

MCP 客户端可作为 Spring AI 工具使用：

```java
// MCP 客户端自动转换为 ToolCallback
List<McpSyncClient> mcpClients = mcpManager.getAllMcpClients();
ToolCallback[] toolCallbacks = mcpManager.getAllToolCallbacks();
```

### ToolUtils 工具管理

ToolUtils 提供统一的工具管理功能：
- 统一管理 Spring AI 工具和 MCP 工具
- 自动过滤和转换工具回调

```java
// 获取所有工具回调（包括 Spring AI 工具和 MCP 工具）
List<ToolCallback> allCallbacks = ToolUtils.getAllToolCallbacksFromTools(tools);
```

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
