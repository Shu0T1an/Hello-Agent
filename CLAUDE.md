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
  - 集成 MyBatis、PostgreSQL、WebFlux

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

# 运行特定测试类
mvn test -Dtest=StateGraphTest

# 运行特定测试方法
mvn test -Dtest=StateGraphTest#testAddNode

# 生成 JaCoCo 覆盖率报告
mvn clean test jacoco:report
# 报告位于 target/site/jacoco/index.html
```

### 前端

```bash
cd frontend
npm install
npm run dev     # 开发服务器
npm run build   # 生产构建
```

## 核心架构概念

### 设计模式

**Builder 模式**: 广泛用于创建复杂对象
- `StateGraph`: 流式 API 构建状态图
- `ReactAgent.Builder`: 构建 Agent 配置
- `LLMNode.Builder`: 构建 LLM 节点

**策略模式**: 状态合并策略
- `ReplaceStrategy`: 覆盖旧值（默认）
- `AppendStrategy`: 追加到列表（用于 messages 等）

**观察者模式**: 生命周期监听
- `GraphLifecycleListener`: 监听图执行事件
- `Hook`: 在特定位置插入自定义逻辑

**工厂模式**: 对象创建
- `StateFactory`: 统一状态创建
- `AgentFactory`: 创建 Agent 实例

### 核心概念

### 状态图执行流程

1. **图定义**: 使用 `StateGraph` 添加节点和边
   ```java
   StateGraph graph = new StateGraph()
       .addNode("node1", state -> { ... })
       .addEdge("node1", "node2")
       .setEntryPoint("node1");
   ```
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

**Builder 模式创建 Agent**:
```java
ReactAgent agent = ReactAgent.builder()
    .name("my-agent")
    .chatModel(chatModel)
    .tools(tools)
    .maxIterations(10)
    .build();
```

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
    default-max-iterations: 100  # 默认最大迭代次数
    debug-mode: false           # 调试模式
    retry:
      enabled: true             # 是否启用重试
      max-retries: 3            # 最大重试次数
      initial-backoff: 1s       # 初始退避时间
      backoff-multiplier: 2.0   # 退避时间倍数
      max-backoff: 30s          # 最大退避时间
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

### 前端架构

**项目结构**:
- `src/components/`: Vue 组件（按功能分组：agent, chat, sidebar, base, common）
- `src/views/`: 页面视图（AgentManagement, McpManagement, RagQueryView 等）
- `src/stores/`: Pinia 状态管理（agent, chat, agentTimeline, modelConfig 等）
- `src/api/`: API 调用封装
- `src/types/`: TypeScript 类型定义
- `src/utils/`: 工具函数（markdown, helpers, constants）
- `src/composables/`: Vue 组合式函数
- `src/layouts/`: 布局组件（MainLayout）

**关键 Store**:
- `useChatStore`: 会话和消息管理，SSE 流处理，工具调用消息处理
- `useAgentStore`: Agent 配置管理
- `useAgentTimelineStore`: Agent 执行时间线追踪

**SSE 事件类型**（与后端 AgentResponse 一致）:
- `running`: 节点运行中（增量输出）
- `completed`: 节点完成
- `GRAPH_COMPLETED`: 图执行完成
- `failed`: 节点失败
- `INTERRUPTION`: 人工在环中断
- `TITLE_GENERATED`: 会话标题生成

**消息类型**:
- `user`: 用户消息
- `assistant`: AI 消息
- `tool_call`: 工具调用（Function Call）
- `tool_response`: 工具执行结果
- `system`: 系统消息

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
- `application-dev.yml`: 开发环境配置（PostgreSQL 连接等）
- Spring AI 相关配置需要添加 API Key 配置

### 数据库连接配置

**PostgreSQL**（当前默认）:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/agent_studio_db
    driver-class-name: org.postgresql.Driver
    username: admin
    password: 123456
```

## 代码规范

- **Java 版本**: 21
- **使用 Lombok 简化代码**
- **异常处理**: 使用 `GraphException` 及其子类
- **响应式编程**: 使用 Reactor Core
- **日志记录**: 使用 SLF4J

### Git 提交规范

**重要限制**:
- **不要**在 Git 提交信息的结尾添加 `Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>` 或类似的署名信息
- 提交信息应该简洁明了，描述实际做了什么改动
- 使用中文编写提交信息

**提交信息格式**:
```
<type>: <subject>

<body>
```

**类型 (type)**:
- `feat`: 新功能
- `fix`: 修复 bug
- `refactor`: 重构代码
- `chore`: 构建/工具链相关
- `docs`: 文档更新
- `test`: 测试相关

**示例**:
```
feat: 添加 MCP 工具自动发现功能

实现了基于注解的工具自动发现机制，
支持 Spring AI @Tool 注解和 MCP 客户端工具。
```

### 测试规范

**测试工具**:
- JUnit 5（单元测试）
- Mockito（Mock 框架）
- Spring Boot Test（集成测试）

**测试类别**:
- 单元测试：各模块的 `src/test/java` 目录
- 集成测试：MyBatis 数据库集成测试、MCP 连接测试
- 示例代码：`EmailProcessingExample` 展示图执行引擎用法

### 数据库

**当前使用**:
- PostgreSQL（主要数据库）
- pgvector 扩展（向量存储）

**迁移历史**:
- 项目从 MySQL 迁移到 PostgreSQL
- 配置文件保留两者兼容性

**Schema 初始化**:
- 位置: `Agent-Studio/src/main/resources/db/`
- 文件:
  - `schema.sql`: 主表结构
  - `schema-checkpoint.sql`: 检查点表
  - `schema-rag.sql`: RAG 相关表
  - `data.sql`: 初始数据

### 常见开发任务

**添加新的 Agent**:
1. 在数据库中创建 Agent 配置（通过 API 或直接插入）
2. Agent 会自动注册到 `AgentExecutionService`
3. 使用 `/api/stream/agent/{agentName}/execute` 执行

**添加新的工具**:
1. 创建工具类并使用 `@Tool` 注解（Spring AI）或实现 MCP 客户端
2. 通过 API 注册工具定义
3. 将工具关联到 Agent 配置

**调试 Agent 执行**:
1. 设置 `agent.execution.debug-mode: true`
2. 查看日志输出（级别设为 DEBUG）
3. 使用 Prometheus 端点查看指标
4. 检查检查点记录（如果启用）

**查看 Checkpoint 记录**:
```sql
-- 查看会话的所有 Checkpoint
SELECT * FROM checkpoint_snapshots WHERE session_id = ? ORDER BY created_at DESC;

-- 查看执行记录
SELECT state_json->'execution_record' FROM checkpoint_snapshots WHERE session_id = ?;

-- 查看执行历史
SELECT state_json->'execution_history' FROM checkpoint_snapshots WHERE session_id = ?;
```

### 重要常量

**graph-core 模块** (`GraphConstants`):
- `START`: 图起始节点标识
- `END`: 图结束节点标识
- `AGENT_MODEL`: Agent MODEL 节点名称
- `AGENT_TOOL`: Agent TOOL 节点名称
- `AGENT_END`: Agent END 节点名称

**agent-core 模块** (`StateKeys`):
- `MESSAGES`: 消息列表状态键
- `ITERATION`: 当前迭代次数
- `MAX_ITERATIONS`: 最大迭代次数
- `CHAT_RESPONSE`: ChatModel 响应
- `EXECUTION_RECORD`: 当前执行记录
- `EXECUTION_HISTORY`: 执行历史记录

**Event 常量** (`EventConstants`):
- `NODE_STARTED`: 节点开始事件
- `NODE_COMPLETED`: 节点完成事件
- `NODE_FAILED`: 节点失败事件
- `GRAPH_COMPLETED`: 图执行完成事件

### 重要文件位置

- **图可视化**: `MermaidGraphVisualizer`（支持导出 Mermaid 格式）
- **状态常量**: `StateKeys`（agent-core）、`GraphConstants`（graph-core）
- **异常层次**: `GraphException` 及其子类
- **测试工具**: `TestFixture`、`TestUtils`

## Web API

### Agent 管理
- `POST /api/agents` - 创建 Agent
- `PUT /api/agents/{id}` - 更新 Agent
- `DELETE /api/agents/{id}` - 删除 Agent
- `GET /api/agents/{id}` - 获取 Agent 详情
- `GET /api/agents` - 获取所有 Agent

### 流式执行
- `GET /api/stream/agent/{agentName}/execute` - SSE 流式执行
- `GET /api/stream/agent/{agentName}/exists` - 检查 Agent 是否存在
- `GET /api/stream/agents` - 获取所有已注册 Agent

### MCP 管理
- `GET /api/mcp/servers` - 获取所有 MCP 服务器
- `POST /api/mcp/servers` - 添加 MCP 服务器
- `DELETE /api/mcp/servers/{name}` - 删除 MCP 服务器

### RAG (检索增强生成)
- `POST /api/rag/upload` - 上传文档到知识库
- `POST /api/rag/query` - 查询知识库
- `GET /api/rag/knowledge-bases` - 获取知识库列表
- `DELETE /api/rag/knowledge-bases/{id}` - 删除知识库

### 可观测性
- `GET /actuator/prometheus` - Prometheus 指标端点
- `GET /actuator/health` - 健康检查端点
- `GET /actuator/metrics` - 指标列表端点

### 会话管理
- `GET /api/sessions` - 获取所有会话列表
- `POST /api/sessions` - 创建新会话
- `GET /api/sessions/{sessionId}` - 获取会话详情
- `DELETE /api/sessions/{sessionId}` - 删除会话
- `DELETE /api/sessions/delete-all` - 删除所有会话
- `GET /api/sessions/{sessionId}/summary` - 获取会话摘要（统计信息）

### Agent 执行
- `POST /api/stream/agent/{agentName}/execute` - 执行 Agent（SSE 流式）
- `POST /api/stream/agent/{agentName}/resume` - 恢复执行（审批后）
- `GET /api/stream/agent/{agentName}/exists` - 检查 Agent 是否存在
- `GET /api/stream/agents` - 获取所有已注册 Agent

### 文件上传
- `POST /api/files/upload` - 上传临时文件（支持 PDF、TXT、MD）
- 返回 `TemporaryFileContent` 包含文件内容和元数据

## 高级功能

### 会话系统

会话系统基于 **Session 和 Checkpoint 分离设计**：

**Session 表** (`sessions`):
- 存储会话级别的元数据（标题、Agent、状态等）
- `session_id`: 会话唯一标识
- `current_agent`: 当前使用的 Agent 名称
- `agent_switch_history`: Agent 切换历史（JSON 数组）
- `status`: 会话状态（active/deleted）

**Checkpoint 表** (`checkpoint_snapshots`):
- 存储执行状态快照，用于状态恢复和历史追踪
- `session_id`: 关联的会话ID（外键）
- `checkpoint_id`: Checkpoint 唯一标识
- `last_node_id`: 上一个执行的节点ID，用于追踪执行流程
- `parent_id`: 父 Checkpoint ID，支持检查点链
- `state_json`: 完整状态（JSON 格式）
- `source`: Checkpoint 来源（auto/manual/error/restore）

**会话摘要服务** (`SessionSummaryService`):
- 计算会话级别的统计数据
- Token 使用情况统计
- 工具调用统计（按名称分组）
- LLM 调用详情列表

### 执行记录系统 (ExecutionRecord)

统一的执行记录系统，记录所有 LLM 和工具调用：

**LLMExecutionRecord**:
- `nodeId`: 节点ID
- `usage`: Token 使用情况（prompt/completion/total）
- `toolCalls`: 工具调用列表
- `inputMessages`: 输入消息
- `startTime`/`endTime`: 时间戳
- `duration`: 执行时长

**ToolExecutionRecord**:
- `executions`: 工具执行列表
- 每个执行包含：`id`, `name`, `arguments`, `result`, `success`, `duration`

**ExecutionRecordManager**:
- 统一管理所有执行记录
- 自动记录到状态中的 `execution_record` 键
- 历史记录累积到 `execution_history` 键

### 文档引用系统 (Citation Reference)

RAG 查询结果支持文档引用：

**CitationReference**:
- `chunkId`: 文档块ID
- `source`: 文档来源（文件名）
- `content`: 引用内容
- `metadata`: 额外元数据

**前端集成**:
- `Message` 类型包含 `citations` 字段
- ChatMessage 组件渲染引用标记
- Markdown 渲染器处理引用标记

### 钩子机制 (Hook System)

钩子系统允许在节点执行前后插入自定义逻辑：

**可用钩子接口**:
- `GraphLifecycleListener`: 图生命周期监听器
- `HumanInTheLoopHook`: 人机交互钩子，支持中断等待用户输入
- `LoggingHook`: 日志记录钩子

**钩子类型**:
- `BEFORE_NODE`: 节点执行前
- `AFTER_NODE`: 节点执行后
- `ON_ERROR`: 错误发生时

**使用示例**:
```java
// 参见 agent-core/src/test/java/cn/ts/agent/hook/HookIntegrationExample.java
HumanInTheLoopHook hook = new HumanInTheLoopHook();
ReactAgent agent = ReactAgent.builder()
    .hook(hook)
    .build();
```

**会话恢复**:
- 从 Checkpoint 恢复执行上下文
- 支持 INTERRUPTION 后的审批恢复
- API: `POST /api/stream/agent/{agentName}/resume`
- 请求参数: `checkpointId`, `feedbackData`, `sessionId`

**前端审批流程**:
1. SSE 事件类型 `INTERRUPTION` 触发
2. 显示 `ApprovalDialog` 组件
3. 用户审批工具调用结果
4. 调用 `resumeAgent` 恢复执行
5. 继续接收 SSE 流事件

### 可观测性 (Observability)

**Micrometer 集成**:
- 自动收集执行指标（执行时间、节点调用次数等）
- 支持 Prometheus 导出
- 通过 `GraphObservationLifecycleListener` 实现

**自定义指标名称**:
参见 `ObservationMetricNames` 和 `ObservationMetricAttributes`

### 重试机制

**配置参数**（见 `agent.execution.retry`）:
- `enabled`: 是否启用重试
- `max-retries`: 最大重试次数
- `initial-backoff`: 初始退避时间
- `backoff-multiplier`: 退避时间倍数
- `max-backoff`: 最大退避时间

**LLM 节点自动重试**:
- 在 LLM 调用失败时自动重试
- 支持指数退避策略

### RAG 功能

**支持的文档格式**:
- PDF（使用 PDFBox）
- TXT、MD、Markdown

**文本分块配置**（见 `rag.text-splitter`）:
- `chunk-size`: 块大小
- `chunk-overlap`: 块重叠大小
- `min-chunk-size`: 最小块大小

**向量存储**:
- 使用 PostgreSQL + pgvector
- 支持相似度检索
- 可配置相似度阈值

**文档引用**:
- 查询结果返回 `CitationReference` 对象
- 包含 `chunkId`, `source`, `content`, `metadata`
- 前端 ChatMessage 组件渲染引用标记
